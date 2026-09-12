"""Relay a YouTube audio URL to a client in ranged chunks.

YouTube's CDN throttles a single un-ranged request for a whole media file to
around real-time or below (measured ~30 KB/s), which stalls playback, while
ranged requests of a few MB are served at full speed -- yt-dlp itself downloads
in ranged chunks for this reason. So the relay always fetches upstream with
`Range` headers and stitches the chunks together, and exposes byte-range support
to the client so players can seek.
"""

import re
import time
from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass

import httpx

CHUNK_SIZE = 2 * 1024 * 1024
READ_SIZE = 64 * 1024

_RANGE_RE = re.compile(r"^bytes=(\d+)-(\d*)$")
_CONTENT_RANGE_RE = re.compile(r"^bytes \d+-\d+/(\d+)$")


def parse_range(header: str | None) -> tuple[int, int | None] | None:
    """Parse a client `Range` header into `(start, end_inclusive_or_None)`.

    Anything we don't handle (missing, malformed, suffix ranges, multiple
    ranges) yields None, meaning "serve the whole file".
    """
    if not header:
        return None
    m = _RANGE_RE.match(header.strip())
    if not m:
        return None
    start = int(m.group(1))
    end = int(m.group(2)) if m.group(2) else None
    if end is not None and end < start:
        return None
    return start, end


def resolve_range(wanted: tuple[int, int | None] | None, total: int) -> tuple[int, int] | None:
    """Clamp a parsed range to a file of `total` bytes. None means unsatisfiable (416)."""
    if wanted is None:
        return (0, total - 1) if total > 0 else None
    start, end = wanted
    if start >= total:
        return None
    end = total - 1 if end is None else min(end, total - 1)
    return start, end


def parse_content_range(header: str | None) -> int | None:
    """Total size from a `Content-Range: bytes a-b/total` header."""
    m = _CONTENT_RANGE_RE.match(header or "")
    return int(m.group(1)) if m else None


@dataclass
class StreamInfoCache:
    """Remembers resolved stream URLs briefly so seeks and replays don't re-run yt-dlp.

    Google's media URLs stay valid for hours; the TTL is kept well under that.
    """

    ttl_s: float = 30 * 60
    clock: Callable[[], float] = time.monotonic

    def __post_init__(self) -> None:
        self._entries: dict[str, tuple[float, str, dict]] = {}

    def get(self, video_id: str) -> tuple[str, dict] | None:
        entry = self._entries.get(video_id)
        if entry is None:
            return None
        expires, url, headers = entry
        if expires <= self.clock():
            self._entries.pop(video_id, None)
            return None
        return url, headers

    def put(self, video_id: str, url: str, headers: dict) -> None:
        self._entries[video_id] = (self.clock() + self.ttl_s, url, headers)

    def invalidate(self, video_id: str) -> None:
        self._entries.pop(video_id, None)


@dataclass(frozen=True)
class UpstreamInfo:
    total: int | None       # None when the upstream ignores Range requests
    content_type: str


async def probe_upstream(client: httpx.AsyncClient, url: str, headers: dict) -> UpstreamInfo:
    """One-byte ranged GET to learn the file size, content type and Range support."""
    async with client.stream("GET", url, headers={**headers, "Range": "bytes=0-0"}) as r:
        r.raise_for_status()
        content_type = r.headers.get("content-type", "application/octet-stream")
        total = parse_content_range(r.headers.get("content-range")) if r.status_code == 206 else None
        return UpstreamInfo(total=total, content_type=content_type)


async def relay_ranged(
    client: httpx.AsyncClient,
    url: str,
    headers: dict,
    start: int,
    end: int,
    chunk_size: int = CHUNK_SIZE,
) -> AsyncIterator[bytes]:
    """Yield bytes `start..end` (inclusive) of `url`, fetching in ranged chunks."""
    pos = start
    while pos <= end:
        stop = min(pos + chunk_size - 1, end)
        req_headers = {**headers, "Range": f"bytes={pos}-{stop}"}
        async with client.stream("GET", url, headers=req_headers) as r:
            r.raise_for_status()
            if r.status_code == 206:
                async for block in r.aiter_bytes(READ_SIZE):
                    yield block
                pos = stop + 1
            else:
                # Upstream ignored the Range: it's sending the whole file from byte 0.
                # Skip to where we are, relay to the end, and stop.
                skip = pos
                remaining = end - pos + 1
                async for block in r.aiter_bytes(READ_SIZE):
                    if skip:
                        if len(block) <= skip:
                            skip -= len(block)
                            continue
                        block = block[skip:]
                        skip = 0
                    if len(block) > remaining:
                        block = block[:remaining]
                    remaining -= len(block)
                    if block:
                        yield block
                    if remaining <= 0:
                        break
                return


async def relay_plain(client: httpx.AsyncClient, url: str, headers: dict) -> AsyncIterator[bytes]:
    """Fallback for upstreams without Range support: a single streaming GET."""
    async with client.stream("GET", url, headers=headers) as r:
        r.raise_for_status()
        async for block in r.aiter_bytes(READ_SIZE):
            yield block
