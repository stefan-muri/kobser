from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request

from db import init_db
from routes import artist, download, import_playlist, library, search, stats, stream, tracks
from routes import auth as auth_routes


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


app = FastAPI(title="Kobser Middleware", lifespan=lifespan)


class NoCacheStaticMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)
        path = request.url.path
        if path.startswith("/") and any(path.endswith(ext) for ext in (".js", ".css", ".html")):
            response.headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
        return response


# Content-Security-Policy tuned for the built SPA: external ESM only (no inline
# scripts), Google Fonts, and remote thumbnail images from search results. Audio
# is proxied same-origin through /api/stream, so media-src stays 'self'.
_CSP = (
    "default-src 'self'; "
    "script-src 'self'; "
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
    "font-src 'self' https://fonts.gstatic.com; "
    "img-src 'self' data: https:; "
    "media-src 'self' blob:; "
    "connect-src 'self'; "
    "frame-ancestors 'none'; "
    "base-uri 'self'; "
    "object-src 'none'"
)


class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)
        response.headers.setdefault("Content-Security-Policy", _CSP)
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("X-Frame-Options", "DENY")
        # no-referrer stops the ?session= token leaking via the Referer header.
        response.headers.setdefault("Referrer-Policy", "no-referrer")
        return response


app.add_middleware(NoCacheStaticMiddleware)
app.add_middleware(SecurityHeadersMiddleware)

app.include_router(auth_routes.router)
app.include_router(search.router)
app.include_router(artist.router)
app.include_router(download.router)
app.include_router(library.router)
app.include_router(stream.router)
app.include_router(tracks.router)
app.include_router(stats.router)
app.include_router(import_playlist.router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


# Static SPA last so /api/* and /health win the route table.
app.mount("/", StaticFiles(directory="frontend/dist", html=True), name="static")
