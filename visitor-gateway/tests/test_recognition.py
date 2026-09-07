from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import httpx

from app.config import Settings
from app.main import create_app
from app.models import PublicSource
from app.recognition import (
    RecognitionInputError,
    RecognitionService,
    RecognitionUnavailable,
    MuseumRagVerifier,
    VerificationResult,
    VisualAnalysis,
    VisualCandidateInput,
)
from tests.test_gateway import FakeUpstream


PNG = b"\x89PNG\r\n\x1a\n" + b"test-image-content"


class FakeVisual:
    def __init__(self, candidates=None, error=False):
        self.candidates = candidates or []
        self.error = error
        self.path_seen: Path | None = None

    async def analyze(self, image_path: Path, content_type: str) -> VisualAnalysis:
        self.path_seen = image_path
        if not image_path.exists():
            raise AssertionError("temporary image must exist during analysis")
        if self.error:
            raise RecognitionUnavailable("test_unavailable")
        return VisualAnalysis(observations=["可见轻薄织物", "衣襟交叠"], candidates=self.candidates)

    async def close(self):
        return None


class FakeVerifier:
    def __init__(self, statuses):
        self.statuses = iter(statuses)

    async def verify(self, candidate, museum):
        status = next(self.statuses)
        sources = [PublicSource(title="馆方图录.pdf", page=3, excerpt="馆方公开资料片段")] if status == "VERIFIED" else []
        return VerificationResult(status=status, sources=sources)

    async def close(self):
        return None


def candidate(name="素纱单衣", confidence=0.91):
    return VisualCandidateInput(name=name, confidence=confidence, visual_basis=["轻薄丝织物"])


class RecognitionServiceTests(unittest.IsolatedAsyncioTestCase):
    async def test_four_public_states_and_temporary_file_cleanup(self):
        scenarios = [
            ([candidate()], ["VERIFIED"], "READY", 1),
            ([candidate(), candidate("汉代丝织服饰", 0.62)], ["VERIFIED", "VERIFIED"], "READY", 2),
            ([candidate()], ["NOT_FOUND"], "NOT_FOUND", 1),
        ]
        for candidates, statuses, expected_status, expected_count in scenarios:
            with self.subTest(status=expected_status, count=expected_count), tempfile.TemporaryDirectory() as temp_dir:
                visual = FakeVisual(candidates)
                service = RecognitionService(
                    Settings(recognition_temp_dir=temp_dir),
                    visual=visual,
                    verifier=FakeVerifier(statuses),
                )
                result = await service.recognize("visitor-a", "image/png", PNG)
                self.assertEqual(expected_status, result.status)
                self.assertEqual(expected_count, len(result.candidates))
                self.assertIsNotNone(visual.path_seen)
                self.assertFalse(visual.path_seen.exists())
                self.assertEqual([], list(Path(temp_dir).iterdir()))

        with tempfile.TemporaryDirectory() as temp_dir:
            visual = FakeVisual(error=True)
            service = RecognitionService(
                Settings(recognition_temp_dir=temp_dir),
                visual=visual,
                verifier=FakeVerifier([]),
            )
            result = await service.recognize("visitor-a", "image/png", PNG)
            self.assertEqual("ERROR", result.status)
            self.assertFalse(visual.path_seen.exists())
            self.assertEqual([], list(Path(temp_dir).iterdir()))

    async def test_rejects_unsupported_or_spoofed_images(self):
        service = RecognitionService(Settings(), visual=FakeVisual(), verifier=FakeVerifier([]))
        with self.assertRaises(RecognitionInputError):
            await service.recognize("visitor-a", "image/gif", b"GIF89a")
        with self.assertRaises(RecognitionInputError):
            await service.recognize("visitor-a", "image/png", b"not-a-png")

    async def test_rag_verifier_only_exposes_public_source_fields(self):
        def handler(request: httpx.Request):
            self.assertEqual("Bearer test-placeholder", request.headers["Authorization"])
            return httpx.Response(200, json={
                "code": 0,
                "contexts": [{
                    "source": "C:\\private\\museum\\馆方图录.pdf",
                    "content": "馆方公开资料片段",
                    "page_number": 9,
                    "chunk_id": "internal-id",
                    "score": 0.99,
                }],
                "trace": {"retrieval_outcome": "ANSWERABLE", "debug": "private"},
            })

        client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        verifier = MuseumRagVerifier(
            Settings(rag_bridge_api_key="test-placeholder"),
            client=client,
        )
        result = await verifier.verify(candidate(), "湖南省博物馆")
        await verifier.close()
        self.assertEqual("VERIFIED", result.status)
        self.assertEqual("馆方图录.pdf", result.sources[0].title)
        self.assertEqual("馆方公开资料片段", result.sources[0].excerpt)
        self.assertNotIn("internal-id", str(result.sources))


class RecognitionApiTests(unittest.IsolatedAsyncioTestCase):
    async def test_upload_get_confirm_and_session_isolation(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            service = RecognitionService(
                Settings(recognition_temp_dir=temp_dir),
                visual=FakeVisual([candidate()]),
                verifier=FakeVerifier(["VERIFIED"]),
            )
            app = create_app(Settings(), upstream=FakeUpstream(), recognition_service=service)
            transport = httpx.ASGITransport(app=app)
            async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
                first = (await client.post("/api/visitor/v1/sessions", json={})).json()["session_id"]
                second = (await client.post("/api/visitor/v1/sessions", json={})).json()["session_id"]
                created = await client.post(
                    "/api/visitor/v1/recognitions",
                    headers={"X-Visitor-Session": first},
                    files={"image": ("artifact.png", PNG, "image/png")},
                )
                self.assertEqual(201, created.status_code)
                recognition_id = created.json()["recognition_id"]

                owned = await client.get(
                    f"/api/visitor/v1/recognitions/{recognition_id}",
                    headers={"X-Visitor-Session": first},
                )
                self.assertEqual(200, owned.status_code)
                isolated = await client.get(
                    f"/api/visitor/v1/recognitions/{recognition_id}",
                    headers={"X-Visitor-Session": second},
                )
                self.assertEqual(404, isolated.status_code)

                confirmed = await client.post(
                    f"/api/visitor/v1/recognitions/{recognition_id}/confirm",
                    headers={"X-Visitor-Session": first},
                    json={"candidate_id": "candidate-1"},
                )
                self.assertEqual(200, confirmed.status_code)
                self.assertEqual("CONFIRMED", confirmed.json()["status"])
                deleted = await client.delete(
                    f"/api/visitor/v1/sessions/{first}",
                    headers={"X-Visitor-Session": first},
                )
                self.assertEqual(200, deleted.status_code)
                self.assertIsNone(service.get(first, recognition_id))

    async def test_upload_requires_session_and_valid_image(self):
        service = RecognitionService(Settings(), visual=FakeVisual(), verifier=FakeVerifier([]))
        app = create_app(Settings(), upstream=FakeUpstream(), recognition_service=service)
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            missing_session = await client.post(
                "/api/visitor/v1/recognitions",
                files={"image": ("artifact.png", PNG, "image/png")},
            )
            self.assertEqual(401, missing_session.status_code)
            session_id = (await client.post("/api/visitor/v1/sessions", json={})).json()["session_id"]
            invalid = await client.post(
                "/api/visitor/v1/recognitions",
                headers={"X-Visitor-Session": session_id},
                files={"image": ("artifact.png", b"spoofed", "image/png")},
            )
            self.assertEqual(400, invalid.status_code)


if __name__ == "__main__":
    unittest.main()
