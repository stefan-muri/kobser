import httpx
from fastapi import APIRouter, Depends, Request, Response

from auth import get_current_session
from config import NAVIDROME_URL
from services.navidrome_client import auth_params

router = APIRouter()


@router.get("/api/library/{subpath:path}")
async def library_proxy(
    subpath: str, request: Request, sess: dict = Depends(get_current_session)
):
    # Strip our auth params from forwarded query; auth_params() wins
    forwarded = {
        k: v
        for k, v in request.query_params.items()
        if k not in ("session", "key", "u", "t", "s", "v", "c", "f")
    }
    params = {**forwarded, **auth_params(sess["username"], sess["password"])}
    async with httpx.AsyncClient(timeout=30) as client:
        upstream = await client.get(
            f"{NAVIDROME_URL}/rest/{subpath}",
            params=params,
        )
    return Response(
        content=upstream.content,
        status_code=upstream.status_code,
        media_type=upstream.headers.get("content-type"),
    )
