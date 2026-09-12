import asyncio
import logging
import re
import time
from pathlib import Path

import httpx
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import Response, StreamingResponse

from auth import get_current_session
from config import MUSIC_DIR, NAVIDROME_URL
from services.navidrome_client import auth_params, trigger_scan_and_wait
from services.preview_stream import (
    StreamInfoCache,
    parse_range,
    probe_upstream,
    relay_plain,
    relay_ranged,
    resolve_range,
)
from services.ytdlp_service import _sanitize, get_stream_info

router = APIRouter()
log = logging.getLogger(__name__)

_YT_VIDEO_ID_RE = re.compile(r'^[A-Za-z0-9_-]{11}$')


_stream_info_cache = StreamInfoCache()


async def _resolve_stream(video_id: str) -> tuple[str, dict]:
    cached = _stream_info_cache.get(video_id)
    if cached is not None:
        return cached
    t0 = time.monotonic()
    try:
        url, headers = await asyncio.get_event_loop().run_in_executor(
            None, get_stream_info, video_id
        )
    except Exception as exc:
        # Log the detail server-side; don't leak yt-dlp internals (URLs/paths)
        # to the client.
        log.warning("preview %s: yt-dlp failed after %.1fs: %s", video_id, time.monotonic() - t0, exc)
        raise HTTPException(status_code=502, detail="couldn't fetch preview") from exc
    log.info("preview %s: resolved stream in %.1fs", video_id, time.monotonic() - t0)
    _stream_info_cache.put(video_id, url, headers)
    return url, headers


async def _closing(gen, client: httpx.AsyncClient):
    try:
        async for block in gen:
            yield block
    finally:
        await client.aclose()


@router.get("/api/preview/{video_id}")
async def preview_track(
    video_id: str, request: Request, sess: dict = Depends(get_current_session)
):
    """Proxy-stream the best audio for a YouTube video without downloading it.

    Fetched upstream in ranged chunks (see services.preview_stream) and served
    with byte-range support so players can seek.
    """
    if not _YT_VIDEO_ID_RE.match(video_id):
        raise HTTPException(status_code=400, detail="invalid video id")
    url, headers = await _resolve_stream(video_id)

    client = httpx.AsyncClient(timeout=httpx.Timeout(30.0, read=60.0), follow_redirects=True)
    try:
        upstream = await probe_upstream(client, url, headers)
    except httpx.HTTPStatusError as exc:
        # A cached URL can go stale (403); forget it so the next attempt re-resolves.
        _stream_info_cache.invalidate(video_id)
        await client.aclose()
        log.warning("preview %s: upstream returned %s", video_id, exc.response.status_code)
        raise HTTPException(status_code=502, detail="couldn't fetch preview") from exc
    except Exception as exc:
        await client.aclose()
        log.warning("preview %s: upstream probe failed: %s", video_id, exc)
        raise HTTPException(status_code=502, detail="couldn't fetch preview") from exc

    if upstream.total is None:
        return StreamingResponse(
            _closing(relay_plain(client, url, headers), client),
            media_type=upstream.content_type,
        )

    wanted = parse_range(request.headers.get("range"))
    resolved = resolve_range(wanted, upstream.total)
    if resolved is None:
        await client.aclose()
        return Response(
            status_code=416, headers={"Content-Range": f"bytes */{upstream.total}"}
        )
    start, end = resolved
    response_headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1),
    }
    status = 200
    if wanted is not None:
        status = 206
        response_headers["Content-Range"] = f"bytes {start}-{end}/{upstream.total}"
    return StreamingResponse(
        _closing(relay_ranged(client, url, headers, start, end), client),
        status_code=status,
        media_type=upstream.content_type,
        headers=response_headers,
    )


@router.delete("/api/track/{track_id}")
async def delete_track(track_id: str, sess: dict = Depends(get_current_session)):
    """Delete a track from disk and trigger a Navidrome rescan."""
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.get(
            f"{NAVIDROME_URL}/rest/getSong",
            params={"id": track_id, **auth_params(sess["username"], sess["salt"], sess["token"])},
        )
        r.raise_for_status()
        body = r.json().get("subsonic-response", {})

    if body.get("status") != "ok" or not body.get("song"):
        raise HTTPException(status_code=404, detail="track not found")

    song = body["song"]

    # Build search roots: global MUSIC_DIR first, then the user's assigned library
    # (which may differ, e.g. /music/music_stefan). The user library takes priority
    # because kobser routes downloads there.
    global_root = Path(MUSIC_DIR).resolve()
    search_roots: list[Path] = [global_root]
    lib_path_str = sess.get("library_path")
    if lib_path_str:
        try:
            lib_path = Path(lib_path_str).resolve()
            if lib_path != global_root:
                search_roots.insert(0, lib_path)
        except Exception:
            pass

    # ── 1. Try the path Navidrome reports (may be a virtual tag-based path) ──
    file_path: Path | None = None
    rel_path = song.get("path")
    if rel_path:
        p = Path(rel_path)
        for root in search_roots:
            candidate = (p if p.is_absolute() else root / rel_path).resolve()
            try:
                candidate.relative_to(root)
            except ValueError:
                continue
            if candidate.exists():
                file_path = candidate
                break

    # ── 2. Fall back to kobser naming: root/artist/artist - title.ext ────────
    if file_path is None:
        s_artist = _sanitize(song.get("artist", "") or song.get("albumArtist", ""))
        s_title = _sanitize(song.get("title", ""))
        if s_artist and s_title:
            stem = f"{s_artist} - {s_title}"
            for root in search_roots:
                for ext in (".m4a", ".opus", ".ogg", ".mp3", ".flac", ".webm"):
                    candidate = (root / s_artist / f"{stem}{ext}").resolve()
                    try:
                        candidate.relative_to(root)
                    except ValueError:
                        continue
                    if candidate.exists():
                        file_path = candidate
                        break
                if file_path:
                    break

    if file_path is None:
        # File is already gone from disk — Navidrome has a stale entry.
        # Rescan so Navidrome removes it from its DB, then report success.
        await trigger_scan_and_wait(sess["username"], sess["salt"], sess["token"])
        return {"ok": True}

    file_path.unlink()

    # Remove now-empty artist/album directories (never remove a library root).
    protected = set(search_roots)
    for parent in [file_path.parent, file_path.parent.parent]:
        if parent in protected or not parent.exists() or any(parent.iterdir()):
            break
        parent.rmdir()

    await trigger_scan_and_wait(sess["username"], sess["salt"], sess["token"])
    return {"ok": True}
