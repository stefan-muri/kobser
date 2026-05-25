import os

NAVIDROME_URL = os.environ.get("NAVIDROME_URL", "http://navidrome:4533")
NAVIDROME_USER = os.environ.get("NAVIDROME_USER", "admin")
NAVIDROME_PASS = os.environ.get("NAVIDROME_PASS", "")
YTDLP_COOKIES_FILE = os.environ.get("YTDLP_COOKIES_FILE", "")
MUSIC_DIR = os.environ.get("MUSIC_DIR", "/music")
KOBSER_DATA_DIR = os.environ.get("KOBSER_DATA_DIR", "/app/data")
