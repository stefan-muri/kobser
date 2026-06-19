from fastapi import Header, HTTPException, Query

from db import get_session


def get_current_session(
    x_session_id: str | None = Header(default=None),
    session: str | None = Query(default=None),
) -> dict:
    """Returns the session dict {id, username, salt, token, library_path, is_admin, expires_at}."""
    sid = x_session_id or session
    if not sid:
        raise HTTPException(status_code=401, detail="not authenticated")
    s = get_session(sid)
    if s is None:
        raise HTTPException(status_code=401, detail="session expired or invalid")
    return s
