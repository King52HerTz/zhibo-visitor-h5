from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


GuideMode = Literal["quick", "deep", "child"]


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
    version: str = "stage-2"


class PublicEvent(BaseModel):
    type: str
    data: dict[str, Any] = Field(default_factory=dict)
