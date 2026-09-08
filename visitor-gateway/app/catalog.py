from __future__ import annotations

import asyncio
import hashlib
import json
import time

import httpx
from pydantic import ValidationError

from .config import Settings
from .models import CatalogExhibit, CatalogResponse


_PUBLISHED_AT = "2026-09-08T00:00:00+08:00"
_EXHIBITS = [
    {
        "id": "demo-su-sha",
        "name": "素纱单衣",
        "era": "西汉",
        "material": "丝织物",
        "category": "服饰工艺",
        "summary": "一件轻薄通透的汉代衣物，从纤维、形制和保存状态都能看见当时的织造技术。",
        "image_key": "silk",
        "tags": ["马王堆精选", "服饰工艺", "汉代生活"],
        "observations": [
            {"title": "先看衣身的薄透程度", "detail": "轻薄感不只是视觉效果，也提示了丝纤维处理和织造工艺的复杂性。"},
            {"title": "再看交领与直裾", "detail": "领口和衣襟的组合可以帮助我们理解汉代服饰的形制结构。"},
            {"title": "最后看织物边缘", "detail": "边缘的收束和纹理，是观察织造与保存状态的一个入口。"},
        ],
        "sources": [{"title": "马王堆汉墓出土文物图录（演示资料）", "page": "第 18 页", "excerpt": "资料将衣物的材质、形制与出土信息分别记录，适合用于核对展品的基础事实。", "status": "verified"}],
    },
    {
        "id": "demo-lacquer-coffin",
        "name": "黑地彩绘棺",
        "era": "西汉",
        "material": "漆木",
        "category": "汉代生活",
        "summary": "黑地与彩绘共同构成庄重而繁复的视觉秩序，可以从纹样读出当时的观念世界。",
        "image_key": "lacquer",
        "tags": ["马王堆精选", "汉代生活"],
        "observations": [
            {"title": "看黑地与彩绘的层次", "detail": "底色与纹样的对比让器物获得更强的仪式感。"},
            {"title": "寻找连续纹样", "detail": "连续纹样常常帮助我们理解器物表面的秩序和观看节奏。"},
            {"title": "注意漆面的光泽", "detail": "光泽既来自工艺，也和保存环境、年代变化有关。"},
        ],
        "sources": [{"title": "马王堆汉墓漆器研究资料（演示资料）", "excerpt": "资料用于演示漆木器的基础信息、纹样观察和来源呈现。", "status": "verified"}],
    },
    {
        "id": "demo-silk-book",
        "name": "帛书残片",
        "era": "西汉",
        "material": "帛",
        "category": "文献与思想",
        "summary": "柔软的帛面承载着文字和思想，也让我们看到古人如何保存、阅读和传递知识。",
        "image_key": "silk-book",
        "tags": ["汉代生活", "文献与思想"],
        "observations": [
            {"title": "观察文字的排列", "detail": "文字的方向和行距，是进入古代书写习惯的第一步。"},
            {"title": "看帛面留下的折痕", "detail": "折痕和缺损记录了材料被使用、收纳与埋藏的经历。"},
            {"title": "想象它的阅读场景", "detail": "在证据允许的范围内，我们可以讨论它可能服务的知识活动。"},
        ],
        "sources": [{"title": "出土帛书整理说明（演示资料）", "excerpt": "资料用于演示帛书的文字排列、材料状态和阅读线索。", "status": "interpretation"}],
    },
]


def published_catalog() -> CatalogResponse:
    payload = json.dumps(_EXHIBITS, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    version = hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]
    return CatalogResponse(
        version=version,
        published_at=_PUBLISHED_AT,
        exhibits=[CatalogExhibit.model_validate(item) for item in _EXHIBITS],
    )


class CatalogProvider:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        self.settings = settings
        self._owns_client = client is None
        self._client = client or httpx.AsyncClient(
            timeout=httpx.Timeout(connect=5.0, read=15.0, write=5.0, pool=5.0)
        )
        self._catalog = published_catalog()
        self._upstream_etag = ""
        self._next_refresh_at = 0.0
        self._lock = asyncio.Lock()

    async def close(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    @property
    def source(self) -> str:
        return self._catalog.content_source

    async def current(self) -> CatalogResponse:
        if not self.settings.catalog_configured or time.monotonic() < self._next_refresh_at:
            return self._catalog
        async with self._lock:
            if time.monotonic() < self._next_refresh_at:
                return self._catalog
            await self._refresh()
        return self._catalog

    async def _refresh(self) -> None:
        headers = {"Accept": "application/json"}
        if self.settings.catalog_api_key:
            headers["Authorization"] = f"Bearer {self.settings.catalog_api_key}"
        if self._upstream_etag:
            headers["If-None-Match"] = self._upstream_etag
        try:
            response = await self._client.get(self.settings.catalog_url, headers=headers)
            if response.status_code == 304:
                return
            response.raise_for_status()
            raw_catalog = response.json()
            if not isinstance(raw_catalog, dict) or not isinstance(raw_catalog.get("exhibits"), list):
                raise ValueError("目录结构无效")
            for item in raw_catalog["exhibits"]:
                if not isinstance(item, dict):
                    raise ValueError("展品结构无效")
                publication = item.get("publication_status", item.get("status"))
                if publication is not None and str(publication).lower() != "published":
                    raise ValueError("公开目录包含未发布内容")
            candidate = CatalogResponse.model_validate({**raw_catalog, "content_source": "remote"})
            identifiers = [item.id for item in candidate.exhibits]
            if len(identifiers) != len(set(identifiers)):
                raise ValueError("目录包含重复展品编号")
            self._catalog = candidate
            self._upstream_etag = response.headers.get("ETag") or f'"{candidate.version}"'
        except (httpx.HTTPError, ValueError, ValidationError):
            # 保留最后一次完整有效目录，不向游客暴露上游错误和认证细节。
            pass
        finally:
            self._next_refresh_at = time.monotonic() + self.settings.catalog_refresh_seconds
