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

That's all you need to run the server. (Building the Android app additionally needs Android Studio — see [below](#android-app).)

---

## Quick start

```bash
# 1. Clone
git clone https://github.com/PhaneSchema/kobser.git
cd kobser

# 2. Configure
cp .env.example .env
#   Edit .env — at minimum set MUSIC_DIR to where you want music stored.

# 3. Start
docker compose up -d

# 4. Create your account (one-time)
#   Open http://localhost:4533  → create a Navidrome admin account.
#   Open http://localhost:8000  → log in with those same credentials.
```

The Navidrome admin page (`:4533`) is only needed once, to create accounts and (optionally) assign per-user library paths.

---

## Configuration

All configuration lives in `.env` (copied from `.env.example`).

| Variable | Default | Description |
|---|---|---|
| `MUSIC_DIR` | `./data/music` | Where music files are stored on the host |
| `KOBSER_PORT` | `8000` | Port for the kobser web UI / API |
| `NAVIDROME_PORT` | `4533` | Port for the Navidrome admin UI (first-run / account management) |
| `YTDLP_COOKIES_FILE` | _(unset)_ | Path to a `cookies.txt` for age-restricted videos |

Apply changes with `docker compose up -d`.

### Cookies (optional)

Some videos require authentication. Export your browser cookies as a Netscape-format `cookies.txt`, drop it in `secrets/`, and point `.env` at it:

```bash
cp ~/Downloads/cookies.txt ./secrets/cookies.txt
```
```dotenv
YTDLP_COOKIES_FILE=./secrets/cookies.txt
```

`secrets/` is gitignored (only `cookies.txt.example` is tracked).

---

## Multi-user & per-user libraries

User accounts are Navidrome accounts — kobser stores no separate user database, only a session token. When a user downloads a track, kobser asks Navidrome which library that user is assigned to and writes the file into that library's path. Assign libraries per user in the Navidrome admin UI; users without an explicit assignment fall back to the default `MUSIC_DIR`.

---

## Android app

The app lives in [`android/`](android/) (Kotlin, Jetpack Compose, Media3/ExoPlayer).

- **Build:** open `android/` in **Android Studio** and run. It requires **JDK 17** (Android Studio's bundled JBR works; newer system JDKs may fail the build).
- **Connect:** on first launch, enter your server URL (e.g. `http://192.168.1.50:8000`) and log in with your Navidrome credentials.
- **Android Auto:** browse Songs / Artists / Playlists / Favorites, search (local + YouTube Music), shuffle/repeat, and queue from the car.

> A signed release APK is not distributed here — build it yourself from source.

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

All bind-mounted from the host; they survive container rebuilds. (`data/` is gitignored.)

---

## Updating

```bash
git pull
docker compose build --pull
docker compose up -d
```

---

## Development

- **Web frontend** (`middleware/frontend/`): `npm install && npm run dev` (Vite dev server, proxies `/api` to the running middleware).
- **Middleware** (`middleware/`): FastAPI app (`main.py`); rebuild the container to pick up Python changes.
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
