import asyncio
from anyio import to_thread
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from auth import get_current_session
from services import ytdlp_service

router = APIRouter()


class SearchRequest(BaseModel):
    query: str = Field(min_length=1)
    limit: int = Field(default=10, ge=1, le=50)
    source: str = Field(default="youtube_music", pattern="^(youtube|youtube_music)$")


@router.post("/api/search")
async def search(body: SearchRequest, sess: dict = Depends(get_current_session)):
    if body.source == "youtube_music":
        songs, artists = await asyncio.gather(
            to_thread.run_sync(lambda: ytdlp_service.search(body.query, body.limit, body.source)),
            to_thread.run_sync(lambda: ytdlp_service.search_artists(body.query, 3)),
        )
        return {"songs": songs, "artists": artists}
    songs = await to_thread.run_sync(
        lambda: ytdlp_service.search(body.query, body.limit, body.source)
    )
    return {"songs": songs, "artists": []}
