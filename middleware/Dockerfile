# Stage 1: build frontend (Vite + Svelte + Tailwind)
FROM node:20-alpine AS assets
WORKDIR /assets

COPY frontend/package.json ./
RUN npm install

COPY frontend/ ./frontend/
RUN cd frontend && npm run build

# Stage 2: Python runtime
#
# Debian slim (glibc) rather than Alpine (musl): yt-dlp now needs an external
# JavaScript runtime to solve YouTube's player-signature ("nsig") challenge, and
# the Deno binary is glibc-only. slim also ships manylinux wheels for psutil /
# uvicorn[standard], so no compiler is needed.
FROM python:3.12-slim

# Deno — the JS runtime yt-dlp auto-detects on PATH for YouTube extraction.
COPY --from=denoland/deno:bin /deno /usr/local/bin/deno

# gosu lets the entrypoint drop from root to the unprivileged 'app' user after
# fixing bind-mount ownership.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates gosu \
    && rm -rf /var/lib/apt/lists/*

# Unprivileged user the app actually runs as (-m gives it a writable $HOME for
# yt-dlp / ytmusicapi caches).
RUN groupadd -g 1000 app \
    && useradd -u 1000 -g app -m -d /home/app -s /usr/sbin/nologin app

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
COPY --from=assets /assets/frontend/dist/ ./frontend/dist/

# Mount points exist so the entrypoint can chown them on first run.
RUN mkdir -p /app/data /app/secrets /music \
    && cp /app/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh \
    && chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8000

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
