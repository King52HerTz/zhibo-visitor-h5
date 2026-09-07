from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from secrets import token_urlsafe


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass
class VisitorSession:
    session_id: str
    upstream_session_id: str
    created_at: datetime
    expires_at: datetime


class SessionStore:
    def __init__(self, ttl_seconds: int):
        self._ttl = ttl_seconds
        self._sessions: dict[str, VisitorSession] = {}

    def _cleanup(self, now: datetime) -> None:
        expired = [key for key, value in self._sessions.items() if value.expires_at <= now]
        for key in expired:
            self._sessions.pop(key, None)

    def create(self) -> VisitorSession:
        now = utc_now()
        self._cleanup(now)
        session_id = token_urlsafe(24)
        session = VisitorSession(
            session_id=session_id,
            upstream_session_id=f"visitor-{session_id}",
            created_at=now,
            expires_at=now + timedelta(seconds=self._ttl),
        )
        self._sessions[session_id] = session
        return session

    def get(self, session_id: str) -> VisitorSession | None:
        now = utc_now()
        self._cleanup(now)
        session = self._sessions.get(session_id)
        if session is not None:
            return session
        return None

    def delete(self, session_id: str) -> bool:
        return self._sessions.pop(session_id, None) is not None
