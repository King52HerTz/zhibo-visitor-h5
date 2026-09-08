from __future__ import annotations

import asyncio
import unittest

import httpx

from app.config import Settings
from app.main import create_app
from app.rate_limit import SlidingWindowRateLimiter
from app.sanitize import public_event


class FakeUpstream:
    async def stream_guide(self, session_id, message, museum, exhibition, exhibit_id):
        yield {"type": "rag_step", "step": {"label": "内部检索", "detail": "不应公开"}}
        yield {"type": "content", "content": "先观察衣身的薄透程度。"}
        yield {
            "type": "trace",
            "rag_trace": {
                "retrieval_outcome": "ANSWERABLE",
                "retrieved_chunks": [
                    {
                        "filename": "C:\\private\\museum\\guide.pdf",
                        "page_number": 18,
                        "text": "馆方公开资料片段",
                        "score": 0.99,
                        "chunk_id": "internal-chunk-id",
                    }
                ],
            },
        }
        yield "[DONE]"

    async def get_session(self, session_id):
        return [{
            "type": "ai",
            "content": "已完成导览",
            "timestamp": "2026-09-08T00:00:00Z",
            "rag_trace": {"retrieved_chunks": [{"filename": "/private/source.pdf", "text": "公开片段"}]},
        }]

    async def delete_session(self, session_id):
        return None


class GatewayTests(unittest.IsolatedAsyncioTestCase):
    async def test_catalog_supports_versioned_refresh_and_only_public_fields(self):
        app = create_app(Settings(), upstream=FakeUpstream())
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.get("/api/visitor/v1/catalog")
            self.assertEqual(response.status_code, 200)
            self.assertEqual(len(response.json()["exhibits"]), 3)
            self.assertTrue(response.headers["etag"])
            self.assertNotIn("draft", response.text)
            self.assertNotIn("internal", response.text)

            unchanged = await client.get(
                "/api/visitor/v1/catalog",
                headers={"If-None-Match": response.headers["etag"]},
            )
            self.assertEqual(unchanged.status_code, 304)
            self.assertEqual(unchanged.content, b"")

            detail = await client.get("/api/visitor/v1/exhibits/demo-su-sha")
            self.assertEqual(detail.status_code, 200)
            self.assertEqual(detail.json()["name"], "素纱单衣")

            unpublished = await client.get("/api/visitor/v1/exhibits/not-published")
            self.assertEqual(unpublished.status_code, 404)

    async def test_session_is_required_and_stream_is_publicly_sanitized(self):
        app = create_app(
            Settings(
                upstream_username="visitor",
                upstream_password="test-only-placeholder",
                cors_origins=("http://localhost:8020",),
            ),
            upstream=FakeUpstream(),
        )
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            created = await client.post("/api/visitor/v1/sessions", json={})
            self.assertEqual(created.status_code, 201)
            session_id = created.json()["session_id"]

            missing_header = await client.get(f"/api/visitor/v1/sessions/{session_id}")
            self.assertEqual(missing_header.status_code, 401)

            detail = await client.get(
                f"/api/visitor/v1/sessions/{session_id}",
                headers={"X-Visitor-Session": session_id},
            )
            self.assertEqual(detail.status_code, 200)
            self.assertEqual(detail.json()["messages"][0]["sources"][0]["title"], "source.pdf")
            self.assertNotIn("rag_trace", detail.text)

            stream = await client.post(
                "/api/visitor/v1/guide/stream",
                headers={"X-Visitor-Session": session_id},
                json={"session_id": session_id, "message": "请介绍这件展品", "mode": "quick"},
            )
            self.assertEqual(stream.status_code, 200)
            self.assertIn('event: content', stream.text)
            self.assertIn('event: evidence', stream.text)
            self.assertIn('"code": "ANSWERABLE"', stream.text)
            self.assertNotIn("internal-chunk-id", stream.text)
            self.assertNotIn("C:\\\\private", stream.text)

    async def test_unknown_paths_are_not_proxied(self):
        app = create_app(Settings(), upstream=FakeUpstream())
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            response = await client.post("/api/documents/upload", content=b"not-forwarded")
        self.assertEqual(response.status_code, 404)


class PureUnitTests(unittest.TestCase):
    def test_trace_redacts_path_and_debug_fields(self):
        event = public_event({
            "type": "trace",
            "rag_trace": {
                "retrieved_chunks": [{
                    "filename": "D:\\internal\\folder\\source.pdf",
                    "text": "公开摘录",
                    "score": 0.9,
                    "chunk_id": "secret",
                }],
            },
        })
        self.assertIsNotNone(event)
        source = event.data["sources"][0]
        self.assertEqual(source["title"], "source.pdf")
        self.assertNotIn("score", source)
        self.assertNotIn("chunk_id", source)

    def test_rate_limiter_returns_retry_after(self):
        limiter = SlidingWindowRateLimiter(max_requests=1, window_seconds=60)
        self.assertEqual(limiter.allow("device", now=10), (True, 0))
        allowed, retry_after = limiter.allow("device", now=11)
        self.assertFalse(allowed)
        self.assertGreaterEqual(retry_after, 59)
        self.assertEqual(limiter.allow("device", now=71), (True, 0))


if __name__ == "__main__":
    asyncio.run(asyncio.sleep(0))
    unittest.main()
