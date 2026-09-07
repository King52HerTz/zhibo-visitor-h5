from __future__ import annotations

import base64
import json
import tempfile
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from secrets import token_urlsafe
from typing import Protocol

import httpx

from .config import Settings
from .models import PublicSource, RecognitionCandidate, RecognitionResponse


SUPPORTED_IMAGES = {
    "image/jpeg": (b"\xff\xd8\xff", ".jpg"),
    "image/png": (b"\x89PNG\r\n\x1a\n", ".png"),
    "image/webp": (b"RIFF", ".webp"),
}


class RecognitionInputError(ValueError):
    pass


class RecognitionUnavailable(RuntimeError):
    pass


@dataclass(frozen=True)
class VisualCandidateInput:
    name: str
    confidence: float
    visual_basis: list[str]


@dataclass(frozen=True)
class VisualAnalysis:
    observations: list[str]
    candidates: list[VisualCandidateInput]


@dataclass(frozen=True)
class VerificationResult:
    status: str
    sources: list[PublicSource]


class VisualAnalyzer(Protocol):
    async def analyze(self, image_path: Path, content_type: str) -> VisualAnalysis: ...

    async def close(self) -> None: ...


class CandidateVerifier(Protocol):
    async def verify(self, candidate: VisualCandidateInput, museum: str) -> VerificationResult: ...

    async def close(self) -> None: ...


def _text(value: object, limit: int) -> str:
    return str(value or "").strip()[:limit]


def _json_content(value: object) -> dict:
    if isinstance(value, dict):
        return value
    if not isinstance(value, str):
        raise RecognitionUnavailable("visual_response_invalid")
    content = value.strip()
    if content.startswith("```"):
        content = content.removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    try:
        parsed = json.loads(content)
    except json.JSONDecodeError as exc:
        raise RecognitionUnavailable("visual_response_invalid") from exc
    if not isinstance(parsed, dict):
        raise RecognitionUnavailable("visual_response_invalid")
    return parsed


class OpenAICompatibleVisualAnalyzer:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        self.settings = settings
        self.client = client or httpx.AsyncClient(
            timeout=httpx.Timeout(float(settings.recognition_timeout_seconds), connect=8.0)
        )

    async def close(self) -> None:
        await self.client.aclose()

    async def analyze(self, image_path: Path, content_type: str) -> VisualAnalysis:
        if not (
            self.settings.visual_model_base_url
            and self.settings.visual_model_api_key
            and self.settings.visual_model_name
        ):
            raise RecognitionUnavailable("visual_model_not_configured")
        encoded = base64.b64encode(image_path.read_bytes()).decode("ascii")
        prompt = (
            "你是博物馆展品视觉观察器。只根据图片提出最多3个候选名称与可见依据，"
            "不要生成历史、年代、尺寸、出处等馆藏事实。无法识别时返回空候选。"
            "严格返回JSON：{observations:[string],candidates:[{name:string,confidence:0到1,visual_basis:[string]}]}。"
        )
        try:
            response = await self.client.post(
                f"{self.settings.visual_model_base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.settings.visual_model_api_key}"},
                json={
                    "model": self.settings.visual_model_name,
                    "temperature": 0,
                    "response_format": {"type": "json_object"},
                    "messages": [{
                        "role": "user",
                        "content": [
                            {"type": "text", "text": prompt},
                            {"type": "image_url", "image_url": {"url": f"data:{content_type};base64,{encoded}"}},
                        ],
                    }],
                },
            )
            if response.status_code in {401, 403}:
                raise RecognitionUnavailable("visual_model_rejected")
            response.raise_for_status()
            payload = response.json()
            content = payload["choices"][0]["message"]["content"]
            result = _json_content(content)
        except RecognitionUnavailable:
            raise
        except (httpx.HTTPError, ValueError, KeyError, TypeError) as exc:
            raise RecognitionUnavailable("visual_model_unavailable") from exc

        observations = [_text(item, 80) for item in result.get("observations", []) if _text(item, 80)][:6]
        candidates: list[VisualCandidateInput] = []
        for raw in result.get("candidates", [])[:3]:
            if not isinstance(raw, dict):
                continue
            name = _text(raw.get("name"), 80)
            if not name:
                continue
            try:
                confidence = min(1.0, max(0.0, float(raw.get("confidence", 0))))
            except (TypeError, ValueError):
                confidence = 0.0
            basis = [_text(item, 80) for item in raw.get("visual_basis", []) if _text(item, 80)][:6]
            candidates.append(VisualCandidateInput(name=name, confidence=confidence, visual_basis=basis))
        return VisualAnalysis(observations=observations, candidates=candidates)


