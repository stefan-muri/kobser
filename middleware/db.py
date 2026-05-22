import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path

from config import PEEL_DATA_DIR

DB_PATH = Path(PEEL_DATA_DIR) / "peel.db"


def init_db() -> None:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                password TEXT NOT NULL,
                expires_at INTEGER NOT NULL
            )
        """)


@contextmanager
def _conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def create_session(username: str, password: str, ttl_days: int = 30) -> str:
    sid = secrets.token_urlsafe(32)
    expires_at = int(time.time()) + ttl_days * 86400
    with _conn() as c:
        c.execute(
            "INSERT INTO sessions (id, username, password, expires_at) VALUES (?, ?, ?, ?)",
            (sid, username, password, expires_at),
        )
    return sid


def get_session(sid: str) -> dict | None:
    with _conn() as c:
        row = c.execute(
            "SELECT * FROM sessions WHERE id = ? AND expires_at > ?",
            (sid, int(time.time())),
        ).fetchone()
    return dict(row) if row else None


def delete_session(sid: str) -> None:
    with _conn() as c:
        c.execute("DELETE FROM sessions WHERE id = ?", (sid,))
