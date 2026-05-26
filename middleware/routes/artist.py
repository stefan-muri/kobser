from anyio import to_thread
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from auth import get_current_session
from services import ytdlp_service

router = APIRouter()


class ArtistSearchRequest(BaseModel):
    query: str = Field(min_length=1)
    limit: int = Field(default=10, ge=1, le=50)


@router.post("/api/search/artists")
async def search_artists(body: ArtistSearchRequest, _: dict = Depends(get_current_session)):
    return await to_thread.run_sync(
        lambda: ytdlp_service.search_artists(body.query, body.limit)
    )


@router.get("/api/artist/{channel_id}")
async def get_artist(channel_id: str, _: dict = Depends(get_current_session)):
    return await to_thread.run_sync(
        lambda: ytdlp_service.get_artist(channel_id)
    )


@router.get("/api/artist/{channel_id}/songs")
async def get_artist_songs(channel_id: str, _: dict = Depends(get_current_session)):
    return await to_thread.run_sync(
        lambda: ytdlp_service.get_artist_songs(channel_id)
    )


@router.get("/api/album/{browse_id}")
async def get_album(browse_id: str, _: dict = Depends(get_current_session)):
    return await to_thread.run_sync(
        lambda: ytdlp_service.get_album(browse_id)
    )
