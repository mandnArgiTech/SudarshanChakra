"""
ONVIF PTZ position helpers (Suite 4). Optional dependency: onvif-zeep / onvif.

Install on the farm runner only: pip install onvif-zeep
"""
from __future__ import annotations

from typing import Any, Dict, Optional


def onvif_available() -> bool:
    try:
        from onvif import ONVIFCamera  # noqa: F401

        return True
    except ImportError:
        return False


def get_ptz_position(host: str, port: int, user: str, password: str) -> Optional[Dict[str, Any]]:
    """
    Return pan/tilt/zoom snapshot if ONVIF is available; else None.
    """
    if not onvif_available():
        return None
    try:
        from onvif import ONVIFCamera

        cam = ONVIFCamera(host, port, user, password)
        ptz = cam.create_ptz_service()
        media = cam.create_media_service()
        profiles = media.GetProfiles()
        if not profiles:
            return None
        token = profiles[0].token
        status = ptz.GetStatus({"ProfileToken": token})
        return {
            "pan": float(status.Position.PanTilt.x),
            "tilt": float(status.Position.PanTilt.y),
            "zoom": float(status.Position.Zoom.x),
        }
    except Exception:
        return None


def assert_ptz_moved(before: Dict[str, float], after: Dict[str, float], axis: str = "pan", min_delta: float = 0.001) -> bool:
    if axis not in before or axis not in after:
        return False
    return abs(after[axis] - before[axis]) >= min_delta
