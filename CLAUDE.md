# CLAUDE.md

Guidance for working in this repo. For human-facing setup/feature docs see
[README.md](README.md) and [CONTRIBUTING.md](CONTRIBUTING.md); this file
captures the things that aren't obvious from the code.

## What this is

Self-hosted music app on top of [Navidrome](https://www.navidrome.org/):

- `middleware/` — FastAPI backend (Python 3.12). Search/download via `yt-dlp` +
  `ytmusicapi`, tagging, and a proxy to Navidrome's Subsonic API.
- `middleware/frontend/` — Svelte + Vite + Tailwind web app, built into
  `frontend/dist/` and served by the middleware at `/`.
- `android/` — Kotlin / Jetpack Compose / Media3 app with Android Auto. See
  [NOTES-android.md](NOTES-android.md).

> **Naming:** the project is now **kobser** (formerly "Peel"). You'll still see
> `peel` in older paths/strings — both refer to this app.

## Backend layout

Request flow is **route → service → external system**, with shared state in two
modules:

- `routes/*.py` — one router per area (`auth`, `search`, `artist`, `download`,
  `import_playlist`, `library`, `stream`, `tracks`, `stats`). Register new
  routers in `main.py` (the static SPA is mounted **last** so `/api/*` and
  `/health` win the route table — keep it last).
- `services/*.py` — `ytdlp_service`, `spotify_client`, `navidrome_client`,
  `tagger_service`. External integrations live here, not in routes.
- `db.py` — SQLite (`sessions` + `downloads`). Schema is created/migrated in
  `init_db()` via `CREATE TABLE IF NOT EXISTS` and additive
  `ALTER TABLE`; follow that pattern for new columns (no migration framework).
- `jobs.py` — in-memory download job tracker (thread-locked), mirrored to the
  `downloads` table. Background download work goes through here.
- `auth.py` — `get_current_session` dependency; auth is a per-user Navidrome
  session, not app accounts. The `sessions` row stores a derived Subsonic
  `(salt, token)` pair + the resolved `library_path` — **never** the cleartext
  password (derived once at login via `make_credentials`). Subsonic calls use
  the stored `(salt, token)`; `get_user_libraries` is the one password-based
  call and runs only at login.

## Running it

**Full stack (Docker):**
```bash
docker compose up -d --build
```
`docker-compose.override.yml` is local-dev-only (gitignored from the server
deploy): it builds the image locally and bind-mounts the Python source dirs, so
`docker compose restart middleware` picks up backend edits **without a rebuild**.
It deliberately does *not* mount `/app/frontend`, so frontend changes still need
a rebuild (or use the Vite dev server below).

**Web frontend (live reload):**
```bash
cd middleware/frontend && npm install && npm run dev
```
Vite proxies `/api` to the running middleware.

**Android:** open `android/` in **Android Studio** with **JDK 17** (Studio's
bundled JBR). Do **not** try to build from the CLI with `./gradlew` — the host
has Java 25 and the Gradle build fails there. Don't commit a machine-specific
`org.gradle.java.home`.

## Conventions

- **Match surrounding style.** Backend is plain FastAPI + stdlib (no ORM, no
  heavy abstractions). Web app is idiomatic Svelte. Android is MVVM
  (`XxxScreen` + `XxxViewModel`, Hilt).
- **`yt-dlp` is intentionally unpinned** (`requirements.txt`) — YouTube changes
  often. Rebuild the image to pick up fixes rather than pinning.
- The cookies secret mount is **not** read-only: yt-dlp rewrites the cookies
  file to persist refreshed cookies. Don't re-add `:ro`.
- yt-dlp needs Deno on PATH (YouTube `nsig` challenge) — that's why the image is
  Debian slim, not Alpine. Don't switch the base image to musl.
- **Tests & lint (backend):** from `middleware/`, `pip install -r
  requirements-dev.txt`, then `ruff check .` and `pytest`. Tests cover the pure
  logic (yt-dlp error taxonomy, filename sanitisation, match-normalisation);
  there's no integration suite, so still verify end-to-end behaviour by running
  the stack. CI runs ruff + pytest on `middleware/**` changes.

## Secrets & data (never commit)

- `secrets/` — cookies files, mounted to `/app/secrets`.
- `data/` — Navidrome data, music, and the SQLite DB (`data/kobser/kobser.db`).
- `.env` — `MUSIC_DIR`, ports, `NAVIDROME_URL`, `YTDLP_COOKIES_FILE`.
- Never commit `.env`, real `cookies.txt`, keystores, or APKs.

## Working agreement

- **Do not commit or push without explicit approval.**
