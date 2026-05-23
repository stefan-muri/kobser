import os
import time
import uuid
from dataclasses import dataclass, field
from threading import Lock
from typing import Literal

import db as _db

JobStatus = Literal["pending", "downloading", "tagging", "scanning", "done", "error"]


@dataclass
class Job:
    job_id: str
    video_id: str
    artist: str
    title: str
    status: JobStatus = "pending"
    error: str | None = None
    file: str | None = None
    started_at: int = field(default_factory=lambda: int(time.time()))


_jobs: dict[str, Job] = {}
_active_video_ids: set[str] = set()
_lock = Lock()


def is_video_active(video_id: str) -> bool:
    with _lock:
        return video_id in _active_video_ids


def create_job(video_id: str, artist: str, title: str) -> Job:
    job = Job(job_id=str(uuid.uuid4()), video_id=video_id, artist=artist, title=title)
    with _lock:
        _jobs[job.job_id] = job
        _active_video_ids.add(video_id)
    _db.upsert_download(job.job_id, video_id, artist, title, "pending", started_at=job.started_at)
    return job


def get_job(job_id: str) -> Job | None:
    with _lock:
        return _jobs.get(job_id)


def update_job(job_id: str, **fields) -> None:
    with _lock:
        job = _jobs.get(job_id)
        if job is None:
            return
        for k, v in fields.items():
            setattr(job, k, v)
        if job.status in ("done", "error"):
            _active_video_ids.discard(job.video_id)
        snap = (job.job_id, job.video_id, job.artist, job.title,
                job.status, job.error, job.file, job.started_at)

    jid, vid, art, tit, st, err, fpath, sat = snap
    completed_at = int(time.time()) if st in ("done", "error") else None
    file_size = None
    if st == "done" and fpath:
        try:
            file_size = os.path.getsize(fpath)
        except OSError:
            pass
    _db.upsert_download(
        jid, vid, art, tit, st,
        error=err, file_path=fpath, file_size_bytes=file_size,
        started_at=sat, completed_at=completed_at,
    )
