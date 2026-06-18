"""Tests for in-memory job map pruning.

_jobs / _cancelled must not grow without bound; _prune_locked drops the oldest
terminal jobs beyond the cap while always keeping in-flight ones.
"""

import jobs
from jobs import Job


def _reset():
    jobs._jobs.clear()
    jobs._cancelled.clear()
    jobs._active_video_ids.clear()


def _job(jid, status, started_at):
    return Job(job_id=jid, video_id=f"v_{jid}", artist="a", title="t",
               status=status, started_at=started_at)


def test_caps_terminal_keeps_active(monkeypatch):
    _reset()
    monkeypatch.setattr(jobs, "_MAX_TERMINAL_JOBS", 2)
    for i in range(3):
        jobs._jobs[f"done{i}"] = _job(f"done{i}", "done", started_at=i)
    jobs._jobs["live"] = _job("live", "downloading", started_at=99)
    jobs._cancelled.add("done0")

    with jobs._lock:
        jobs._prune_locked()

    # Oldest terminal (done0) evicted; the 2 newest terminal + the active one stay.
    assert set(jobs._jobs) == {"done1", "done2", "live"}
    # Evicted job's id is also cleared from _cancelled.
    assert "done0" not in jobs._cancelled


def test_noop_when_under_cap(monkeypatch):
    _reset()
    monkeypatch.setattr(jobs, "_MAX_TERMINAL_JOBS", 10)
    for i in range(3):
        jobs._jobs[f"done{i}"] = _job(f"done{i}", "done", started_at=i)

    with jobs._lock:
        jobs._prune_locked()

    assert len(jobs._jobs) == 3


def test_never_evicts_inflight(monkeypatch):
    _reset()
    monkeypatch.setattr(jobs, "_MAX_TERMINAL_JOBS", 0)
    for i in range(5):
        jobs._jobs[f"live{i}"] = _job(f"live{i}", "downloading", started_at=i)

    with jobs._lock:
        jobs._prune_locked()

    assert len(jobs._jobs) == 5
