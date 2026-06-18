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
        # Pre-token sessions stored the cleartext Navidrome password. If we find
        # that old schema, drop the table so no plaintext lingers; users simply
        # re-login and the new schema persists only a derived Subsonic
        # (salt, token) pair plus the resolved library path.
        existing = conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='sessions'"
        ).fetchone()
        if existing:
            scols = {r[1] for r in conn.execute("PRAGMA table_info(sessions)").fetchall()}
            if "token" not in scols:
                conn.execute("DROP TABLE sessions")
        conn.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                salt TEXT NOT NULL,
                token TEXT NOT NULL,
                library_path TEXT,
                expires_at INTEGER NOT NULL
            )
        """)
        # Hygiene: drop sessions that have already expired (they're filtered on
        # read anyway, this just stops the table accumulating dead rows).
        conn.execute("DELETE FROM sessions WHERE expires_at <= ?", (int(time.time()),))
        conn.execute("""
            CREATE TABLE IF NOT EXISTS downloads (
                id TEXT PRIMARY KEY,
                video_id TEXT NOT NULL,
                artist TEXT NOT NULL,
                title TEXT NOT NULL,
                album TEXT,
                source TEXT,
                status TEXT NOT NULL DEFAULT 'pending',
                error TEXT,
                file_path TEXT,
                file_size_bytes INTEGER,
                started_at INTEGER NOT NULL,
                completed_at INTEGER
            )
        """)
        # Migrate older DBs that predate the album/source columns.
        cols = {r[1] for r in conn.execute("PRAGMA table_info(downloads)").fetchall()}
        if "album" not in cols:
            conn.execute("ALTER TABLE downloads ADD COLUMN album TEXT")
        if "source" not in cols:
            conn.execute("ALTER TABLE downloads ADD COLUMN source TEXT")

        # Job state lives in memory (see jobs.py) and doesn't survive a restart.
        # Any row still marked in-flight at startup is therefore orphaned from a
        # previous process — flip it to error so it doesn't sit "downloading"
        # forever, and so the UI offers a retry instead of polling a dead job.
        conn.execute(
            """
            UPDATE downloads
               SET status = 'error',
                   error = 'interrupted by restart',
                   completed_at = ?
             WHERE status IN ('pending', 'downloading', 'tagging', 'scanning')
            """,
            (int(time.time()),),
        )


@contextmanager
def _conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def create_session(
    username: str, salt: str, token: str, library_path: str | None = None,
    ttl_days: int = 30,
) -> str:
    sid = secrets.token_urlsafe(32)
    expires_at = int(time.time()) + ttl_days * 86400
    with _conn() as c:
        c.execute(
            "INSERT INTO sessions (id, username, salt, token, library_path, expires_at) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (sid, username, salt, token, library_path, expires_at),
        )
    return sid


def delete_expired_sessions() -> int:
    """Purge sessions past their expiry. Returns the number removed."""
    with _conn() as c:
        cur = c.execute("DELETE FROM sessions WHERE expires_at <= ?", (int(time.time()),))
        return cur.rowcount


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
    album: str | None = None,
    source: str | None = None,
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
                (id, video_id, artist, title, album, source, status, error,
                 file_path, file_size_bytes, started_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                status          = excluded.status,
                error           = excluded.error,
                album           = COALESCE(excluded.album,           album),
                source          = COALESCE(excluded.source,          source),
                file_path       = COALESCE(excluded.file_path,       file_path),
                file_size_bytes = COALESCE(excluded.file_size_bytes, file_size_bytes),
                completed_at    = COALESCE(excluded.completed_at,    completed_at)
            """,
            (
                id_, video_id, artist, title, album, source, status, error,
                file_path, file_size_bytes, started_at or int(time.time()), completed_at,
            ),
        )


def list_downloads() -> list[dict]:
    with _conn() as c:
        rows = c.execute(
            "SELECT * FROM downloads ORDER BY started_at DESC LIMIT 200"
        ).fetchall()
    return [dict(r) for r in rows]


def get_download_record(id_: str) -> dict | None:
    with _conn() as c:
        row = c.execute("SELECT * FROM downloads WHERE id = ?", (id_,)).fetchone()
    return dict(row) if row else None


def delete_download_record(id_: str) -> None:
    with _conn() as c:
        c.execute("DELETE FROM downloads WHERE id = ?", (id_,))
