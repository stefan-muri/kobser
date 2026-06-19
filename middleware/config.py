import os
from pathlib import Path


def _load_dotenv() -> None:
    """Load a repo-root `.env` for local (non-Docker) runs.

    Stdlib-only, no dependency. Existing environment variables always win, so
    Docker / explicit `VAR=... uvicorn` launches are never overridden. Lines are
    `KEY=value`; blank lines and `#` comments are ignored, surrounding quotes on
    the value are stripped. Missing file is a no-op.
    """
    env_path = Path(__file__).resolve().parent.parent / ".env"
    try:
        text = env_path.read_text()
    except OSError:
        return
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


_load_dotenv()

NAVIDROME_URL = os.environ.get("NAVIDROME_URL", "http://navidrome:4533")
YTDLP_COOKIES_FILE = os.environ.get("YTDLP_COOKIES_FILE", "")
MUSIC_DIR = os.environ.get("MUSIC_DIR", "/music")
KOBSER_DATA_DIR = os.environ.get("KOBSER_DATA_DIR", "/app/data")
