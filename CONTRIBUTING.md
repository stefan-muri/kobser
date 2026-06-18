# Contributing to kobser

Thanks for your interest! kobser is a self-hosted music app — a FastAPI
middleware, a Svelte web app, and a Kotlin/Compose Android app, all on top of
Navidrome.

## Project layout

```
middleware/          FastAPI backend (Python)
  main.py            app entry
  routes/            HTTP endpoints
  services/          yt-dlp, ytmusicapi, Navidrome, tagging
  db.py              SQLite sessions + download history
  frontend/          Svelte web app (Vite)
android/             Android app (Kotlin, Jetpack Compose, Media3)
docker-compose.yml   the stack (Navidrome + middleware)
```

## Dev setup

**Backend + web app (Docker):**
```bash
cp .env.example .env      # set MUSIC_DIR
docker compose up -d --build
```

**Web frontend (live reload):**
```bash
cd middleware/frontend
npm install
npm run dev               # Vite dev server, proxies /api to the running middleware
```

**Android:** open `android/` in Android Studio. Requires **JDK 17** (Studio's
bundled JBR works). Don't commit a machine-specific `org.gradle.java.home`.

**Backend tests & lint:**
```bash
cd middleware
pip install -r requirements-dev.txt
ruff check .
pytest
```
CI runs both on any change under `middleware/`.

## Guidelines

- **Match the surrounding style.** Python is plain FastAPI + stdlib-ish; the web
  app is idiomatic Svelte; Android is MVVM (`XxxScreen` + `XxxViewModel`, Hilt).
- **Keep secrets out of the repo.** Never commit `.env`, real `cookies.txt`,
  keystores, or APKs (all gitignored).
- **Small, focused PRs** with a clear description. Note any config/env changes.
- **Don't add tracking, telemetry, or hardcoded credentials.**

## Reporting bugs / ideas

Open an issue with steps to reproduce (and your deployment details for bugs).
For security issues, see [SECURITY.md](SECURITY.md) — report privately.

## License

By contributing, you agree your contributions are licensed under the project's
[GPL-3.0](LICENSE).
