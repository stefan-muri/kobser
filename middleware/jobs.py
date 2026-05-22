import uuid
from dataclasses import dataclass
from threading import Lock
from typing import Literal

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


_jobs: dict[str, Job] = {}
_active_video_ids: set[str] = set()
_lock = Lock()


def is_video_active(video_id: str) -> bool:
    with _lock:
        return video_id in _active_video_ids


def create_job(video_id: str, artist: str, title: str) -> Job:
    job = Job(
        job_id=str(uuid.uuid4()),
        video_id=video_id,
        artist=artist,
        title=title,
    )
    with _lock:
        _jobs[job.job_id] = job
        _active_video_ids.add(video_id)
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
        if fields.get("status") in ("done", "error"):
            _active_video_ids.discard(job.video_id)
