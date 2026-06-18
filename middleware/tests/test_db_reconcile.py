"""Tests for startup reconciliation of orphaned downloads.

Job progress lives in memory and is lost on restart; init_db() must flip any
row left mid-flight to a terminal 'error' state so it doesn't poll forever.
"""

import db


def _setup_tmp_db(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "kobser.db")
    db.init_db()


def test_inflight_rows_are_failed_on_restart(tmp_path, monkeypatch):
    _setup_tmp_db(tmp_path, monkeypatch)
    for i, status in enumerate(["pending", "downloading", "tagging", "scanning"]):
        db.upsert_download(f"job{i}", f"vid{i}", "Artist", "Title", status)

    # Simulate a process restart.
    db.init_db()

    for i in range(4):
        rec = db.get_download_record(f"job{i}")
        assert rec["status"] == "error"
        assert rec["error"] == "interrupted by restart"
        assert rec["completed_at"] is not None


def test_terminal_rows_are_left_untouched(tmp_path, monkeypatch):
    _setup_tmp_db(tmp_path, monkeypatch)
    db.upsert_download("done1", "vidd", "Artist", "Title", "done")
    db.upsert_download("err1", "vide", "Artist", "Title", "error", error="real failure")
    db.upsert_download("cancel1", "vidc", "Artist", "Title", "cancelled")

    db.init_db()

    assert db.get_download_record("done1")["status"] == "done"
    assert db.get_download_record("err1")["error"] == "real failure"
    assert db.get_download_record("cancel1")["status"] == "cancelled"
