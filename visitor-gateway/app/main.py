from __future__ import annotations

import asyncio
import json
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, Request, Response, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from .config import Settings
from .models import (
    GuideRequest,
    HealthResponse,
    RecognitionConfirmRequest,
    RecognitionResponse,
    SessionCreateRequest,
    SessionDeleteResponse,
    SessionDetailResponse,
    SessionResponse,
)
from .rate_limit import SlidingWindowRateLimiter
from .recognition import RecognitionInputError, RecognitionService
from .sanitize import public_event, public_message
from .session_store import SessionStore
from .upstream import StableMuseumClient, UpstreamError

SESSION_HEADER = "X-Visitor-Session"
PREFIX = "/api/visitor/v1"


def _sse(event_type: str, data: dict) -> str:
    return f"event: {event_type}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


def _client_key(request: Request) -> str:
    return request.client.host if request.client else "unknown"


def _friendly_error(error: UpstreamError) -> tuple[str, str]:
    if error.kind == "rate_limited":
        return "RATE_LIMITED", "访问有些频繁，请稍后再试。"
    if error.kind in {"unauthorized", "identity_rejected"}:
        return "SESSION_EXPIRED", "游客导览暂时需要重新连接。"
    return "INSUFFICIENT_EVIDENCE", "暂时无法确认馆方资料，请稍后重试。"


