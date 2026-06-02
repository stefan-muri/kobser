# kobser

**Self-hosted music for your home lab.** Search YouTube / YouTube Music, download tracks straight into your own library, and stream everything from a clean web app or a native Android app — with Android Auto support.

Built on [Navidrome](https://www.navidrome.org/) (music server + Subsonic API) with a FastAPI middleware, a Svelte web app, and a Kotlin/Compose Android app.

> **License:** [GPL-3.0](#license) · **Status:** self-hosted, multi-user.

---

## Features

- **Search YouTube & YouTube Music** — songs, artists, and full albums (via `yt-dlp` + `ytmusicapi`).
- **Download to your library** — tracked with artist/title/album, embedded cover art, auto-rescanned into Navidrome.
- **Multi-user** — each Navidrome user downloads into *their own* assigned library path.
- **Preview before downloading** — stream a result without committing it to disk.
- **Stream your library** — browse artists/albums/songs, liked songs, and playlists.
- **Native Android app** — same backend, with a queue, drag-to-reorder, and **Android Auto** (browse, search, play in the car).
- **Cookie support** — optional, for age-restricted/region-locked videos.

---

## Requirements

- [Docker](https://docs.docker.com/get-docker/) + [Docker Compose](https://docs.docker.com/compose/)

No build step — the server image is pulled automatically from [ghcr.io](https://github.com/PhaneSchema/kobser/pkgs/container/kobser).

---

## Quick start

### Full stack (Navidrome + kobser)

No existing Navidrome? This starts everything.

```bash
# 1. Download the compose file and env template
mkdir kobser && cd kobser
curl -O https://raw.githubusercontent.com/PhaneSchema/kobser/master/docker-compose.yml
curl -O https://raw.githubusercontent.com/PhaneSchema/kobser/master/.env.example
mv .env.example .env

# 2. Edit .env — at minimum set MUSIC_DIR to where you want music stored

# 3. Start
docker compose --profile full up -d

# 4. Create your account (one-time)
#   Open http://localhost:4533 → create a Navidrome admin account
#   Open http://localhost:8000 → log in with those same credentials
```

The Navidrome admin page (`:4533`) is only needed once, to create accounts and (optionally) assign per-user library paths.

### Middleware only (existing Navidrome)

Already running Navidrome? Just add kobser on top — no second Navidrome starts.

```bash
mkdir kobser && cd kobser
curl -O https://raw.githubusercontent.com/PhaneSchema/kobser/master/docker-compose.yml
curl -O https://raw.githubusercontent.com/PhaneSchema/kobser/master/.env.example
mv .env.example .env

# Set NAVIDROME_URL to your existing Navidrome, e.g.:
#   NAVIDROME_URL=http://192.168.1.50:4533
# Set MUSIC_DIR to the same music path your Navidrome uses

docker compose up -d
```

---

## Configuration

All configuration lives in `.env`.

| Variable | Default | Description |
|---|---|---|
| `MUSIC_DIR` | `./data/music` | Where music files are stored on the host |
| `KOBSER_PORT` | `8000` | Port for the kobser web UI / API |
| `NAVIDROME_PORT` | `4533` | Port for the Navidrome admin UI (full-stack mode only) |
| `NAVIDROME_URL` | `http://navidrome:4533` | URL of your Navidrome instance (set this in middleware-only mode) |
| `YTDLP_COOKIES_FILE` | _(unset)_ | Path to a `cookies.txt` for age-restricted videos |

Apply changes with `docker compose up -d`.

### Cookies (optional)

Some videos require authentication. Export your browser cookies as a Netscape-format `cookies.txt`, drop it in `secrets/`, and point `.env` at it:

```bash
mkdir -p secrets
cp ~/Downloads/cookies.txt ./secrets/cookies.txt
```
```dotenv
YTDLP_COOKIES_FILE=./secrets/cookies.txt
```

---

## Multi-user & per-user libraries

User accounts are Navidrome accounts — kobser stores no separate user database, only a session token. When a user downloads a track, kobser asks Navidrome which library that user is assigned to and writes the file into that library's path. Assign libraries per user in the Navidrome admin UI; users without an explicit assignment fall back to the default `MUSIC_DIR`.

---

## Android app

Download the latest APK from the [Releases](https://github.com/PhaneSchema/kobser/releases) page, enable **Install from unknown sources** on your device, and open the file to install.

- **Connect:** on first launch, enter your server URL (e.g. `http://192.168.1.50:8000`) and log in with your Navidrome credentials.
- **Android Auto:** browse Songs / Artists / Playlists / Favorites, search (local + YouTube Music), shuffle/repeat, and queue from the car.

> The APK is a debug build signed with the standard Android debug key. It is built automatically from the latest commit by GitHub Actions.

To build from source: open [`android/`](android/) in **Android Studio** with JDK 17 (the bundled JBR works; newer system JDKs may fail the build).

---

## Architecture

```
 Web app (:8000)          Android app
      │                        │
      └───────────┬────────────┘
                  ▼
        FastAPI middleware  ──►  yt-dlp + ytmusicapi   (search / download / preview)
                  │
                  └──►  Navidrome  ──►  Music files (shared volume)
                        (Subsonic API: library, playlists, liked, streaming)
```

The middleware serves the compiled web app and proxies the Subsonic API; both clients use the same HTTP API. yt-dlp runs against the **Deno** JS runtime (bundled in the image) for YouTube's signature challenge.

---

## Data & persistence

| Path | Contents |
|---|---|
| `./data/music/` | Downloaded music files |
| `./data/navidrome/` | Navidrome database and config |
| `./data/kobser/` | kobser session database |

All bind-mounted from the host; they survive container rebuilds.

---

## Updating

```bash
docker compose pull
docker compose up -d        # or: docker compose --profile full up -d
```

---

## Development

- **Web frontend** (`middleware/frontend/`): `npm install && npm run dev` (Vite dev server, proxies `/api` to the running middleware).
- **Middleware** (`middleware/`): FastAPI app; `docker compose restart middleware` picks up Python changes without a rebuild (source dirs are mounted via `docker-compose.override.yml`).
- **Android** (`android/`): Android Studio + JDK 17.

See [CONTRIBUTING.md](CONTRIBUTING.md) for more.

---

## Security

kobser is meant for **trusted self-hosted use**, not hostile public exposure.
Run it behind HTTPS (a reverse proxy), and note that the middleware stores
Navidrome credentials in its session database so it can talk to the Subsonic
API. See [SECURITY.md](SECURITY.md) for the threat model and how to report
vulnerabilities.

---

## Legal

kobser uses [`yt-dlp`](https://github.com/yt-dlp/yt-dlp) to fetch audio from YouTube. **Downloading copyrighted material without permission may violate YouTube's Terms of Service and/or copyright law in your jurisdiction.** This project is provided for personal use with content you have the right to download. You are responsible for how you use it; the authors assume no liability.

---

## License

[GPL-3.0](LICENSE) © contributors. kobser is free software: you may redistribute and/or modify it under the terms of the GNU General Public License v3.
