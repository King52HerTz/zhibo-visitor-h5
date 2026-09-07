from __future__ import annotations

import os
from dataclasses import dataclass


def _int_env(name: str, default: int, minimum: int = 1) -> int:
    try:
        return max(minimum, int(os.getenv(name, str(default))))
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    upstream_base_url: str = "http://127.0.0.1:8000"
    upstream_username: str = ""
    upstream_password: str = ""
    session_ttl_seconds: int = 1800
    rate_limit_per_minute: int = 12
    max_concurrent_guides: int = 2
    max_message_length: int = 600
    upstream_timeout_seconds: int = 45
    cors_origins: tuple[str, ...] = ("http://localhost:8020", "http://127.0.0.1:8020")

    @classmethod
    def from_env(cls) -> "Settings":
        origins = tuple(
            origin.strip()
            for origin in os.getenv(
                "VISITOR_CORS_ORIGINS",
                "http://localhost:8020,http://127.0.0.1:8020",
            ).split(",")
            if origin.strip()
        )
        return cls(
            upstream_base_url=os.getenv("MUSEUM_RAG_BASE_URL", cls.upstream_base_url).rstrip("/"),
            upstream_username=os.getenv("MUSEUM_RAG_USERNAME", "").strip(),
            upstream_password=os.getenv("MUSEUM_RAG_PASSWORD", ""),
            session_ttl_seconds=_int_env("VISITOR_SESSION_TTL_SECONDS", 1800),
            rate_limit_per_minute=_int_env("VISITOR_RATE_LIMIT_PER_MINUTE", 12),
            max_concurrent_guides=_int_env("VISITOR_MAX_CONCURRENT_GUIDES", 2),
            max_message_length=_int_env("VISITOR_MAX_MESSAGE_LENGTH", 600),
            upstream_timeout_seconds=_int_env("VISITOR_UPSTREAM_TIMEOUT_SECONDS", 45),
            cors_origins=origins,
        )

    @property
    def upstream_configured(self) -> bool:
        return bool(self.upstream_base_url and self.upstream_username and self.upstream_password)
