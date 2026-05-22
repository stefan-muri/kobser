import asyncio
import hashlib
import secrets
from typing import Any

import httpx

from config import NAVIDROME_URL

CLIENT_ID = "peel-middleware"
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
