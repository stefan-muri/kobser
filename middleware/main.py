from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from db import init_db
from routes import auth as auth_routes
from routes import download, library, search, stats, stream, tracks


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


app = FastAPI(title="Peel Middleware", lifespan=lifespan)

app.include_router(auth_routes.router)
app.include_router(search.router)
app.include_router(download.router)
app.include_router(library.router)
app.include_router(stream.router)
app.include_router(tracks.router)
app.include_router(stats.router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


# Static SPA last so /api/* and /health win the route table.
app.mount("/", StaticFiles(directory="static", html=True), name="static")
