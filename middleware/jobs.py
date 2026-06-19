import os
import time
import uuid
from dataclasses import dataclass, field
from threading import Lock
from typing import Literal

import db as _db

JobStatus = Literal["pending", "downloading", "tagging", "scanning", "done", "error", "cancelled"]


@dataclass
class Job:
    job_id: str
    video_id: str
    artist: str
    title: str
    username: str = ""
    album: str = ""
    source: str = "youtube_music"
    status: JobStatus = "pending"
    error: str | None = None
    file: str | None = None
    started_at: int = field(default_factory=lambda: int(time.time()))


_jobs: dict[str, Job] = {}
_active_video_ids: set[str] = set()
_cancelled: set[str] = set()
_lock = Lock()

# Cap on finished jobs retained in memory. The DB holds the full history and the
# status endpoint falls back to it, so evicting old terminal jobs is safe and
# just keeps _jobs/_cancelled from growing without bound over a long uptime.
_MAX_TERMINAL_JOBS = 200
_TERMINAL = ("done", "error", "cancelled")


def _prune_locked() -> None:
    """Drop the oldest terminal jobs beyond the cap. Caller must hold _lock."""
    terminal = [j for j in _jobs.values() if j.status in _TERMINAL]
    excess = len(terminal) - _MAX_TERMINAL_JOBS
    if excess <= 0:
        return
    terminal.sort(key=lambda j: j.started_at)
    for j in terminal[:excess]:
        _jobs.pop(j.job_id, None)
        _cancelled.discard(j.job_id)


def is_video_active(video_id: str) -> bool:
    with _lock:
        return video_id in _active_video_ids


def create_job(
    video_id: str, artist: str, title: str, username: str,
    album: str = "", source: str = "youtube_music",
) -> Job:
    job = Job(
        job_id=str(uuid.uuid4()), video_id=video_id, artist=artist, title=title,
        username=username, album=album, source=source,
    )
    with _lock:
        _jobs[job.job_id] = job
        _active_video_ids.add(video_id)
        _prune_locked()
    _db.upsert_download(
        job.job_id, video_id, artist, title, "pending",
        username=username, album=album, source=source, started_at=job.started_at,
    )
    return job


def get_job(job_id: str) -> Job | None:
    with _lock:
        return _jobs.get(job_id)


def is_cancelled(job_id: str) -> bool:
    return job_id in _cancelled


def cancel_job(job_id: str) -> bool:
    with _lock:
        job = _jobs.get(job_id)
        if job is None or job.status in ("done", "error", "cancelled"):
            return False
        _cancelled.add(job_id)
        job.status = "cancelled"
        _active_video_ids.discard(job.video_id)
        snap = (job.job_id, job.video_id, job.artist, job.title, job.username, job.started_at)
    jid, vid, art, tit, usr, sat = snap
    _db.upsert_download(jid, vid, art, tit, "cancelled",
                        username=usr, error="Cancelled by user", started_at=sat,
                        completed_at=int(time.time()))
    return True


def update_job(job_id: str, **fields) -> None:
    with _lock:
        job = _jobs.get(job_id)
        if job is None:
            return
        # Don't override a user cancellation with a late status update
        if job.status == "cancelled":
            return
        for k, v in fields.items():
            setattr(job, k, v)
        if job.status in ("done", "error", "cancelled"):
            _active_video_ids.discard(job.video_id)
        snap = (job.job_id, job.video_id, job.artist, job.title, job.username,
                job.status, job.error, job.file, job.started_at)

    jid, vid, art, tit, usr, st, err, fpath, sat = snap
    completed_at = int(time.time()) if st in ("done", "error") else None
    file_size = None
    if st == "done" and fpath:
        try:
            file_size = os.path.getsize(fpath)
        except OSError:
            pass
    _db.upsert_download(
        jid, vid, art, tit, st,
        username=usr, error=err, file_path=fpath, file_size_bytes=file_size,
        started_at=sat, completed_at=completed_at,
    )
