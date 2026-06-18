import asyncio
import logging
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from threading import Lock

from anyio import to_thread
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel, Field
from yt_dlp.utils import DownloadError

from auth import get_current_session
from services import navidrome_client, spotify_client, tagger_service, ytdlp_service
from services.spotify_client import SpotifyError

router = APIRouter()
log = logging.getLogger(__name__)

# How many tracks to download at once during an import. Kept modest: too high
# trips YouTube rate-limiting (403s) and spikes CPU.
IMPORT_CONCURRENCY = 2
# Transient failures (403/timeout/signature blips) often succeed on a second try.
IMPORT_DOWNLOAD_RETRIES = 1
IMPORT_RETRY_BACKOFF_S = 3.0


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
        raise HTTPException(status_code=400, detail=str(e)) from e

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


async def _download_with_retry(video_id: str, artist: str, title: str, music_dir: str | None) -> str:
    """Download a track, retrying once (with backoff) on transient failures."""
    for attempt in range(IMPORT_DOWNLOAD_RETRIES + 1):
        try:
            return await ytdlp_service.download_limited(
                video_id, artist, title, "youtube_music", music_dir=music_dir
            )
        except DownloadError as e:
            if attempt >= IMPORT_DOWNLOAD_RETRIES or not ytdlp_service.is_retryable(e):
                raise
            log.info(
                "import: retry %d/%d for %s - %s (%s)",
                attempt + 1, IMPORT_DOWNLOAD_RETRIES, artist, title, ytdlp_service.describe_error(e),
            )
            await asyncio.sleep(IMPORT_RETRY_BACKOFF_S)
    raise RuntimeError("unreachable")  # loop either returns or raises


async def _run_import(import_id: str, tracks: list[dict], username: str, password: str) -> None:
    job = _imports[import_id]
    music_dir = await _resolve_music_dir(username, password)
    sem = asyncio.Semaphore(IMPORT_CONCURRENCY)
    # Per-track outcome, kept index-aligned so the playlist preserves Spotify order.
    # ("existing", song_id) | ("downloaded", artist, title) | ("failed", label) | None
    outcomes: list[tuple | None] = [None] * len(tracks)

    def fail(i: int, label: str, reason: str) -> None:
        outcomes[i] = ("failed", f"{label} — {reason}")
        job.failed += 1
        job.failures.append(f"{label} — {reason}")

    async def handle(i: int, track: dict) -> None:
        artist, title, album = track["artist"], track["title"], track.get("album", "")
        label = f"{artist} - {title}"
        async with sem:
            try:
                # Already in library? Skip download, reuse it for the playlist.
                existing_id = await navidrome_client.find_song_id(artist, title, username, password)
                if existing_id:
                    outcomes[i] = ("existing", existing_id)
                    job.existing += 1
                    return

                results = await to_thread.run_sync(
                    lambda: ytdlp_service.search(f"{artist} {title}", limit=1, source="youtube_music")
                )
                if not results:
                    log.info("import %s: no YouTube match for %s", import_id, label)
                    fail(i, label, "no match on YouTube")
                    return

                top = results[0]
                file_path = await _download_with_retry(top["videoId"], artist, title, music_dir)
                await to_thread.run_sync(tagger_service.tag, file_path, artist, title, album or "Singles")
                outcomes[i] = ("downloaded", artist, title)
                job.downloaded += 1
            except DownloadError as e:
                reason = ytdlp_service.describe_error(e)
                log.warning("import %s: skipped %s — %s", import_id, label, reason)
                fail(i, label, reason)
            except Exception as e:
                # Unexpected (our bug, Navidrome, network) — keep the traceback.
                log.exception("import %s: unexpected error on %s", import_id, label)
                fail(i, label, ytdlp_service.describe_error(e))
            finally:
                job.current += 1

    try:
        await asyncio.gather(*(handle(i, t) for i, t in enumerate(tracks)))

        # Make freshly downloaded files visible before resolving their ids.
        if any(o and o[0] == "downloaded" for o in outcomes):
            await navidrome_client.trigger_scan_and_wait(username, password)

        # Build the playlist in original track order.
        song_ids: list[str] = []
        for o in outcomes:
            if o is None:
                continue
            if o[0] == "existing":
                song_ids.append(o[1])
            elif o[0] == "downloaded":
                sid = await navidrome_client.find_song_id(o[1], o[2], username, password)
                if sid:
                    song_ids.append(sid)

        if song_ids:
            job.playlist_id = await navidrome_client.create_playlist(job.name, song_ids, username, password)

        job.status = "done"
    except Exception as e:
        log.exception("import %s failed", import_id)
        job.status = "error"
        job.error = str(e)