def create_app(
    settings: Settings | None = None,
    upstream: StableMuseumClient | None = None,
    recognition_service: RecognitionService | None = None,
) -> FastAPI:
    config = settings or Settings.from_env()
    sessions = SessionStore(config.session_ttl_seconds)
    limiter = SlidingWindowRateLimiter(config.rate_limit_per_minute)
    guide_slots = asyncio.Semaphore(config.max_concurrent_guides)
    client = upstream or StableMuseumClient(config)
    recognizer = recognition_service or RecognitionService(config)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        yield
        if upstream is None:
            await client.close()
        if recognition_service is None:
            await recognizer.close()

    app = FastAPI(
        title="智博导览游客网关",
        version="0.2.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
        lifespan=lifespan,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(config.cors_origins),
        allow_credentials=False,
        allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
        allow_headers=["Content-Type", SESSION_HEADER],
    )

    def require_session(request: Request, session_id: str):
        header_session = request.headers.get(SESSION_HEADER, "")
        if not header_session or header_session != session_id:
            raise HTTPException(status_code=401, detail="游客会话无效")
        session = sessions.get(session_id)
        if session is None:
            raise HTTPException(status_code=401, detail="游客会话已过期")
        return session

    @app.get(f"{PREFIX}/health", response_model=HealthResponse)
    async def health() -> HealthResponse:
        return HealthResponse(
            status="ok",
            upstream="configured" if config.upstream_configured else "not_configured",
            recognition="configured" if config.recognition_configured else "not_configured",
        )

    @app.post(f"{PREFIX}/sessions", response_model=SessionResponse, status_code=201)
    async def create_session(request: Request, _: SessionCreateRequest) -> SessionResponse:
        allowed, retry_after = limiter.allow(_client_key(request))
        if not allowed:
            raise HTTPException(
                status_code=429,
                detail="访问有些频繁，请稍后再试。",
                headers={"Retry-After": str(retry_after)},
            )
        session = sessions.create()
        return SessionResponse(
            session_id=session.session_id,
            expires_at=session.expires_at,
            capabilities={"guide_stream": True, "recognition": config.recognition_configured, "exhibits": False},
        )

    def require_header_session(request: Request):
        session_id = request.headers.get(SESSION_HEADER, "")
        if not session_id:
            raise HTTPException(status_code=401, detail="游客会话无效")
        session = sessions.get(session_id)
        if session is None:
            raise HTTPException(status_code=401, detail="游客会话已过期")
        return session

    @app.post(f"{PREFIX}/recognitions", response_model=RecognitionResponse, status_code=201)
    async def create_recognition(request: Request, image: UploadFile = File(...)) -> RecognitionResponse:
        session = require_header_session(request)
        allowed, retry_after = limiter.allow(_client_key(request))
        if not allowed:
            raise HTTPException(
                status_code=429,
                detail="访问有些频繁，请稍后再试。",
                headers={"Retry-After": str(retry_after)},
            )
        data = await image.read(config.max_image_bytes + 1)
        try:
            return await recognizer.recognize(session.session_id, image.content_type or "", data)
        except RecognitionInputError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error

    @app.get(f"{PREFIX}/recognitions/{{recognition_id}}", response_model=RecognitionResponse)
    async def get_recognition(request: Request, recognition_id: str) -> RecognitionResponse:
        session = require_header_session(request)
        result = recognizer.get(session.session_id, recognition_id)
        if result is None:
            raise HTTPException(status_code=404, detail="识别记录不存在或已过期")
        return result

    @app.post(f"{PREFIX}/recognitions/{{recognition_id}}/confirm", response_model=RecognitionResponse)
    async def confirm_recognition(
        request: Request,
        recognition_id: str,
        payload: RecognitionConfirmRequest,
    ) -> RecognitionResponse:
        session = require_header_session(request)
        try:
            result = recognizer.confirm(session.session_id, recognition_id, payload.candidate_id)
        except RecognitionInputError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        if result is None:
            raise HTTPException(status_code=404, detail="识别记录不存在或已过期")
        return result

    @app.get(f"{PREFIX}/sessions/{{session_id}}", response_model=SessionDetailResponse)
    async def get_session(request: Request, session_id: str) -> SessionDetailResponse:
        session = require_session(request, session_id)
        raw_messages = await client.get_session(session.upstream_session_id)
        messages = [item for raw in raw_messages if (item := public_message(raw)) is not None]
        return SessionDetailResponse(
            session_id=session.session_id,
            expires_at=session.expires_at,
            messages=messages,
        )

    @app.delete(f"{PREFIX}/sessions/{{session_id}}", response_model=SessionDeleteResponse)
    async def delete_session(request: Request, session_id: str) -> SessionDeleteResponse:
        session = require_session(request, session_id)
        await client.delete_session(session.upstream_session_id)
        recognizer.delete_session(session_id)
        sessions.delete(session_id)
        return SessionDeleteResponse(session_id=session_id)

    @app.post(f"{PREFIX}/guide/stream")
    async def guide_stream(request: Request, payload: GuideRequest):
        session = require_session(request, payload.session_id)
        allowed, retry_after = limiter.allow(_client_key(request))
        if not allowed:
            raise HTTPException(
                status_code=429,
                detail="访问有些频繁，请稍后再试。",
                headers={"Retry-After": str(retry_after)},
            )
        if guide_slots.locked():
            raise HTTPException(
                status_code=429,
                detail="当前导览请求较多，请稍后再试。",
                headers={"Retry-After": "5"},
            )

        await guide_slots.acquire()

        async def event_stream() -> AsyncIterator[str]:
            try:
                async for raw_event in client.stream_guide(
                    session.upstream_session_id,
                    payload.message,
                    payload.museum,
                    payload.exhibition,
                    payload.exhibit_id,
                ):
                    event = public_event(raw_event)
                    if event is not None:
                        yield _sse(event.type, event.data)
                yield _sse("done", {})
            except UpstreamError as error:
                code, message = _friendly_error(error)
                yield _sse("outcome", {"code": code, "message": message})
                yield _sse("done", {})
            except asyncio.CancelledError:
                raise
            finally:
                guide_slots.release()

        return StreamingResponse(
            event_stream(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache, no-store, must-revalidate",
                "X-Accel-Buffering": "no",
            },
        )

    @app.api_route("/{path:path}", methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"])
    async def deny_unknown_routes(path: str) -> Response:
        return Response(content="游客网关不提供此接口", status_code=404, media_type="text/plain")

    return app


app = create_app()
