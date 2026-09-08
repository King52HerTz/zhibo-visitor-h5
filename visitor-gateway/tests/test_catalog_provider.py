from __future__ import annotations

import unittest

import httpx

from app.catalog import CatalogProvider
from app.config import Settings


def remote_catalog(exhibits=None):
    return {
        "version": "museum-release-12",
        "published_at": "2026-09-08T10:00:00+08:00",
        "exhibits": exhibits or [
            {
                "id": "mwd-001",
                "name": "远端发布展品",
                "era": "西汉",
                "material": "丝织物",
                "category": "服饰工艺",
                "summary": "由管理端公开发布的游客可见摘要。",
                "image_url": "https://cdn.example.com/museum/mwd-001.webp",
                "tags": ["马王堆精选"],
                "observations": [{"title": "观察纹理", "detail": "从公开资料确认的观察提示。"}],
                "sources": [{"title": "馆方公开资料", "excerpt": "游客可见的来源摘要。"}],
                "internal_note": "此字段必须被模型忽略",
            }
        ],
    }


class CatalogProviderTests(unittest.IsolatedAsyncioTestCase):
    async def test_remote_catalog_replaces_bundled_and_uses_conditional_refresh(self):
        seen_headers = []

        def handler(request: httpx.Request):
            seen_headers.append(request.headers)
            if len(seen_headers) == 1:
                return httpx.Response(200, json=remote_catalog(), headers={"ETag": '"release-12"'})
            return httpx.Response(304)

        client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        provider = CatalogProvider(
            Settings(
                catalog_url="https://manager.example.com/api/public/v1/catalog",
                catalog_api_key="test-only-placeholder",
            ),
            client=client,
        )
        try:
            catalog = await provider.current()
            self.assertEqual(catalog.exhibits[0].id, "mwd-001")
            self.assertEqual(catalog.content_source, "remote")
            self.assertEqual(provider.source, "remote")
            self.assertEqual(seen_headers[0]["Authorization"], "Bearer test-only-placeholder")
            self.assertNotIn("internal_note", catalog.model_dump_json())

            provider._next_refresh_at = 0
            unchanged = await provider.current()
            self.assertEqual(unchanged.version, "museum-release-12")
            self.assertEqual(seen_headers[1]["If-None-Match"], '"release-12"')
        finally:
            await client.aclose()

    async def test_invalid_remote_catalog_keeps_bundled_version(self):
        duplicate = remote_catalog()
        duplicate["exhibits"] = [duplicate["exhibits"][0], duplicate["exhibits"][0]]

        def handler(_: httpx.Request):
            return httpx.Response(200, json=duplicate)

        client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        provider = CatalogProvider(
            Settings(catalog_url="https://manager.example.com/catalog"),
            client=client,
        )
        try:
            catalog = await provider.current()
            self.assertEqual(provider.source, "bundled")
            self.assertEqual(catalog.exhibits[0].id, "demo-su-sha")
        finally:
            await client.aclose()

    async def test_unsafe_remote_image_url_is_rejected(self):
        payload = remote_catalog()
        payload["exhibits"][0]["image_url"] = "http://private.example.com/image.webp"

        def handler(_: httpx.Request):
            return httpx.Response(200, json=payload)

        client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        provider = CatalogProvider(
            Settings(catalog_url="https://manager.example.com/catalog"),
            client=client,
        )
        try:
            catalog = await provider.current()
            self.assertEqual(catalog.exhibits[0].id, "demo-su-sha")
        finally:
            await client.aclose()

    async def test_explicitly_unpublished_item_rejects_whole_release(self):
        payload = remote_catalog()
        payload["exhibits"][0]["publication_status"] = "draft"

        def handler(_: httpx.Request):
            return httpx.Response(200, json=payload)

        client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        provider = CatalogProvider(
            Settings(catalog_url="https://manager.example.com/catalog"),
            client=client,
        )
        try:
            catalog = await provider.current()
            self.assertEqual(catalog.content_source, "bundled")
            self.assertEqual(catalog.exhibits[0].id, "demo-su-sha")
        finally:
            await client.aclose()


if __name__ == "__main__":
    unittest.main()
