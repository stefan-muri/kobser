"""Targeted Navidrome scans and wait-until-visible (faster library updates)."""

import sqlite3

import pytest

import db
from services import navidrome_client as nd


@pytest.fixture
def anyio_backend():
    return "asyncio"


class TestScanTarget:
    def test_artist_folder_relative_to_library(self, tmp_path):
        (tmp_path / "Yeat").mkdir()
        assert nd.scan_target(1, str(tmp_path), tmp_path / "Yeat") == "1:Yeat"

    def test_nested_folder_uses_posix_path(self, tmp_path):
        (tmp_path / "A" / "Album").mkdir(parents=True)
        assert nd.scan_target(3, str(tmp_path), tmp_path / "A" / "Album") == "3:A/Album"

    def test_missing_folder_targets_nearest_existing_ancestor(self, tmp_path):
        # Navidrome skips a target that doesn't exist without dropping its tracks,
        # so a removed album folder is covered by scanning its artist folder...
        (tmp_path / "Artist").mkdir()
        assert nd.scan_target(1, str(tmp_path), tmp_path / "Artist" / "Gone Album") == "1:Artist"

    def test_removed_top_level_folder_falls_back_to_full_scan(self, tmp_path):
        # ...and a removed top-level artist folder leaves only the root: full scan.
        assert nd.scan_target(1, str(tmp_path), tmp_path / "Gone Artist") is None

    def test_falls_back_when_not_expressible(self, tmp_path):
        assert nd.scan_target(None, str(tmp_path), tmp_path / "x") is None
        assert nd.scan_target(1, None, tmp_path / "x") is None
        assert nd.scan_target(1, str(tmp_path), tmp_path) is None            # the root itself
        assert nd.scan_target(1, str(tmp_path / "lib"), tmp_path / "other") is None  # outside


class FakeClock:
    def __init__(self):
        self.t = 0.0

    def __call__(self):
        return self.t


@pytest.fixture
def fake_scan(monkeypatch):
    calls = []
    clock = FakeClock()

    async def start_scan(username, salt, token, targets=None):
        calls.append(targets)
        return {}

    async def sleep(s):
        clock.t += s

    monkeypatch.setattr(nd, "start_scan", start_scan)
    monkeypatch.setattr(nd.asyncio, "sleep", sleep)
    return calls, clock


@pytest.mark.anyio
async def test_returns_as_soon_as_change_is_visible(fake_scan):
    calls, clock = fake_scan
    checks = iter([False, False, True])

    async def done():
        return next(checks)

    ok = await nd.scan_until("u", "s", "t", done, targets=["1:Yeat"], clock=clock)
    assert ok is True
    assert calls == [["1:Yeat"]]          # targeted only, no full-scan fallback
    assert clock.t < 1.0


@pytest.mark.anyio
async def test_falls_back_to_full_scan_once_after_grace(fake_scan):
    calls, clock = fake_scan

    async def done():
        return clock.t >= 7.0             # becomes visible only after the full scan

    ok = await nd.scan_until("u", "s", "t", done, targets=["1:Yeat"], targeted_grace_s=6.0, clock=clock)
    assert ok is True
    assert calls == [["1:Yeat"], None]     # one targeted, then exactly one full scan


@pytest.mark.anyio
async def test_gives_up_after_timeout_without_raising(fake_scan):
    calls, clock = fake_scan

    async def done():
        return False

    ok = await nd.scan_until("u", "s", "t", done, targets=None, timeout_s=5.0, clock=clock)
    assert ok is False
    assert calls == [None]                 # full scan requested once, no extra fallback
    assert 5.0 <= clock.t < 6.0


@pytest.mark.anyio
async def test_scan_errors_never_propagate(monkeypatch):
    clock = FakeClock()

    async def boom(*a, **k):
        raise RuntimeError("navidrome down")

    async def sleep(s):
        clock.t += s

    monkeypatch.setattr(nd, "start_scan", boom)
    monkeypatch.setattr(nd.asyncio, "sleep", sleep)

    async def done():
        raise RuntimeError("search failed")

    assert await nd.scan_until("u", "s", "t", done, targets=["1:x"], timeout_s=2.0, clock=clock) is False


class TestLibraryIdColumn:
    def test_migration_adds_column_and_session_round_trips_it(self, tmp_path, monkeypatch):
        monkeypatch.setattr(db, "DB_PATH", tmp_path / "kobser.db")
        db.init_db()
        sid = db.create_session("alice", "s", "t", "/music", library_id=4)
        assert db.get_session(sid)["library_id"] == 4
        old = db.create_session("bob", "s", "t")
        assert db.get_session(old)["library_id"] is None

    def test_existing_db_without_column_is_migrated(self, tmp_path, monkeypatch):
        monkeypatch.setattr(db, "DB_PATH", tmp_path / "kobser.db")
        with sqlite3.connect(db.DB_PATH) as conn:
            conn.execute(
                "CREATE TABLE sessions (id TEXT PRIMARY KEY, username TEXT NOT NULL, salt TEXT NOT NULL,"
                " token TEXT NOT NULL, library_path TEXT, is_admin INTEGER NOT NULL DEFAULT 0,"
                " expires_at INTEGER NOT NULL)"
            )
        db.init_db()
        with sqlite3.connect(db.DB_PATH) as conn:
            cols = {r[1] for r in conn.execute("PRAGMA table_info(sessions)")}
        assert "library_id" in cols


@pytest.mark.anyio
async def test_resolve_library_id_prefers_stored_value():
    assert await nd.resolve_library_id({"username": "u", "salt": "s", "token": "t", "library_id": 7}) == 7


@pytest.mark.anyio
async def test_new_song_visible_ignores_songs_that_already_matched(monkeypatch):
    results = [[{"id": "old", "title": "Back Home", "artist": "Yeat, Joji"}]]

    async def search(query, u, s, t, count=10):
        return results[-1]

    monkeypatch.setattr(nd, "search_songs", search)
    known = await nd.matching_song_ids("Yeat, Joji", "Back Home", "u", "s", "t")
    assert known == {"old"}
    assert await nd.new_song_visible("Yeat, Joji", "Back Home", known, "u", "s", "t") is False
    results.append(results[0] + [{"id": "new", "title": "Back  Home", "artist": "yeat joji"}])
    assert await nd.new_song_visible("Yeat, Joji", "Back Home", known, "u", "s", "t") is True


@pytest.mark.anyio
async def test_song_is_unlisted_tracks_the_listing_not_the_id(monkeypatch):
    listed = [{"id": "x1", "title": "T"}]

    async def strict(query, u, s, t, count=50):
        return listed

    monkeypatch.setattr(nd, "_search_songs_strict", strict)
    assert await nd.song_is_unlisted("x1", "T", "u", "s", "t") is False
    listed.clear()
    assert await nd.song_is_unlisted("x1", "T", "u", "s", "t") is True


@pytest.mark.anyio
async def test_song_is_unlisted_raises_on_navidrome_errors(monkeypatch):
    # An error must not read as "gone" — scan_until treats raising as "not yet".
    async def strict(query, u, s, t, count=50):
        raise RuntimeError("search3 failed")

    monkeypatch.setattr(nd, "_search_songs_strict", strict)
    with pytest.raises(RuntimeError):
        await nd.song_is_unlisted("x1", "T", "u", "s", "t")