class MuseumRagVerifier:
    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        self.settings = settings
        self.client = client or httpx.AsyncClient(
            timeout=httpx.Timeout(float(settings.recognition_timeout_seconds), connect=5.0)
        )

    async def close(self) -> None:
        await self.client.aclose()

    async def verify(self, candidate: VisualCandidateInput, museum: str) -> VerificationResult:
        if not self.settings.rag_bridge_api_key:
            raise RecognitionUnavailable("rag_bridge_not_configured")
        basis = "、".join(candidate.visual_basis)
        question = f"核验候选展品“{candidate.name}”是否被馆方资料收录。可见线索：{basis or '未提供'}"
        try:
            response = await self.client.post(
                f"{self.settings.upstream_base_url}/api/museum/query",
                headers={"Authorization": f"Bearer {self.settings.rag_bridge_api_key}"},
                json={
                    "question": question,
                    "museum": museum,
                    "scene": "visitor_visual_recognition",
                    "top_k": 3,
                    "need_trace": True,
                },
            )
            if response.status_code in {401, 403, 503}:
                raise RecognitionUnavailable("rag_bridge_rejected")
            response.raise_for_status()
            payload = response.json()
        except RecognitionUnavailable:
            raise
        except (httpx.HTTPError, ValueError) as exc:
            raise RecognitionUnavailable("rag_unavailable") from exc
        if not isinstance(payload, dict) or payload.get("code", 0) != 0:
            raise RecognitionUnavailable("rag_invalid_response")

        contexts = payload.get("contexts") if isinstance(payload.get("contexts"), list) else []
        sources: list[PublicSource] = []
        for raw in contexts[:3]:
            if not isinstance(raw, dict):
                continue
            source = _text(raw.get("title") or raw.get("source"), 160).replace("\\", "/").split("/")[-1]
            excerpt = _text(raw.get("content"), 260)
            if source and excerpt:
                sources.append(PublicSource(title=source, page=raw.get("page_number"), excerpt=excerpt))
        outcome = _text((payload.get("trace") or {}).get("retrieval_outcome"), 40)
        if sources and outcome in {"", "ANSWERABLE"}:
            return VerificationResult(status="VERIFIED", sources=sources)
        if outcome == "INSUFFICIENT_EVIDENCE":
            return VerificationResult(status="PARTIAL", sources=[])
        return VerificationResult(status="NOT_FOUND", sources=[])


@dataclass
class StoredRecognition:
    session_id: str
    response: RecognitionResponse
    expires_at: datetime


class RecognitionStore:
    def __init__(self, ttl_seconds: int):
        self.ttl_seconds = ttl_seconds
        self.items: dict[str, StoredRecognition] = {}

    def put(self, session_id: str, response: RecognitionResponse) -> None:
        self._cleanup()
        self.items[response.recognition_id] = StoredRecognition(
            session_id=session_id,
            response=response,
            expires_at=datetime.now(timezone.utc) + timedelta(seconds=self.ttl_seconds),
        )

    def get(self, session_id: str, recognition_id: str) -> RecognitionResponse | None:
        self._cleanup()
        item = self.items.get(recognition_id)
        return item.response if item is not None and item.session_id == session_id else None

    def delete_session(self, session_id: str) -> None:
        for key in [key for key, item in self.items.items() if item.session_id == session_id]:
            self.items.pop(key, None)

    def _cleanup(self) -> None:
        now = datetime.now(timezone.utc)
        for key in [key for key, item in self.items.items() if item.expires_at <= now]:
            self.items.pop(key, None)


