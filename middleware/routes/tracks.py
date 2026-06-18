import asyncio
import logging
import re
from pathlib import Path

import httpx
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse

from auth import get_current_session
from config import MUSIC_DIR, NAVIDROME_URL
from services.navidrome_client import auth_params, trigger_scan_and_wait
from services.ytdlp_service import _sanitize, get_stream_info

router = APIRouter()
log = logging.getLogger(__name__)

_YT_VIDEO_ID_RE = re.compile(r'^[A-Za-z0-9_-]{11}$')


@router.get("/api/preview/{video_id}")
async def preview_track(video_id: str, sess: dict = Depends(get_current_session)):
    """Proxy-stream the best audio for a YouTube video without downloading it."""
    if not _YT_VIDEO_ID_RE.match(video_id):
        raise HTTPException(status_code=400, detail="invalid video id")
    try:
        url, headers = await asyncio.get_event_loop().run_in_executor(
            None, get_stream_info, video_id
        )
    except Exception as exc:
        # Log the detail server-side; don't leak yt-dlp internals (URLs/paths)
        # to the client.
        log.warning("preview failed for %s: %s", video_id, exc)
        raise HTTPException(status_code=502, detail="couldn't fetch preview") from exc

    async def stream():
        async with httpx.AsyncClient(timeout=None, follow_redirects=True) as client:
            async with client.stream("GET", url, headers=headers) as r:
                async for chunk in r.aiter_bytes(chunk_size=65536):
                    yield chunk

    return StreamingResponse(stream(), media_type="audio/mp4")


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
