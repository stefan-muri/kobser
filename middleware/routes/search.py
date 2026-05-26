from anyio import to_thread
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from auth import get_current_session
from services import ytdlp_service

router = APIRouter()


class SearchRequest(BaseModel):
    query: str = Field(min_length=1)
    limit: int = Field(default=10, ge=1, le=50)
    source: str = Field(default="youtube", pattern="^(youtube|youtube_music)$")


@router.post("/api/search")
async def search(body: SearchRequest, sess: dict = Depends(get_current_session)):
    return await to_thread.run_sync(
        lambda: ytdlp_service.search(body.query, body.limit, body.source)
    )
