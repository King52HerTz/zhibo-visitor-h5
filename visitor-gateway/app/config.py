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
    rag_bridge_api_key: str = ""
    visual_model_base_url: str = ""
    visual_model_api_key: str = ""
    visual_model_name: str = ""
    recognition_timeout_seconds: int = 45
    max_image_bytes: int = 8 * 1024 * 1024
    recognition_temp_dir: str = ""
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
            rag_bridge_api_key=os.getenv("MUSEUM_RAG_BRIDGE_API_KEY", "").strip(),
            visual_model_base_url=os.getenv("VISUAL_MODEL_BASE_URL", "").strip().rstrip("/"),
            visual_model_api_key=os.getenv("VISUAL_MODEL_API_KEY", "").strip(),
            visual_model_name=os.getenv("VISUAL_MODEL_NAME", "").strip(),
            recognition_timeout_seconds=_int_env("VISITOR_RECOGNITION_TIMEOUT_SECONDS", 45),
            max_image_bytes=_int_env("VISITOR_MAX_IMAGE_BYTES", 8 * 1024 * 1024, 1024),
            recognition_temp_dir=os.getenv("VISITOR_RECOGNITION_TEMP_DIR", "").strip(),
            cors_origins=origins,
        )

    @property
    def upstream_configured(self) -> bool:
        return bool(self.upstream_base_url and self.upstream_username and self.upstream_password)

    @property
    def recognition_configured(self) -> bool:
        return bool(
            self.upstream_base_url
            and self.rag_bridge_api_key
            and self.visual_model_base_url
            and self.visual_model_api_key
            and self.visual_model_name
        )
