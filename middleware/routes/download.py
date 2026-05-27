import logging

from anyio import to_thread
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel, Field

import db as _db
from auth import get_current_session
from jobs import cancel_job, create_job, get_job, is_cancelled, is_video_active, update_job
from services import navidrome_client, tagger_service, ytdlp_service
from services.ytdlp_service import DownloadCancelled

router = APIRouter()
log = logging.getLogger(__name__)


class DownloadRequest(BaseModel):
    videoId: str = Field(min_length=1)
    artist: str = Field(min_length=1)
    title: str = Field(min_length=1)
    source: str = Field(default="youtube_music", pattern="^(youtube|youtube_music)$")
    album: str = Field(default="")


@router.post("/api/download")
async def download(
    body: DownloadRequest,
    bg: BackgroundTasks,
    sess: dict = Depends(get_current_session),
):
    if is_video_active(body.videoId):
        raise HTTPException(
            status_code=409, detail="download already in progress for this video"
        )
    job = create_job(body.videoId, body.artist, body.title)
    bg.add_task(
        _run_download,
        job.job_id,
        body.videoId,
        body.artist,
        body.title,
        sess["username"],
        sess["password"],
        body.source,
        body.album,
    )
    return {"jobId": job.job_id, "status": job.status}


async def _run_download(
    job_id: str,
    video_id: str,
    artist: str,
    title: str,
    username: str,
    password: str,
    source: str = "youtube_music",
    album: str = "",
) -> None:
    try:
        # Resolve the user's library path from Navidrome; fall back to MUSIC_DIR.
        libs = await navidrome_client.get_user_libraries(username, password)
        music_dir = libs[0]["path"] if libs else None
        if music_dir:
            log.info("download %s: routing to user '%s' library %s", job_id, username, music_dir)

        update_job(job_id, status="downloading")
        file_path = await to_thread.run_sync(
            lambda: ytdlp_service.download(
                video_id, artist, title, source,
                cancel_check=lambda: is_cancelled(job_id),
                music_dir=music_dir,
            )
        )

        if is_cancelled(job_id):
            return

        final_album = album.strip() or "Singles"
        update_job(job_id, status="tagging", file=file_path)
        await to_thread.run_sync(tagger_service.tag, file_path, artist, title, final_album)

        if is_cancelled(job_id):
            return

        update_job(job_id, status="scanning")
        await navidrome_client.trigger_scan_and_wait(username, password)

        update_job(job_id, status="done")
    except DownloadCancelled:
        pass  # cancel_job already set status; nothing more to do
    except Exception as e:
        log.exception("download job %s failed", job_id)
        update_job(job_id, status="error", error=str(e))


@router.get("/api/status/{job_id}", dependencies=[Depends(get_current_session)])
async def status(job_id: str):
    job = get_job(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="job not found")
    return {
        "jobId": job.job_id,
        "status": job.status,
        "error": job.error,
        "file": job.file,
    }


@router.post("/api/rescan")
async def rescan(sess: dict = Depends(get_current_session)):
    await navidrome_client.trigger_scan_and_wait(sess["username"], sess["password"])
    return {"ok": True}


@router.post("/api/jobs/{job_id}/cancel", dependencies=[Depends(get_current_session)])
def cancel_job_endpoint(job_id: str):
    if not cancel_job(job_id):
        raise HTTPException(status_code=404, detail="job not found or already finished")
    return {"ok": True}


@router.get("/api/downloads", dependencies=[Depends(get_current_session)])
def list_downloads():
    return {"downloads": _db.list_downloads()}


@router.delete("/api/downloads/{job_id}", dependencies=[Depends(get_current_session)])
def delete_download(job_id: str):
    _db.delete_download_record(job_id)
    return {"ok": True}
