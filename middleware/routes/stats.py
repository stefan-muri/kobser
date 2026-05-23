import shutil

import psutil
from fastapi import APIRouter, Depends

from auth import get_current_session
from config import MUSIC_DIR

router = APIRouter()


@router.get("/api/stats", dependencies=[Depends(get_current_session)])
def get_stats():
    try:
        du = shutil.disk_usage(MUSIC_DIR)
        disk = {"used": du.used, "total": du.total}
    except Exception:
        disk = {"used": 0, "total": 0}

    mem = psutil.virtual_memory()
    cpu = psutil.cpu_percent(interval=0.2)

    return {
        "disk": disk,
        "ram": {"used": mem.used, "total": mem.total},
        "cpu_percent": cpu,
    }
