import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path

from config import KOBSER_DATA_DIR

DB_PATH = Path(KOBSER_DATA_DIR) / "kobser.db"


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
        conn.execute("""
            CREATE TABLE IF NOT EXISTS downloads (
                id TEXT PRIMARY KEY,
                video_id TEXT NOT NULL,
                artist TEXT NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                error TEXT,
                file_path TEXT,
                file_size_bytes INTEGER,
                started_at INTEGER NOT NULL,
                completed_at INTEGER
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


# ── Downloads ────────────────────────────────────────────────────────────────

def upsert_download(
    id_: str,
    video_id: str,
    artist: str,
    title: str,
    status: str,
    *,
    error: str | None = None,
    file_path: str | None = None,
    file_size_bytes: int | None = None,
    started_at: int | None = None,
    completed_at: int | None = None,
) -> None:
    with _conn() as c:
        c.execute(
            """
            INSERT INTO downloads
                (id, video_id, artist, title, status, error, file_path,
                 file_size_bytes, started_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                status          = excluded.status,
                error           = excluded.error,
                file_path       = COALESCE(excluded.file_path,       file_path),
                file_size_bytes = COALESCE(excluded.file_size_bytes, file_size_bytes),
                completed_at    = COALESCE(excluded.completed_at,    completed_at)
            """,
            (
                id_, video_id, artist, title, status, error, file_path,
                file_size_bytes, started_at or int(time.time()), completed_at,
            ),
        )


def list_downloads() -> list[dict]:
    with _conn() as c:
        rows = c.execute(
            "SELECT * FROM downloads ORDER BY started_at DESC LIMIT 200"
        ).fetchall()
    return [dict(r) for r in rows]


def delete_download_record(id_: str) -> None:
    with _conn() as c:
        c.execute("DELETE FROM downloads WHERE id = ?", (id_,))
