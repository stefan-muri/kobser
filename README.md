# peel

A self-hosted music system for home labs. Search YouTube, download tracks, and stream your personal library — all from a clean web UI.

Built on [Navidrome](https://www.navidrome.org/) (music server) + a FastAPI middleware + a vanilla JS single-page app.

---

## Requirements

- [Docker](https://docs.docker.com/get-docker/) + [Docker Compose](https://docs.docker.com/compose/)
- That's it.

---

## Quick start

```bash
# 1. Clone
git clone <your-gitea-url>/peel.git
cd peel

# 2. Configure
cp .env.example .env
# Edit .env — at minimum set MUSIC_DIR to wherever you want music stored

# 3. Start
docker compose up -d

# 4. Create your account
# Open http://localhost:4533 and create an admin account (one-time setup)
# Then open http://localhost:8000 and log in with those same credentials
```

Done. The Navidrome admin page (`localhost:4533`) is only needed this one time to create your account.

---

## Configuration

All configuration lives in `.env`. Copy `.env.example` to get started.

| Variable | Default | Description |
|---|---|---|
| `MUSIC_DIR` | `./data/music` | Where music files are stored on the host |
| `PEEL_PORT` | `8000` | Port for the Peel web UI |
| `NAVIDROME_PORT` | `4533` | Port for the Navidrome admin UI (first-run only) |
| `YTDLP_COOKIES_FILE` | _(unset)_ | Path to a `cookies.txt` for age-restricted videos |

Restart the stack after changing `.env`:
```bash
docker compose up -d
```

---

## Cookies (optional)

Some YouTube videos require authentication (age-restricted, region-locked). To download those, export your browser cookies as a Netscape-format `cookies.txt` and drop it in the `secrets/` folder:

```bash
cp ~/Downloads/cookies.txt ./secrets/cookies.txt
```

Then set in `.env`:
```
YTDLP_COOKIES_FILE=./secrets/cookies.txt
```

> The `secrets/` directory is gitignored. A browser extension like [cookies.txt](https://github.com/lennonhill/cookies-txt) can export the right format.

---

## Architecture

```
Browser
  │
  └─► Peel UI (port 8000)
        │
        ├─► FastAPI middleware  ──► yt-dlp (search + download)
        │        │
        │        └─► Navidrome Subsonic API (library, playlists, liked songs, streaming)
        │
        └─► Navidrome (port 4533, internal after first-run)
              │
              └─► Music files (shared volume)
```

User accounts are Navidrome accounts. Peel stores only a session token in the browser (localStorage) — no separate user database.

---

## Data & persistence

| Path | Contents |
|---|---|
| `./data/music/` | Downloaded music files |
| `./data/navidrome/` | Navidrome database and config |
| `./data/peel/` | Peel session database |

All three are bind-mounted from the host and survive container rebuilds.

---

## Updating

```bash
git pull
docker compose build --pull
docker compose up -d
```
