import re
from pathlib import Path
from typing import Any

from yt_dlp import YoutubeDL

from config import MUSIC_DIR, YTDLP_COOKIES_FILE


def _cookies_opts() -> dict[str, Any]:
    if not YTDLP_COOKIES_FILE:
        return {}
    p = Path(YTDLP_COOKIES_FILE)
    if p.is_file() and p.stat().st_size > 0:
        return {"cookiefile": YTDLP_COOKIES_FILE}
    return {}


_INVALID_FS_CHARS = re.compile(r'[<>:"/\\|?*\x00-\x1f]')


def _sanitize(s: str) -> str:
    s = _INVALID_FS_CHARS.sub(" ", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s or "Unknown"


def search(query: str, limit: int = 10) -> list[dict[str, Any]]:
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noconfig": True,
        "extract_flat": True,
        "skip_download": True,
        **_cookies_opts(),
    }
    with YoutubeDL(opts) as ydl:
        result = ydl.extract_info(f"ytsearch{limit}:{query}", download=False)
    entries = (result or {}).get("entries") or []
    return [_normalize_entry(e) for e in entries if e]


def _normalize_entry(entry: dict) -> dict[str, Any]:
    return {
        "videoId": entry.get("id"),
        "title": entry.get("title"),
        "channel": entry.get("channel") or entry.get("uploader"),
        "duration": entry.get("duration"),
        "thumbnail": _best_thumbnail(entry),
    }


def _best_thumbnail(entry: dict) -> str | None:
    thumbs = entry.get("thumbnails")
    if isinstance(thumbs, list) and thumbs:
        return thumbs[-1].get("url")
    return entry.get("thumbnail")


def download(video_id: str, artist: str, title: str) -> str:
    """Download bestaudio in its native container. Returns the absolute file path."""
    artist_dir = Path(MUSIC_DIR) / _sanitize(artist)
    artist_dir.mkdir(parents=True, exist_ok=True)

    filename_stem = _sanitize(f"{artist} - {title}")
    outtmpl = str(artist_dir / f"{filename_stem}.%(ext)s")

    opts = {
        "quiet": True,
        "no_warnings": True,
        "noconfig": True,   # ignore any yt-dlp.conf that could override outtmpl
        # Prefer m4a (AAC) since it tags cleanly and is universally supported.
        # Fall back to bestaudio (usually opus-in-webm) and remux below.
        "format": "bestaudio[ext=m4a]/bestaudio",
        "outtmpl": outtmpl,
        "noplaylist": True,
        # preferredcodec="best" keeps the source codec (no re-encode) and only
        # remuxes the container when needed — e.g. opus-in-webm → opus-in-ogg.
        "postprocessors": [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "best",
            },
            # Embed the YouTube thumbnail into the audio file so Navidrome
            # can display cover art without a separate image fetch.
            {
                "key": "EmbedThumbnail",
            },
        ],
        "writethumbnail": True,
        **_cookies_opts(),
    }

    url = f"https://www.youtube.com/watch?v={video_id}"
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        if not info:
            raise RuntimeError("yt-dlp returned no info")

        requested = info.get("requested_downloads") or []
        if requested and requested[0].get("filepath"):
            return requested[0]["filepath"]
        return ydl.prepare_filename(info)
