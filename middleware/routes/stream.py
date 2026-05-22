import httpx
from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from auth import get_current_session
from config import NAVIDROME_URL
from services.navidrome_client import auth_params

router = APIRouter()

PASSTHROUGH_HEADERS = ("content-type", "content-length", "accept-ranges", "content-range")


@router.get("/api/stream/{track_id}")
async def stream(
    track_id: str, request: Request, sess: dict = Depends(get_current_session)
):
    params = {"id": track_id, **auth_params(sess["username"], sess["password"])}
    upstream_headers = {}
    if r := request.headers.get("range"):
        upstream_headers["Range"] = r

    client = httpx.AsyncClient(timeout=None)
    upstream_req = client.build_request(
        "GET",
        f"{NAVIDROME_URL}/rest/stream",
        params=params,
        headers=upstream_headers,
    )
    upstream = await client.send(upstream_req, stream=True)

    async def relay():
        try:
            async for chunk in upstream.aiter_raw():
                yield chunk
        finally:
            await upstream.aclose()
            await client.aclose()

    response_headers = {
        h: upstream.headers[h] for h in PASSTHROUGH_HEADERS if h in upstream.headers
    }

    return StreamingResponse(
        relay(),
        status_code=upstream.status_code,
        headers=response_headers,
    )
