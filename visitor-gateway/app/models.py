from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


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
    version: str = "stage-4"


class CatalogObservation(BaseModel):
    title: str
    detail: str


class CatalogSource(BaseModel):
    title: str
    page: str | None = None
    excerpt: str
    status: Literal["verified", "interpretation", "missing"] = "verified"


class CatalogExhibit(BaseModel):
    id: str
    name: str
    era: str
    material: str
    category: str
    summary: str
    image_key: str
    tags: list[str] = Field(default_factory=list)
    observations: list[CatalogObservation] = Field(default_factory=list)
    sources: list[CatalogSource] = Field(default_factory=list)


class CatalogResponse(BaseModel):
    version: str
    published_at: str
    exhibits: list[CatalogExhibit]


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
