from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from auth import get_current_session
from db import create_session, delete_session
from services.navidrome_client import get_user_libraries, make_credentials, ping

router = APIRouter()


class LoginRequest(BaseModel):
    username: str
    password: str


@router.post("/api/login")
async def login(body: LoginRequest):
    # Derive a reusable Subsonic (salt, token) from the password and verify it
    # with a ping. The cleartext password is used only here and never stored.
    salt, token = make_credentials(body.password)
    try:
        r = await ping(body.username, salt, token)
        if r.get("status") != "ok":
            raise HTTPException(status_code=401, detail="invalid credentials")
    except HTTPException:
        raise
    except Exception:
        # Deliberately don't chain the underlying error — a failed ping is always
        # surfaced to the client as a generic auth failure.
        raise HTTPException(status_code=401, detail="invalid credentials") from None

    # Resolve the user's library path once (needs the native password login) and
    # persist that instead of the password, so downloads can route to it later.
    libs = await get_user_libraries(body.username, body.password)
    library_path = libs[0]["path"] if libs else None

    sid = create_session(body.username, salt, token, library_path)
    return {"sessionId": sid, "username": body.username}


@router.post("/api/logout")
async def logout(sess: dict = Depends(get_current_session)):
    delete_session(sess["id"])
    return {"ok": True}


@router.get("/api/me")
async def me(sess: dict = Depends(get_current_session)):
    return {"username": sess["username"]}
