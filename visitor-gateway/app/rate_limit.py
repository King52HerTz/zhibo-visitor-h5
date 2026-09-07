from __future__ import annotations

import time
from collections import defaultdict, deque


class SlidingWindowRateLimiter:
    def __init__(self, max_requests: int, window_seconds: int = 60):
        self.max_requests = max_requests
        self.window_seconds = window_seconds
        self._requests: dict[str, deque[float]] = defaultdict(deque)

    def allow(self, key: str, now: float | None = None) -> tuple[bool, int]:
        current = time.monotonic() if now is None else now
        requests = self._requests[key]
        cutoff = current - self.window_seconds
        while requests and requests[0] <= cutoff:
            requests.popleft()
        if len(requests) >= self.max_requests:
            retry_after = max(1, int(requests[0] + self.window_seconds - current + 0.999))
            return False, retry_after
        requests.append(current)
        return True, 0
