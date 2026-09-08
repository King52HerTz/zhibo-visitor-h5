from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator


GuideMode = Literal["quick", "deep", "child"]
RecognitionStatus = Literal["READY", "NOT_FOUND", "ERROR", "CONFIRMED"]
KnowledgeStatus = Literal["VERIFIED", "PARTIAL", "NOT_FOUND"]


class SessionCreateRequest(BaseModel):
    museum_id: str | None = Field(default=None, max_length=80)


class SessionResponse(BaseModel):
    session_id: str
    expires_at: datetime
    capabilities: dict[str, bool]


class GuideRequest(BaseModel):
    session_id: str = Field(min_length=1, max_length=120)
    message: str = Field(min_length=1, max_length=600)
    mode: GuideMode = "quick"
    museum: str | None = Field(default="湖南省博物馆", max_length=120)
    exhibition: str | None = Field(default=None, max_length=120)
    exhibit_id: str | None = Field(default=None, max_length=120)


class PublicSource(BaseModel):
    title: str
    page: str | int | None = None
    excerpt: str


class PublicMessage(BaseModel):
    role: Literal["visitor", "guide"]
    content: str
    timestamp: str | None = None
    sources: list[PublicSource] = Field(default_factory=list)


class SessionDetailResponse(BaseModel):
    session_id: str
    expires_at: datetime
    messages: list[PublicMessage] = Field(default_factory=list)


class SessionDeleteResponse(BaseModel):
    session_id: str
    deleted: bool = True


class HealthResponse(BaseModel):
    status: Literal["ok"]
    upstream: Literal["configured", "not_configured"]
    recognition: Literal["configured", "not_configured"]
    catalog: Literal["configured", "bundled"] = "bundled"
    version: str = "stage-5"


class CatalogObservation(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    detail: str = Field(min_length=1, max_length=800)


class CatalogSource(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    page: str | None = Field(default=None, max_length=80)
    excerpt: str = Field(min_length=1, max_length=1200)
    status: Literal["verified", "interpretation", "missing"] = "verified"


class CatalogExhibit(BaseModel):
    id: str = Field(min_length=1, max_length=120, pattern=r"^[a-zA-Z0-9][a-zA-Z0-9_-]*$")
    name: str = Field(min_length=1, max_length=120)
    era: str = Field(min_length=1, max_length=80)
    material: str = Field(min_length=1, max_length=120)
    category: str = Field(min_length=1, max_length=80)
    summary: str = Field(min_length=1, max_length=1200)
    image_key: str = Field(default="", max_length=80, pattern=r"^[a-zA-Z0-9_-]*$")
    image_url: str | None = Field(default=None, max_length=1000)
    image_sha256: str | None = Field(default=None, pattern=r"^[a-fA-F0-9]{64}$")
    tags: list[str] = Field(default_factory=list, max_length=20)
    observations: list[CatalogObservation] = Field(default_factory=list, max_length=12)
    sources: list[CatalogSource] = Field(default_factory=list, min_length=1, max_length=12)

    @model_validator(mode="after")
    def validate_image(self):
        if not self.image_key and not self.image_url:
            raise ValueError("展品必须提供图片标识或公开图片地址")
        if self.image_url and not (self.image_url.startswith("https://") or self.image_url.startswith("/")):
            raise ValueError("公开图片地址只允许 HTTPS 或站内相对路径")
        return self


class CatalogResponse(BaseModel):
    version: str = Field(min_length=1, max_length=120)
    published_at: str = Field(min_length=1, max_length=80)
    content_source: Literal["bundled", "remote"] = "bundled"
    exhibits: list[CatalogExhibit] = Field(min_length=1, max_length=200)


class PublicEvent(BaseModel):
    type: str
    data: dict[str, Any] = Field(default_factory=dict)


class RecognitionCandidate(BaseModel):
    candidate_id: str
    name: str
    confidence: float = Field(ge=0, le=1)
    visual_basis: list[str] = Field(default_factory=list)
    knowledge_status: KnowledgeStatus
    sources: list[PublicSource] = Field(default_factory=list)


class RecognitionResponse(BaseModel):
    recognition_id: str
    status: RecognitionStatus
    observations: list[str] = Field(default_factory=list)
    candidates: list[RecognitionCandidate] = Field(default_factory=list)
    message: str
    created_at: datetime
    confirmed_candidate_id: str | None = None


class RecognitionConfirmRequest(BaseModel):
    candidate_id: str = Field(min_length=1, max_length=120)
