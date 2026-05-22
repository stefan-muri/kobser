from pathlib import Path

import httpx
from fastapi import APIRouter, Depends, HTTPException

from auth import get_current_session
from config import MUSIC_DIR, NAVIDROME_URL
from services.navidrome_client import auth_params, trigger_scan_and_wait

router = APIRouter()


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
    file_path = (music_root / rel_path).resolve()

    # Ensure the resolved path stays inside the music directory.
    try:
        file_path.relative_to(music_root)
    except ValueError:
        raise HTTPException(status_code=400, detail="invalid path")

    if not file_path.exists():
        raise HTTPException(status_code=404, detail="file not found on disk")

    file_path.unlink()

    # Remove now-empty artist directory.
    parent = file_path.parent
    if parent != music_root and parent.exists() and not any(parent.iterdir()):
        parent.rmdir()

    await trigger_scan_and_wait(sess["username"], sess["password"])
    return {"ok": True}
