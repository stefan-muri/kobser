"""Tests for the token-based session model (H1).

Sessions must store a derived Subsonic (salt, token) pair and the resolved
library path — never the cleartext Navidrome password — and upgrading from the
old password schema must purge the plaintext.
"""

import hashlib
import sqlite3
import time

import db
from services.navidrome_client import auth_params, make_credentials


class TestMakeCredentials:
    def test_token_matches_subsonic_scheme(self):
        salt, token = make_credentials("hunter2")
        assert token == hashlib.md5(("hunter2" + salt).encode()).hexdigest()
        assert len(salt) >= 16

    def test_salt_is_random_per_call(self):
        assert make_credentials("same-password")[0] != make_credentials("same-password")[0]

    def test_auth_params_uses_stored_pair(self):
        p = auth_params("alice", "saltval", "tokval")
        assert p["u"] == "alice"
        assert p["s"] == "saltval"
        assert p["t"] == "tokval"
        assert p["f"] == "json"


class TestSessionStore:
    def _db(self, tmp_path, monkeypatch):
        monkeypatch.setattr(db, "DB_PATH", tmp_path / "kobser.db")
        db.init_db()

    def test_roundtrip_stores_no_password(self, tmp_path, monkeypatch):
        self._db(tmp_path, monkeypatch)
        sid = db.create_session("alice", "saltval", "tokval", "/music/alice")
        s = db.get_session(sid)
        assert s["username"] == "alice"
        assert s["salt"] == "saltval"
        assert s["token"] == "tokval"
        assert s["library_path"] == "/music/alice"
        assert "password" not in s

    def test_expired_sessions_are_swept(self, tmp_path, monkeypatch):
        self._db(tmp_path, monkeypatch)
        # Insert one already-expired and one live session directly.
        with sqlite3.connect(db.DB_PATH) as c:
            c.execute(
                "INSERT INTO sessions (id, username, salt, token, library_path, expires_at) "
                "VALUES ('dead','u','s','t',NULL,?)",
                (int(time.time()) - 10,),
            )
        live = db.create_session("u", "s", "t", None)
        removed = db.delete_expired_sessions()
        assert removed == 1
        assert db.get_session("dead") is None
        assert db.get_session(live) is not None

    def test_migration_purges_plaintext_password_table(self, tmp_path, monkeypatch):
        path = tmp_path / "kobser.db"
        monkeypatch.setattr(db, "DB_PATH", path)
        # Simulate a pre-token DB: old schema with a cleartext password + a row.
        with sqlite3.connect(path) as c:
            c.execute(
                "CREATE TABLE sessions (id TEXT PRIMARY KEY, username TEXT NOT NULL, "
                "password TEXT NOT NULL, expires_at INTEGER NOT NULL)"
            )
            c.execute(
                "INSERT INTO sessions VALUES ('old','bob','plaintext-secret',?)",
                (int(time.time()) + 99999,),
            )

        db.init_db()

        with sqlite3.connect(path) as c:
            cols = {r[1] for r in c.execute("PRAGMA table_info(sessions)").fetchall()}
            assert "token" in cols
            assert "password" not in cols
            # The old plaintext row is gone — users simply re-login.
            assert c.execute("SELECT COUNT(*) FROM sessions").fetchone()[0] == 0
