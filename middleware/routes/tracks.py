from pathlib import Path

import httpx
from fastapi import APIRouter, Depends, HTTPException

from auth import get_current_session
from config import MUSIC_DIR, NAVIDROME_URL
from services.navidrome_client import auth_params, trigger_scan_and_wait

router = APIRouter()


@router.get("/api/track/{track_id}/debug")
async def debug_track(track_id: str, sess: dict = Depends(get_current_session)):
    """Return path info for a track without deleting — remove once the delete bug is fixed."""
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.get(
            f"{NAVIDROME_URL}/rest/getSong",
            params={"id": track_id, **auth_params(sess["username"], sess["password"])},
        )
        r.raise_for_status()
        body = r.json().get("subsonic-response", {})

    song = body.get("song", {})
    rel_path = song.get("path", "")
    music_root = Path(MUSIC_DIR).resolve()
    p = Path(rel_path) if rel_path else None
    file_path = (p if (p and p.is_absolute()) else music_root / rel_path) if rel_path else None
    return {
        "MUSIC_DIR_config": MUSIC_DIR,
        "music_root_resolved": str(music_root),
        "navidrome_path": rel_path,
        "is_absolute": p.is_absolute() if p else None,
        "resolved_file_path": str(file_path.resolve()) if file_path else None,
        "file_exists": file_path.resolve().exists() if file_path else None,
    }


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
        # File is missing from the middleware's volume — could be a mount mismatch
        # or a stale Navidrome entry. Trigger a rescan so Navidrome cleans it up.
        await trigger_scan_and_wait(sess["username"], sess["password"])
        raise HTTPException(
            status_code=404,
            detail=f"file not visible to middleware at {file_path} (navidrome path: {rel_path!r}). "
                   f"Check that MUSIC_DIR in .env points to the same directory Navidrome uses. "
                   f"A rescan was triggered to remove stale entries.",
        )

    file_path.unlink()

    # Remove now-empty artist/album directories.
    for parent in [file_path.parent, file_path.parent.parent]:
        if parent != music_root and parent.exists() and not any(parent.iterdir()):
            parent.rmdir()
        else:
            break

    await trigger_scan_and_wait(sess["username"], sess["password"])
    return {"ok": True}
