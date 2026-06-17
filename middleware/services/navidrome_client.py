import asyncio
import hashlib
import re
import secrets
import unicodedata
from typing import Any

import httpx

from config import NAVIDROME_URL


def _norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", s.lower())

CLIENT_ID = "kobser-middleware"
API_VERSION = "1.16.1"


def auth_params(username: str, password: str) -> dict[str, str]:
    """Subsonic auth params with a fresh salt per call."""
    salt = secrets.token_hex(8)
    token = hashlib.md5((password + salt).encode()).hexdigest()
    return {
        "u": username,
        "t": token,
        "s": salt,
        "v": API_VERSION,
        "c": CLIENT_ID,
        "f": "json",
    }


async def ping(username: str, password: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.get(
            f"{NAVIDROME_URL}/rest/ping",
            params=auth_params(username, password),
        )
        r.raise_for_status()
        return r.json().get("subsonic-response", {})


async def start_scan(username: str, password: str) -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=30) as client:
        r = await client.get(
            f"{NAVIDROME_URL}/rest/startScan",
            params=auth_params(username, password),
        )
        r.raise_for_status()
        return r.json()


async def get_user_libraries(username: str, password: str) -> list[dict[str, Any]]:
    """Return the libraries assigned to the given user, via Navidrome's native API.

    Non-admin users can call /api/user/{their_own_id} — the response embeds the
    full `libraries` array (with paths) for the libraries that user is assigned
    to. No admin privileges or /api/library access required.

    Each entry is `{id, name, path}`. Returns an empty list on failure so callers
    can fall back to the default MUSIC_DIR.
    """
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            login = await client.post(
                f"{NAVIDROME_URL}/auth/login",
                json={"username": username, "password": password},
            )
            login.raise_for_status()
            login_data = login.json() or {}
            token = login_data.get("token")
            user_id = login_data.get("id")
            if not token or not user_id:
                return []

            user_resp = await client.get(
                f"{NAVIDROME_URL}/api/user/{user_id}",
                headers={"x-nd-authorization": f"Bearer {token}"},
            )
            user_resp.raise_for_status()
            libraries = (user_resp.json() or {}).get("libraries") or []

            return [
                {"id": l.get("id"), "name": l.get("name") or "", "path": l.get("path") or ""}
                for l in libraries
                if l.get("path")
            ]
    except Exception:
        return []


async def search_songs(query: str, username: str, password: str, count: int = 10) -> list[dict]:
    """Search Navidrome for songs matching `query`. Returns a list of song dicts."""
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.get(
                f"{NAVIDROME_URL}/rest/search3",
                params={
                    "query": query,
                    "songCount": count,
                    "artistCount": 0,
                    "albumCount": 0,
                    **auth_params(username, password),
                },
            )
            r.raise_for_status()
        body = r.json().get("subsonic-response", {})
        if body.get("status") != "ok":
            return []
        return body.get("searchResult3", {}).get("song") or []
    except Exception:
        return []


async def find_song_id(artist: str, title: str, username: str, password: str) -> str | None:
    """Return the Navidrome song id whose artist+title match (normalised), or None."""
    songs = await search_songs(title, username, password)
    na, nt = _norm(artist), _norm(title)
    for s in songs:
        if _norm(s.get("title", "")) == nt and _norm(s.get("artist", "")) == na:
            return s.get("id")
    # Looser fallback: title match alone (handles "feat." artist mismatches).
    for s in songs:
        if _norm(s.get("title", "")) == nt:
            return s.get("id")
    return None


async def create_playlist(name: str, song_ids: list[str], username: str, password: str) -> str | None:
    """Create a playlist with the given songs. Returns the new playlist id, or None."""
    try:
        params = [("name", name)]
        params += [("songId", sid) for sid in song_ids]
        async with httpx.AsyncClient(timeout=30) as client:
            r = await client.get(
                f"{NAVIDROME_URL}/rest/createPlaylist",
                params=params + list(auth_params(username, password).items()),
            )
            r.raise_for_status()
        body = r.json().get("subsonic-response", {})
        if body.get("status") != "ok":
            return None
        return (body.get("playlist") or {}).get("id")
    except Exception:
        return None


async def trigger_scan_and_wait(username: str, password: str, scan_wait_s: float = 1.5) -> None:
    """Trigger a Navidrome scan and give it long enough to finish.

    We deliberately don't poll getScanStatus to detect completion: its
    `lastScan` field only updates on full scans (not the quick-selective
    scans incremental additions use), and `scanning: false` flips back too
    fast (~30ms) to reliably catch. A short fixed wait covers Navidrome's
    on-demand scan in 99% of cases; if a download is somehow still missing,
    re-entering the Library tab re-fetches `getArtists` anyway."""
    try:
        await start_scan(username, password)
    except Exception:
        return
    await asyncio.sleep(scan_wait_s)
