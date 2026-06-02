import asyncio
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
        raise HTTPException(status_code=502, detail=str(exc))

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
            params={"id": track_id, **auth_params(sess["username"], sess["password"])},
        )
        r.raise_for_status()
        body = r.json().get("subsonic-response", {})

    if body.get("status") != "ok" or not body.get("song"):
        raise HTTPException(status_code=404, detail="track not found")

    rel_path = body["song"].get("path")
    if not rel_path:
        raise HTTPException(status_code=404, detail="track has no path")

    music_root = Path(MUSIC_DIR).resolve()
    p = Path(rel_path)
    file_path = (p if p.is_absolute() else music_root / rel_path).resolve()

    # Ensure the resolved path stays inside the music directory.
    try:
        file_path.relative_to(music_root)
    except ValueError:
        raise HTTPException(status_code=400, detail="invalid path")

    if not file_path.exists():
        # Navidrome's stored path doesn't match what's on disk.
        # Try to find the file by reconstructing the Kobser download path (artist/artist - title.*).
        song = body["song"]
        s_artist = _sanitize(song.get("artist", ""))
        s_title = _sanitize(song.get("title", ""))
        found = None
        if s_artist and s_title:
            stem = f"{s_artist} - {s_title}"
            for ext in (".m4a", ".opus", ".ogg", ".mp3", ".flac", ".webm"):
                candidate = (music_root / s_artist / f"{stem}{ext}").resolve()
                try:
                    candidate.relative_to(music_root)
                except ValueError:
                    continue
                if candidate.exists():
                    found = candidate
                    break
        if not found:
            await trigger_scan_and_wait(sess["username"], sess["password"])
            raise HTTPException(status_code=404, detail=f"file not found: {file_path}")
        file_path = found

    file_path.unlink()

    # Remove now-empty artist/album directories.
    for parent in [file_path.parent, file_path.parent.parent]:
        if parent != music_root and parent.exists() and not any(parent.iterdir()):
            parent.rmdir()
        else:
            break

    await trigger_scan_and_wait(sess["username"], sess["password"])
    return {"ok": True}
