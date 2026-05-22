import logging

from anyio import to_thread
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel, Field

from auth import get_current_session
from jobs import create_job, get_job, is_video_active, update_job
from services import navidrome_client, tagger_service, ytdlp_service

router = APIRouter()
log = logging.getLogger(__name__)


class DownloadRequest(BaseModel):
    videoId: str = Field(min_length=1)
    artist: str = Field(min_length=1)
    title: str = Field(min_length=1)


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
    )
    return {"jobId": job.job_id, "status": job.status}


async def _run_download(
    job_id: str,
    video_id: str,
    artist: str,
    title: str,
    username: str,
    password: str,
) -> None:
    try:
        update_job(job_id, status="downloading")
        file_path = await to_thread.run_sync(
            ytdlp_service.download, video_id, artist, title
        )

        update_job(job_id, status="tagging", file=file_path)
        await to_thread.run_sync(tagger_service.tag, file_path, artist, title)

        update_job(job_id, status="scanning")
        await navidrome_client.trigger_scan_and_wait(username, password)

        update_job(job_id, status="done")
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
