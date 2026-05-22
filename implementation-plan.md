# Peel — YouTube → Navidrome Web App (Android later)

## Vision

A self-hosted music system. A single web app (served by the middleware) lets you:

1. **Search YouTube** for any track
2. **Download** it to your home server (yt-dlp, tagged with artist + title)
3. **Browse and stream** your library (backed by Navidrome's Subsonic API)

Phase 1 ships the web app. The middleware exposes a stable API. Later, a native Android app reuses the same API to bring playback into Android Auto.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Browser (web app)                          │
└─────────────────────────────────────────────────────────────┘
                          │  single origin, single API key
                          ▼
┌─────────────────────────────────────────────────────────────┐
│  Middleware (FastAPI on :8000)                              │
│                                                             │
│   GET  /                serves static SPA (HTML/JS/CSS)     │
│   POST /api/search      → yt-dlp ytsearch                   │
│   POST /api/download    → background yt-dlp job             │
│   GET  /api/status/:id  → job state                         │
│   POST /api/rescan      → Navidrome startScan               │
│   GET  /api/library/*   → proxy to Navidrome /rest/*        │
│   GET  /api/stream/:id  → proxy to Navidrome /rest/stream   │
│                                                             │
└────────┬───────────────────────────────────────┬────────────┘
         │                                       │
         ▼                                       ▼
   yt-dlp + ffmpeg                       Navidrome (:4533)
   writes to /music                       reads /music
```

Both containers share `/music`. Middleware writes, Navidrome reads.

**Why proxy Navidrome through the middleware?**
- Single origin → no CORS dance in the browser
- Single API key → browser doesn't need Navidrome credentials
- Future Android client can hit the same surface

---

## Stack decisions

| Layer | Choice | Rationale |
|---|---|---|
| Orchestration | Docker Compose | Two services, one file |
| Music server | Navidrome | Subsonic API → reusable by Android clients |
| Middleware | FastAPI + uvicorn | Async, simple, good streaming response support |
| Downloader | yt-dlp + ffmpeg | The only real choice |
| Tagging | mutagen | Direct ID3/Vorbis writes from user-supplied artist+title. **No beets in v1** — MusicBrainz lookup misses too often on YouTube source. |
| Audio format | **Native container, no re-encode** | YouTube source is already lossy Opus/AAC. FLAC re-encode just inflates a lossy file. Keep `.opus` / `.m4a`. |
| Job state | In-memory dict | SQLite later if persistence becomes a real problem |
| Web app | Vanilla HTML + ES modules | No build step, no npm, small Docker image. Revisit if state management becomes painful. |
| Player | HTML5 `<audio>` | All modern browsers handle Opus/AAC/MP3 natively |

---

## File layout on disk

```
/music/
└── {artist}/
    └── {artist} - {title}.{ext}
```

Artist and title come from the **user** at download time (not yt-dlp's unreliable `uploader` field). The web app's download dialog pre-fills these from the YouTube title using a best-effort parse (`" - "` split), but the user can edit before confirming.

---

## Repository layout

```
peel/
├── docker-compose.yml
├── .env.example
├── secrets/
│   └── cookies.txt              (gitignored, mounted into middleware)
├── middleware/
│   ├── Dockerfile
│   ├── requirements.txt
│   ├── main.py
│   ├── config.py
│   ├── auth.py
│   ├── jobs.py
│   ├── routes/
│   │   ├── search.py
│   │   ├── download.py
│   │   ├── library.py           (proxy to Navidrome)
│   │   └── stream.py            (proxy to Navidrome)
│   ├── services/
│   │   ├── ytdlp_service.py
│   │   ├── tagger_service.py
│   │   └── navidrome_client.py
│   └── static/                  (web app lives here)
│       ├── index.html
│       ├── app.js
│       ├── style.css
│       └── modules/
│           ├── api.js
│           ├── player.js
│           ├── search.js
│           └── library.js
└── implementation-plan.md
```

---

## Phase 1 — Server scaffold

**Goal:** docker-compose up, Navidrome reachable, middleware reachable at `/health`.

### Deliverables
- [ ] `docker-compose.yml` with `navidrome` + `middleware` services on a shared network
- [ ] `.env.example` documenting all env vars
- [ ] `middleware/Dockerfile` (python:3.12-slim + ffmpeg + deps)
- [ ] `middleware/main.py` with `/health` endpoint only
- [ ] `secrets/cookies.txt` placeholder (gitignored), mounted at `/app/cookies.txt`
- [ ] Navidrome admin user created, music folder set to `/music`

### Environment variables

| Var | Example | Purpose |
|---|---|---|
| `MUSIC_DIR` | `/srv/peel/music` | Host path mounted into both containers |
| `NAVIDROME_DATA` | `/srv/peel/navidrome-data` | Host path for Navidrome state |
| `NAVIDROME_URL` | `http://navidrome:4533` | Internal URL for middleware → Navidrome |
| `NAVIDROME_USER` | `admin` | Subsonic auth |
| `NAVIDROME_PASS` | (secret) | Subsonic auth |
| `MIDDLEWARE_API_KEY` | (secret) | Required on all `/api/*` requests |
| `YTDLP_COOKIES_FILE` | `/app/cookies.txt` | Optional; enables age/region-locked content |

---

## Phase 2 — Middleware API

**Goal:** all endpoints in place, testable via `curl`, downloads land in `/music` and appear in Navidrome.

### `POST /api/search`
- Body: `{ "query": "string", "limit": 10 }`
- yt-dlp `extract_flat` search, no download
- Returns: `[{ videoId, title, channel, duration, thumbnail }]`

### `POST /api/download`
- Body: `{ "videoId": "...", "artist": "...", "title": "..." }`
- Returns immediately: `{ "jobId": "uuid", "status": "pending" }`
- Background task:
  1. yt-dlp downloads `bestaudio` in native container
  2. Move to `/music/{artist}/{artist} - {title}.{ext}`
  3. Write ID3/Vorbis tags via mutagen (artist, title; embed thumbnail if downloaded)
  4. POST Navidrome `/rest/startScan`
  5. Mark job `done` (or `error` with message)

### `GET /api/status/{jobId}`
- Returns: `{ jobId, status: "pending|downloading|tagging|scanning|done|error", error?: string, file?: string }`

### `POST /api/rescan`
- Triggers Navidrome `/rest/startScan`
- Returns: `{ ok: true }`

### `GET /api/library/{subpath}`
- Proxies to `{NAVIDROME_URL}/rest/{subpath}` with auth params injected server-side
- Used for: `ping`, `getArtists`, `getArtist`, `getAlbum`, `search3`, `getCoverArt`

### `GET /api/stream/{trackId}`
- Proxies `{NAVIDROME_URL}/rest/stream?id={trackId}` with `StreamingResponse`
- Supports `Range` header (pass through) so `<audio>` seeking works

### Auth
- Every `/api/*` endpoint requires `X-API-Key` matching `MIDDLEWARE_API_KEY`
- The static file routes (`/`, `/style.css`, etc.) are unauthenticated — the web app fetches the API key from settings stored in `localStorage`

### Cookies handling
- yt-dlp gets `cookiefile=/app/cookies.txt` when the file exists
- README documents: `yt-dlp --cookies-from-browser firefox --cookies cookies.txt` on a machine with a logged-in YouTube session, then `scp` to `secrets/cookies.txt`
- Refresh roughly every few months when YouTube invalidates the session

### Deliverables
- [ ] All 7 endpoints implemented with `X-API-Key` auth
- [ ] `curl` smoke test: search → download → status reaches `done` → file in `/music` → appears in Navidrome
- [ ] Range request to `/api/stream/...` returns 206 with correct byte range
- [ ] Cookies file optional — public videos work without it

---

## Phase 3 — Web app

**Goal:** one-page SPA at `/` that does search + download + library browse + playback.

### Screens (rendered as views, no router library — `history.pushState` is enough)

1. **Search** — query input, results list (thumbnail / title / channel / duration), per-result "Download" button. Tapping Download opens a small dialog: artist + title (pre-filled from a `" - "` split heuristic), confirm/cancel.
2. **Library** — artists (alphabetical), drill into albums, drill into tracks. Tap track → adds to queue, starts playing.
3. **Jobs** — recent downloads with status chips; polls `/api/status/{id}` every 2s for any non-terminal jobs.
4. **Settings** — middleware URL + API key. Stored in `localStorage`. Ping test button.

### Persistent UI

- **Bottom mini-player**: track title, artist, play/pause, seek bar. Tap to expand to full-screen player.
- **Top tab bar**: Search / Library / Jobs / Settings.

### Player

- Single `<audio>` element managed by `modules/player.js`
- Source: `/api/stream/{trackId}` with `X-API-Key` header — set via `fetch` + `URL.createObjectURL`? **No** — `<audio>` doesn't send custom headers. Two options:
  - **A:** API key as query param (`?key=...`) instead of header for the stream endpoint. Simpler, fine for a home LAN.
  - **B:** Service worker intercepts `<audio>` requests and injects the header.
- Going with **A** for v1. Document the trade-off in the README.

### Deliverables
- [ ] Settings stored in `localStorage`, ping test confirms middleware + Navidrome reachability
- [ ] Search returns results, download dialog confirms metadata, status updates appear in Jobs view
- [ ] Library browses artists → albums → tracks from Navidrome via the proxy
- [ ] Playback works end-to-end including seeking
- [ ] Mini-player persists across view switches

---

## Phase 4 — Polish

- [ ] **Duplicate check**: before download, call `search3` via the library proxy; if exact artist+title match, show "Already in library" and skip
- [ ] **Error states**: middleware down → banner; Navidrome down → library tab shows error; yt-dlp failure → readable error from job status
- [ ] **PWA manifest**: installable on Android home screen, offline shell, app icon
- [ ] **Cookies refresh script**: `scripts/refresh-cookies.sh` to ease the manual step

---

## Phase 5 — Android app (later)

Once the middleware API is stable and the web app works as wanted:

- Native Kotlin + Jetpack Compose
- Media3 `MediaSessionService` (required for Android Auto)
- Calls the same `/api/*` surface — no API changes needed
- Existing Subsonic Android clients are an option for non-Auto use, but a custom client is required for Android Auto

This phase is **out of scope** until Phases 1–4 are working in daily use.

---

## Suggested build order

1. `docker-compose.yml` + Navidrome reachable, middleware `/health` returns 200
2. `POST /api/search` end-to-end (yt-dlp ytsearch, no download)
3. `POST /api/download` + `GET /api/status/:id` — file lands in `/music`, Navidrome scans
4. `GET /api/library/*` and `GET /api/stream/:id` proxies
5. Web app: settings screen + library browse (proves the proxy works)
6. Web app: HTML5 audio player with seeking
7. Web app: search + download flow + jobs view
8. Polish: duplicates, errors, PWA manifest

---

## Open questions to revisit

- **Playlists / album uploads**: a single video that's actually a 60-minute album. Reject? Split via yt-dlp chapter markers? Defer to Phase 4.
- **Multi-user**: single-user assumed throughout. If multiple household members need separate libraries / playback, Navidrome supports multi-user but the middleware API key would need per-user scoping.
- **HTTPS**: assumed deployed behind Tailscale or a reverse proxy that terminates TLS. Middleware itself serves plain HTTP.
