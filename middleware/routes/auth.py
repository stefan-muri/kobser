from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from auth import get_current_session
from db import create_session, delete_session
from services.navidrome_client import ping

router = APIRouter()


class LoginRequest(BaseModel):
    username: str
    password: str


@router.post("/api/login")
async def login(body: LoginRequest):
    try:
        r = await ping(body.username, body.password)
        if r.get("status") != "ok":
            raise HTTPException(status_code=401, detail="invalid credentials")
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=401, detail="invalid credentials")
    sid = create_session(body.username, body.password)
    return {"sessionId": sid, "username": body.username}


@router.post("/api/logout")
async def logout(sess: dict = Depends(get_current_session)):
    delete_session(sess["id"])
    return {"ok": True}


@router.get("/api/me")
async def me(sess: dict = Depends(get_current_session)):
    return {"username": sess["username"]}
