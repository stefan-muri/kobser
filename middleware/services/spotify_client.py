import json
import logging
import re

import httpx

log = logging.getLogger(__name__)

# A browser-like UA keeps Spotify from serving us a stripped/blocked response.
_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


class SpotifyError(Exception):
    """Raised for any Spotify import failure (bad URL, private playlist, parse error)."""


def parse_playlist_id(url: str) -> str | None:
    """Extract a playlist id from a share URL or URI.

    Handles open.spotify.com/playlist/<id>, /intl-xx/playlist/<id>, and
    spotify:playlist:<id>.
    """
    if not url:
        return None
    m = re.search(r"playlist[/:]([A-Za-z0-9]+)", url.strip())
    return m.group(1) if m else None


def _extract_next_data(html: str) -> dict | None:
    m = re.search(
        r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.DOTALL
    )
    if not m:
        return None
    try:
        return json.loads(m.group(1))
    except json.JSONDecodeError:
        return None


async def fetch_playlist(url: str) -> dict:
    """Return {"name": str, "tracks": [{"artist", "title", "album"}]} for a public playlist.

    Reads the public embed page (no API key / Premium needed). Only works for
    public playlists; the embed JSON carries title + artist per track.
    """
    playlist_id = parse_playlist_id(url)
    if not playlist_id:
        raise SpotifyError("That doesn't look like a Spotify playlist link")

    try:
        async with httpx.AsyncClient(
            timeout=20, headers={"User-Agent": _UA}, follow_redirects=True
        ) as client:
            r = await client.get(f"https://open.spotify.com/embed/playlist/{playlist_id}")
            r.raise_for_status()
            html = r.text
    except Exception as e:
        raise SpotifyError(f"Couldn't reach Spotify: {e}") from e

    data = _extract_next_data(html)
    if not data:
        raise SpotifyError("Couldn't read playlist data from Spotify (page format may have changed)")

    try:
        entity = data["props"]["pageProps"]["state"]["data"]["entity"]
    except (KeyError, TypeError):
        log.warning("spotify embed: unexpected JSON shape for playlist %s", playlist_id)
        raise SpotifyError("Couldn't find the tracks — make sure the playlist is public")

    name = entity.get("name") or entity.get("title") or "Imported playlist"
    track_list = entity.get("trackList") or []

    tracks: list[dict] = []
    for t in track_list:
        title = t.get("title")
        if not title:
            continue
        tracks.append({
            "artist": t.get("subtitle") or "Unknown",
            "title": title,
            "album": "",  # embed data doesn't expose album
        })

    if not tracks:
        raise SpotifyError("Playlist has no tracks, or it isn't public")
    return {"name": name, "tracks": tracks}
