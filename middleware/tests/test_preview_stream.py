"""Tests for the ranged preview relay (services/preview_stream.py)."""

import httpx
import pytest

from services.preview_stream import (
    StreamInfoCache,
    parse_content_range,
    parse_range,
    probe_upstream,
    relay_ranged,
    resolve_range,
)

FILE = bytes(range(256)) * 20_000  # 5 MB, non-trivial content


@pytest.fixture
def anyio_backend():
    return "asyncio"


class TestRangeParsing:
    def test_full_and_open_ended(self):
        assert parse_range(None) is None
        assert parse_range("") is None
        assert parse_range("bytes=0-") == (0, None)
        assert parse_range("bytes=100-199") == (100, 199)

    def test_rejects_what_we_dont_serve(self):
        assert parse_range("bytes=-500") is None       # suffix range
        assert parse_range("bytes=0-10,20-30") is None  # multi-range
        assert parse_range("bytes=50-10") is None       # inverted
        assert parse_range("items=0-1") is None

    def test_resolve_clamps_and_rejects(self):
        assert resolve_range(None, 1000) == (0, 999)
        assert resolve_range((10, None), 1000) == (10, 999)
        assert resolve_range((10, 5000), 1000) == (10, 999)
        assert resolve_range((1000, None), 1000) is None
        assert resolve_range(None, 0) is None

    def test_content_range_total(self):
        assert parse_content_range("bytes 0-0/12345") == 12345
        assert parse_content_range("bytes */12345") is None
        assert parse_content_range(None) is None


class TestStreamInfoCache:
    def test_expires(self):
        now = [100.0]
        cache = StreamInfoCache(ttl_s=60, clock=lambda: now[0])
        cache.put("vid", "http://u", {"h": "1"})
        assert cache.get("vid") == ("http://u", {"h": "1"})
        now[0] += 59
        assert cache.get("vid") is not None
        now[0] += 2
        assert cache.get("vid") is None

    def test_invalidate(self):
        cache = StreamInfoCache()
        cache.put("vid", "u", {})
        cache.invalidate("vid")
        assert cache.get("vid") is None


def _ranged_server(log: list[str]):
    def handler(request: httpx.Request) -> httpx.Response:
        rng = request.headers.get("range")
        log.append(rng)
        start, end = parse_range(rng)
        end = len(FILE) - 1 if end is None else min(end, len(FILE) - 1)
        return httpx.Response(
            206,
            headers={
                "Content-Range": f"bytes {start}-{end}/{len(FILE)}",
                "Content-Type": "audio/mp4",
            },
            content=FILE[start : end + 1],
        )
    return handler


def _unranged_server(log: list[str]):
    def handler(request: httpx.Request) -> httpx.Response:
        log.append(request.headers.get("range"))
        return httpx.Response(200, headers={"Content-Type": "audio/webm"}, content=FILE)
    return handler


async def _collect(gen) -> bytes:
    return b"".join([block async for block in gen])


@pytest.mark.anyio
async def test_probe_reports_total_and_type():
    async with httpx.AsyncClient(transport=httpx.MockTransport(_ranged_server([]))) as client:
        info = await probe_upstream(client, "http://cdn/audio", {})
    assert info.total == len(FILE)
    assert info.content_type == "audio/mp4"


@pytest.mark.anyio
async def test_probe_without_range_support():
    async with httpx.AsyncClient(transport=httpx.MockTransport(_unranged_server([]))) as client:
        info = await probe_upstream(client, "http://cdn/audio", {})
    assert info.total is None
    assert info.content_type == "audio/webm"


@pytest.mark.anyio
async def test_relay_fetches_in_ranged_chunks_and_reassembles():
    log: list[str] = []
    async with httpx.AsyncClient(transport=httpx.MockTransport(_ranged_server(log))) as client:
        out = await _collect(relay_ranged(client, "http://cdn/audio", {"X": "y"}, 0, len(FILE) - 1, chunk_size=2_000_000))
    assert out == FILE
    assert log == ["bytes=0-1999999", "bytes=2000000-3999999", "bytes=4000000-5119999"]


@pytest.mark.anyio
async def test_relay_serves_a_client_subrange():
    async with httpx.AsyncClient(transport=httpx.MockTransport(_ranged_server([]))) as client:
        out = await _collect(relay_ranged(client, "http://cdn/audio", {}, 1000, 4_500_000, chunk_size=1_000_000))
    assert out == FILE[1000 : 4_500_001]


@pytest.mark.anyio
async def test_relay_copes_with_upstream_ignoring_range():
    log: list[str] = []
    async with httpx.AsyncClient(transport=httpx.MockTransport(_unranged_server(log))) as client:
        out = await _collect(relay_ranged(client, "http://cdn/audio", {}, 70_000, 3_000_000))
    assert out == FILE[70_000 : 3_000_001]
    assert len(log) == 1
