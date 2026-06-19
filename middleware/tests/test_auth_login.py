"""Tests for the login endpoint's failure-mode handling.

A bad password and an unreachable Navidrome must be distinguishable: bad
credentials return 401 (and count against the rate limit), while a Navidrome
that throws returns 502 (and does NOT count against the limit), so a flaky
backend can't lock users out or masquerade as a wrong password.
"""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from routes import auth as auth_routes


@pytest.fixture
def client(monkeypatch):
    # Don't touch the real session DB; just hand back a fake id.
    monkeypatch.setattr(auth_routes, "create_session", lambda *a, **k: "fake-sid")

    async def _no_libs(username, password):
        return []

    monkeypatch.setattr(auth_routes, "get_user_libraries", _no_libs)

    auth_routes._login_failures.clear()
    app = FastAPI()
    app.include_router(auth_routes.router)
    yield TestClient(app)
    auth_routes._login_failures.clear()


def _login(client, password="pw"):
    return client.post("/api/login", json={"username": "u", "password": password})


def test_valid_credentials_return_200(client, monkeypatch):
    async def _ping_ok(*a, **k):
        return {"status": "ok"}

    monkeypatch.setattr(auth_routes, "ping", _ping_ok)
    r = _login(client)
    assert r.status_code == 200
    assert r.json()["sessionId"] == "fake-sid"


def test_bad_credentials_return_401_and_count(client, monkeypatch):
    async def _ping_failed(*a, **k):
        return {"status": "failed"}

    monkeypatch.setattr(auth_routes, "ping", _ping_failed)
    r = _login(client)
    assert r.status_code == 401
    # A genuine credential rejection is recorded against the rate limit.
    assert len(auth_routes._login_failures) == 1


def test_unreachable_navidrome_returns_502_and_does_not_count(client, monkeypatch):
    async def _ping_boom(*a, **k):
        raise ConnectionError("All connection attempts failed")

    monkeypatch.setattr(auth_routes, "ping", _ping_boom)
    r = _login(client)
    assert r.status_code == 502
    assert r.json()["detail"] == "music server unreachable"
    # An infrastructure failure must NOT be held against the client.
    assert len(auth_routes._login_failures) == 0
