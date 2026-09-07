from __future__ import annotations

import json
from collections.abc import AsyncIterator
from typing import Any

import httpx

from .config import Settings


class UpstreamError(Exception):
    def __init__(self, kind: str):
        super().__init__(kind)
        self.kind = kind


class UpstreamUnauthorized(UpstreamError):
    def __init__(self):
        super().__init__("unauthorized")


class StableMuseumClient:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        self.settings = settings
        timeout = httpx.Timeout(
            connect=5.0,
            read=float(settings.upstream_timeout_seconds),
            write=10.0,
            pool=5.0,
        )
        self.client = client or httpx.AsyncClient(timeout=timeout)
        self._token: str | None = None

    async def close(self) -> None:
        await self.client.aclose()

    async def _login(self) -> str:
        if not self.settings.upstream_configured:
            raise UpstreamError("not_configured")
        try:
            response = await self.client.post(
                f"{self.settings.upstream_base_url}/auth/login",
                json={
                    "username": self.settings.upstream_username,
                    "password": self.settings.upstream_password,
                },
            )
            if response.status_code in {401, 403}:
                raise UpstreamError("identity_rejected")
            response.raise_for_status()
            payload = response.json()
        except (httpx.HTTPError, ValueError) as exc:
            raise UpstreamError("login_failed") from exc
        token = payload.get("access_token") if isinstance(payload, dict) else None
        if not isinstance(token, str) or not token:
            raise UpstreamError("login_failed")
        self._token = token
        return token

    async def _get_token(self, force_refresh: bool = False) -> str:
        if force_refresh or not self._token:
            return await self._login()
        return self._token

    async def _stream_once(self, token: str, session_id: str, body: dict[str, Any]) -> AsyncIterator[Any]:
        try:
            async with self.client.stream(
                "POST",
                f"{self.settings.upstream_base_url}/chat/stream",
                headers={"Authorization": f"Bearer {token}"},
                json={**body, "session_id": session_id},
            ) as response:
                if response.status_code in {401, 403}:
                    raise UpstreamUnauthorized()
                if response.status_code == 429:
                    raise UpstreamError("rate_limited")
                if response.status_code >= 500:
                    raise UpstreamError("service_unavailable")
                if response.status_code >= 400:
                    raise UpstreamError("bad_request")
                async for event in self._read_sse(response):
                    yield event
        except UpstreamError:
            raise
        except httpx.TimeoutException as exc:
            raise UpstreamError("timeout") from exc
        except httpx.HTTPError as exc:
            raise UpstreamError("network") from exc

    async def stream_guide(
        self,
        session_id: str,
        message: str,
        museum: str | None,
        exhibition: str | None,
        exhibit_id: str | None,
    ) -> AsyncIterator[Any]:
        body = {"message": message}
        token = await self._get_token()
        try:
            async for event in self._stream_once(
                token,
                session_id,
                body,
            ):
                yield event
        except UpstreamUnauthorized:
            self._token = None
            async for event in self._stream_once(await self._get_token(force_refresh=True), session_id, body):
                yield event

    async def get_session(self, session_id: str) -> list[dict[str, Any]]:
        token = await self._get_token()
        try:
            response = await self.client.get(
                f"{self.settings.upstream_base_url}/sessions/{session_id}",
                headers={"Authorization": f"Bearer {token}"},
            )
            if response.status_code in {401, 403}:
                self._token = None
                token = await self._get_token(force_refresh=True)
                response = await self.client.get(
                    f"{self.settings.upstream_base_url}/sessions/{session_id}",
                    headers={"Authorization": f"Bearer {token}"},
                )
            if response.status_code >= 400:
                return []
            payload = response.json()
            return payload.get("messages", []) if isinstance(payload, dict) else []
        except (httpx.HTTPError, ValueError, UpstreamError):
            return []

    async def delete_session(self, session_id: str) -> None:
        try:
            token = await self._get_token()
            await self.client.delete(
                f"{self.settings.upstream_base_url}/sessions/{session_id}",
                headers={"Authorization": f"Bearer {token}"},
            )
        except (httpx.HTTPError, UpstreamError):
            return

    @staticmethod
    async def _read_sse(response: httpx.Response) -> AsyncIterator[Any]:
        data_lines: list[str] = []
        async for line in response.aiter_lines():
            if line.startswith("data:"):
                data_lines.append(line[5:].lstrip())
                continue
            if line or not data_lines:
                continue
            data = "\n".join(data_lines)
            data_lines = []
            if data == "[DONE]":
                yield "[DONE]"
                continue
            try:
                yield json.loads(data)
            except json.JSONDecodeError:
                continue
        if data_lines:
            data = "\n".join(data_lines)
            if data == "[DONE]":
                yield "[DONE]"
