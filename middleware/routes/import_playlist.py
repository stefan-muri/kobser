import logging
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from threading import Lock

from anyio import to_thread
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel, Field

from auth import get_current_session
from config import MUSIC_DIR
from services import navidrome_client, spotify_client, tagger_service, ytdlp_service
from services.spotify_client import SpotifyError

router = APIRouter()
log = logging.getLogger(__name__)


@dataclass
class ImportJob:
    import_id: str
    name: str
    total: int
    status: str = "running"  # running | done | error
    current: int = 0
    downloaded: int = 0
    existing: int = 0
    failed: int = 0
    playlist_id: str | None = None
    error: str | None = None
    failures: list[str] = field(default_factory=list)  # "Artist - Title" of misses


_imports: dict[str, ImportJob] = {}
_lock = Lock()


class ImportRequest(BaseModel):
    url: str = Field(min_length=1)


@router.post("/api/import/spotify")
async def import_spotify(
    body: ImportRequest,
    bg: BackgroundTasks,
    sess: dict = Depends(get_current_session),
):
    try:
        playlist = await spotify_client.fetch_playlist(body.url)
    except SpotifyError as e:
        log.warning("spotify import failed for %r: %s", body.url, e)
        raise HTTPException(status_code=400, detail=str(e))

    job = ImportJob(
        import_id=str(uuid.uuid4()),
        name=playlist["name"],
        total=len(playlist["tracks"]),
    )
    with _lock:
        _imports[job.import_id] = job

    bg.add_task(_run_import, job.import_id, playlist["tracks"], sess["username"], sess["password"])
    return {"importId": job.import_id, "name": job.name, "total": job.total}


@router.get("/api/import/{import_id}", dependencies=[Depends(get_current_session)])
def import_status(import_id: str):
    job = _imports.get(import_id)
    if job is None:
        raise HTTPException(status_code=404, detail="import not found")
    return {
        "importId": job.import_id,
        "name": job.name,
        "status": job.status,
        "total": job.total,
        "current": job.current,
        "downloaded": job.downloaded,
        "existing": job.existing,
        "failed": job.failed,
        "playlistId": job.playlist_id,
        "error": job.error,
        "failures": job.failures,
    }


async def _resolve_music_dir(username: str, password: str) -> str | None:
    """User's library path, falling back to MUSIC_DIR when the reported path
    isn't present on this host (local dev / outside Navidrome's container)."""
    libs = await navidrome_client.get_user_libraries(username, password)
    music_dir = libs[0]["path"] if libs else None
    if music_dir and not Path(music_dir).exists():
        music_dir = None
    return music_dir


async def _run_import(import_id: str, tracks: list[dict], username: str, password: str) -> None:
    job = _imports[import_id]
    music_dir = await _resolve_music_dir(username, password)
    song_ids: list[str] = []
    downloaded_tracks: list[dict] = []

    try:
        for track in tracks:
            job.current += 1
            artist, title, album = track["artist"], track["title"], track.get("album", "")

            # Already in library? Skip download, reuse it for the playlist.
            existing_id = await navidrome_client.find_song_id(artist, title, username, password)
            if existing_id:
                song_ids.append(existing_id)
                job.existing += 1
                continue

            results = await to_thread.run_sync(
                lambda: ytdlp_service.search(f"{artist} {title}", limit=1, source="youtube_music")
            )
            if not results:
                job.failed += 1
                job.failures.append(f"{artist} - {title}")
                continue

            top = results[0]
            try:
                file_path = await to_thread.run_sync(
                    lambda: ytdlp_service.download(top["videoId"], artist, title, "youtube_music", music_dir=music_dir)
                )
                await to_thread.run_sync(tagger_service.tag, file_path, artist, title, album or "Singles")
                job.downloaded += 1
                downloaded_tracks.append({"artist": artist, "title": title})
            except Exception:
                log.exception("import %s: download failed for %s - %s", import_id, artist, title)
                job.failed += 1
                job.failures.append(f"{artist} - {title}")

        # Make freshly downloaded files visible, then resolve their ids.
        if downloaded_tracks:
            await navidrome_client.trigger_scan_and_wait(username, password)
            for t in downloaded_tracks:
                sid = await navidrome_client.find_song_id(t["artist"], t["title"], username, password)
                if sid:
                    song_ids.append(sid)

        if song_ids:
            job.playlist_id = await navidrome_client.create_playlist(job.name, song_ids, username, password)

        job.status = "done"
    except Exception as e:
        log.exception("import %s failed", import_id)
        job.status = "error"
        job.error = str(e)
