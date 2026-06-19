"""Downloads are per-user: a user sees and controls only their own, while an
admin sees and controls everyone's. Covers the DB-level scoping and the route
ownership checks (list / status / delete / cancel)."""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

import db
import jobs
from auth import get_current_session
from routes import download as dl


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", tmp_path / "kobser.db")
    db.init_db()
    jobs._jobs.clear()
    jobs._active_video_ids.clear()
    jobs._cancelled.clear()

    app = FastAPI()
    app.include_router(dl.router)
    c = TestClient(app)

    def _as(username, is_admin=False):
        app.dependency_overrides[get_current_session] = lambda: {
            "id": "sid", "username": username, "salt": "s", "token": "t",
            "library_path": None, "is_admin": int(is_admin),
        }

    c.as_user = _as
    return c


def _seed(owner, id_="d1", status="done"):
    db.upsert_download(id_, "vid00000001", "Artist", "Title", status, username=owner)


# ── DB-level scoping ─────────────────────────────────────────────────────────

def test_db_list_is_scoped_to_user(client):
    _seed("alice", "a1")
    _seed("bob", "b1")
    assert {r["id"] for r in db.list_downloads("alice")} == {"a1"}
    assert {r["id"] for r in db.list_downloads("bob")} == {"b1"}


def test_db_admin_sees_all(client):
    _seed("alice", "a1")
    _seed("bob", "b1")
    assert {r["id"] for r in db.list_downloads("alice", is_admin=True)} == {"a1", "b1"}


# ── Route ownership ──────────────────────────────────────────────────────────

def test_list_returns_only_own(client):
    _seed("alice", "a1")
    _seed("bob", "b1")
    client.as_user("alice")
    ids = {d["id"] for d in client.get("/api/downloads").json()["downloads"]}
    assert ids == {"a1"}


def test_admin_list_returns_all(client):
    _seed("alice", "a1")
    _seed("bob", "b1")
    client.as_user("admin", is_admin=True)
    ids = {d["id"] for d in client.get("/api/downloads").json()["downloads"]}
    assert ids == {"a1", "b1"}


def test_cannot_delete_others_download(client):
    _seed("bob", "b1")
    client.as_user("alice")
    assert client.delete("/api/downloads/b1").status_code == 404
    assert db.get_download_record("b1") is not None  # untouched


def test_admin_can_delete_any_download(client):
    _seed("bob", "b1")
    client.as_user("admin", is_admin=True)
    assert client.delete("/api/downloads/b1").status_code == 200
    assert db.get_download_record("b1") is None


def test_cannot_see_others_status(client):
    _seed("bob", "b1")
    client.as_user("alice")
    assert client.get("/api/status/b1").status_code == 404


def test_cannot_cancel_others_live_job(client):
    job = jobs.create_job("vid00000002", "Artist", "Title", "bob")
    client.as_user("alice")
    assert client.post(f"/api/jobs/{job.job_id}/cancel").status_code == 404
    # bob's job is still running (not cancelled by alice)
    assert jobs.get_job(job.job_id).status != "cancelled"
