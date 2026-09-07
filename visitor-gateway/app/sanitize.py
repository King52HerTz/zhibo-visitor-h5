from __future__ import annotations

import ntpath
import re
from typing import Any

from .models import PublicEvent, PublicMessage, PublicSource


def clean_text(value: Any, limit: int = 1200) -> str:
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    return text[:limit].rstrip()


def public_filename(value: Any) -> str:
    filename = str(value or "").replace("\\", "/")
    filename = ntpath.basename(filename).strip()
    return filename if filename and filename not in {".", ".."} else "馆方公开资料"


def sources_from_trace(trace: Any) -> list[PublicSource]:
    if not isinstance(trace, dict):
        return []
    chunks = trace.get("retrieved_chunks") or []
    sources: list[PublicSource] = []
    seen: set[tuple[str, str]] = set()
    for chunk in chunks:
        if not isinstance(chunk, dict):
            continue
        excerpt = clean_text(chunk.get("text") or chunk.get("content"), 360)
        if not excerpt:
            continue
        title = public_filename(chunk.get("filename") or chunk.get("source"))
        page = chunk.get("page_number")
        key = (title, excerpt)
        if key in seen:
            continue
        seen.add(key)
        sources.append(PublicSource(title=title, page=page, excerpt=excerpt))
    return sources[:5]


def outcome_from_trace(trace: Any, has_sources: bool) -> str:
    if isinstance(trace, dict):
        outcome = trace.get("retrieval_outcome")
        if outcome in {"ANSWERABLE", "NO_KNOWLEDGE", "INSUFFICIENT_EVIDENCE"}:
            return outcome
    return "ANSWERABLE" if has_sources else "NO_KNOWLEDGE"


def public_event(payload: Any) -> PublicEvent | None:
    if payload == "[DONE]" or (isinstance(payload, dict) and payload.get("type") == "done"):
        return PublicEvent(type="done")
    if not isinstance(payload, dict):
        return None

    event_type = payload.get("type")
    if event_type == "content":
        content = clean_text(payload.get("content"), 2000)
        return PublicEvent(type="content", data={"delta": content}) if content else None
    if event_type == "rag_step":
        return PublicEvent(type="status", data={"stage": "retrieving", "message": "正在查找馆方资料"})
    if event_type == "trace":
        trace = payload.get("rag_trace")
        sources = sources_from_trace(trace)
        return PublicEvent(
            type="evidence",
            data={
                "sources": [source.model_dump(exclude_none=True) for source in sources],
                "code": outcome_from_trace(trace, bool(sources)),
            },
        )
    if event_type == "error":
        code = str(payload.get("code") or "").upper()
        outcome = "RATE_LIMITED" if code in {"RATE_LIMITED", "HTTP_429"} else "INSUFFICIENT_EVIDENCE"
        return PublicEvent(type="outcome", data={"code": outcome, "message": _outcome_message(outcome)})
    if event_type == "hitl_request":
        return PublicEvent(
            type="outcome",
            data={"code": "INSUFFICIENT_EVIDENCE", "message": "这个问题需要更多上下文才能确认。"},
        )
    return None


def public_message(item: Any) -> PublicMessage | None:
    if not isinstance(item, dict):
        return None
    content = clean_text(item.get("content"), 4000)
    if not content:
        return None
    role = "visitor" if item.get("type") == "human" else "guide"
    return PublicMessage(
        role=role,
        content=content,
        timestamp=clean_text(item.get("timestamp"), 64) or None,
        sources=sources_from_trace(item.get("rag_trace")),
    )


def _outcome_message(code: str) -> str:
    return {
        "RATE_LIMITED": "访问有些频繁，请稍后再试。",
        "SESSION_EXPIRED": "本次导览已过期，请重新开始。",
        "NO_KNOWLEDGE": "当前馆方资料暂未收录这个问题。",
        "INSUFFICIENT_EVIDENCE": "暂时无法确认馆方资料，请稍后重试。",
    }.get(code, "暂时无法完成这次导览。")