class RecognitionService:
    def __init__(
        self,
        settings: Settings,
        visual: VisualAnalyzer | None = None,
        verifier: CandidateVerifier | None = None,
    ):
        self.settings = settings
        self.visual = visual or OpenAICompatibleVisualAnalyzer(settings)
        self.verifier = verifier or MuseumRagVerifier(settings)
        self.store = RecognitionStore(settings.session_ttl_seconds)

    async def close(self) -> None:
        await self.visual.close()
        await self.verifier.close()

    def _validate(self, content_type: str, data: bytes) -> str:
        signature = SUPPORTED_IMAGES.get(content_type)
        if signature is None:
            raise RecognitionInputError("仅支持 JPEG、PNG 或 WebP 图片")
        if not data:
            raise RecognitionInputError("图片内容为空")
        if len(data) > self.settings.max_image_bytes:
            raise RecognitionInputError("图片不能超过 8MB")
        magic, suffix = signature
        if not data.startswith(magic) or (content_type == "image/webp" and data[8:12] != b"WEBP"):
            raise RecognitionInputError("图片格式与文件内容不一致")
        return suffix

    async def recognize(self, session_id: str, content_type: str, data: bytes) -> RecognitionResponse:
        suffix = self._validate(content_type, data)
        recognition_id = token_urlsafe(18)
        created_at = datetime.now(timezone.utc)
        temp_root = Path(self.settings.recognition_temp_dir) if self.settings.recognition_temp_dir else None
        if temp_root is not None:
            temp_root.mkdir(parents=True, exist_ok=True)
        temp_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(prefix="visitor-image-", suffix=suffix, dir=temp_root, delete=False) as handle:
                handle.write(data)
                temp_path = Path(handle.name)
            analysis = await self.visual.analyze(temp_path, content_type)
            candidates: list[RecognitionCandidate] = []
            verification_failures = 0
            for index, candidate in enumerate(analysis.candidates, start=1):
                try:
                    verified = await self.verifier.verify(candidate, "湖南省博物馆")
                except RecognitionUnavailable:
                    verified = VerificationResult(status="PARTIAL", sources=[])
                    verification_failures += 1
                candidates.append(RecognitionCandidate(
                    candidate_id=f"candidate-{index}",
                    name=candidate.name,
                    confidence=candidate.confidence,
                    visual_basis=candidate.visual_basis,
                    knowledge_status=verified.status,
                    sources=verified.sources,
                ))
            has_verified = any(item.knowledge_status == "VERIFIED" for item in candidates)
            if has_verified:
                status, message = "READY", "已找到馆方资料，请确认与你眼前的展品是否一致。"
            elif candidates and verification_failures == len(candidates):
                status, message = "ERROR", "识别服务暂时无法完成馆方核验，请稍后重试。"
            elif not candidates:
                status, message = "NOT_FOUND", "暂时没有识别出清晰候选，可以换一个角度重新拍摄。"
            else:
                status, message = "NOT_FOUND", "镜头提出了候选，但馆方资料暂未收录对应内容。"
            response = RecognitionResponse(
                recognition_id=recognition_id,
                status=status,
                observations=analysis.observations,
                candidates=candidates,
                message=message,
                created_at=created_at,
            )
        except RecognitionUnavailable:
            response = RecognitionResponse(
                recognition_id=recognition_id,
                status="ERROR",
                message="识别服务暂时不可用，请稍后重试。",
                created_at=created_at,
            )
        finally:
            if temp_path is not None:
                temp_path.unlink(missing_ok=True)
        self.store.put(session_id, response)
        return response

    def get(self, session_id: str, recognition_id: str) -> RecognitionResponse | None:
        return self.store.get(session_id, recognition_id)

    def confirm(self, session_id: str, recognition_id: str, candidate_id: str) -> RecognitionResponse | None:
        response = self.store.get(session_id, recognition_id)
        if response is None:
            return None
        if not any(item.candidate_id == candidate_id for item in response.candidates):
            raise RecognitionInputError("候选展品不存在")
        updated = response.model_copy(update={"status": "CONFIRMED", "confirmed_candidate_id": candidate_id})
        self.store.put(session_id, updated)
        return updated

    def delete_session(self, session_id: str) -> None:
        self.store.delete_session(session_id)
