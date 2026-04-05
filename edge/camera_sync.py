#!/usr/bin/env python3
"""
Pull camera definitions from device-service and write ``cameras.json`` for the edge node.

Environment:
  DEVICE_SERVICE_URL  — Base URL (e.g. http://device-service:8082 or http://localhost:8080/api/v1
    via gateway). Trailing slashes stripped. Path should include /api/v1 if your gateway mounts it.
  EDGE_NODE_ID        — Node id to filter cameras (query param nodeId).
  CAMERA_SYNC_TOKEN   — JWT Bearer token for authenticated GET.
  CONFIG_DIR          — Directory for cameras.json (default /app/config).

Alternatively run manually or from cron:
  CAMERA_SYNC_TOKEN=... DEVICE_SERVICE_URL=http://localhost:8080/api/v1 \\
    EDGE_NODE_ID=edge-node-a CONFIG_DIR=./config python3 -m camera_sync

The inference pipeline does not hot-reload cameras; restart the edge container or process after sync,
or rely on periodic restarts. Optional ``CAMERA_SYNC_ENABLED=true`` in ``farm_edge_node`` runs a
background thread on ``CAMERA_SYNC_INTERVAL_SEC`` (default 900).
"""

from __future__ import annotations

import json
import logging
import os
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, List

log = logging.getLogger("camera_sync")


def _env(name: str, default: str = "") -> str:
    return (os.getenv(name) or default).strip()


def map_api_camera_to_edge(cam: Dict[str, Any]) -> Dict[str, Any]:
    """Map device-service JSON (camelCase) to ``cameras.json`` camera object."""
    cid = str(cam.get("id") or "")
    name = str(cam.get("name") or cid)
    fps = float(cam.get("fpsTarget") if cam.get("fpsTarget") is not None else 2.0)
    enabled = cam.get("enabled")
    if enabled is None:
        enabled = True
    st = (cam.get("sourceType") or "rtsp").lower()
    source_url = cam.get("sourceUrl")
    if source_url is not None:
        source_url = str(source_url).strip() or None
    rtsp = str(cam.get("rtspUrl") or "").strip()

    if st in ("file", "http"):
        primary = (source_url or rtsp or "").strip()
        edge_rtsp = rtsp or primary
    else:
        primary = rtsp or (source_url or "").strip()
        edge_rtsp = primary

    row: Dict[str, Any] = {
        "id": cid,
        "name": name,
        "rtsp_url": edge_rtsp,
        "fps": fps,
        "enabled": bool(enabled),
        "source_type": st,
    }
    if source_url:
        row["source_url"] = source_url
    # ONVIF passthrough (optional; edge pipeline may use later)
    for key, api_key in (
        ("onvif_host", "onvifHost"),
        ("onvif_port", "onvifPort"),
        ("onvif_user", "onvifUser"),
        ("onvif_pass", "onvifPass"),
    ):
        v = cam.get(api_key)
        if v is not None and v != "":
            row[key] = v
    return row


def fetch_cameras(base_url: str, node_id: str, token: str) -> List[Dict[str, Any]]:
    """GET /cameras?nodeId=… with Bearer token."""
    root = base_url.rstrip("/")
    q = urllib.parse.urlencode({"nodeId": node_id})
    url = f"{root}/cameras?{q}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        log.error("camera_sync HTTP %s: %s", e.code, e.reason)
        raise
    except urllib.error.URLError as e:
        log.error("camera_sync URL error: %s", e.reason)
        raise
    data = json.loads(body)
    if not isinstance(data, list):
        raise ValueError("expected JSON array of cameras")
    return data


def build_cameras_document(api_cameras: List[Dict[str, Any]]) -> Dict[str, Any]:
    cameras = [map_api_camera_to_edge(c) for c in api_cameras if c.get("id")]
    return {"cameras": cameras}


def write_cameras_json(config_dir: str, doc: Dict[str, Any]) -> str:
    os.makedirs(config_dir, exist_ok=True)
    path = os.path.join(config_dir, "cameras.json")
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")
    os.replace(tmp, path)
    return path


def _canonical_document_json(doc: Dict[str, Any]) -> str:
    """Stable JSON for comparing logical camera config (key order independent)."""
    return json.dumps(doc, sort_keys=True, separators=(",", ":"))


def read_cameras_document_from_disk(config_dir: str) -> Dict[str, Any] | None:
    """
    Load ``cameras.json`` as ``{"cameras": [...]}`` or None if missing / invalid.
    """
    path = os.path.join(config_dir, "cameras.json")
    if not os.path.isfile(path):
        return None
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        return None
    if isinstance(data, dict) and "cameras" in data and isinstance(data["cameras"], list):
        return {"cameras": data["cameras"]}
    if isinstance(data, list):
        return {"cameras": data}
    return None


def run_camera_sync() -> bool:
    """
    Read env, fetch cameras, write ``CONFIG_DIR/cameras.json`` if content changed.

    Returns True if the file was written; False if skipped (unchanged, missing env, or error).
    """
    base = _env("DEVICE_SERVICE_URL")
    node_id = _env("EDGE_NODE_ID") or _env("NODE_ID", "edge-node-a")
    token = _env("CAMERA_SYNC_TOKEN")
    config_dir = _env("CONFIG_DIR", "/app/config")

    if not base:
        log.warning("camera_sync: DEVICE_SERVICE_URL not set, skipping")
        return False
    if not token:
        log.warning("camera_sync: CAMERA_SYNC_TOKEN not set, skipping")
        return False

    api_list = fetch_cameras(base, node_id, token)
    doc = build_cameras_document(api_list)
    existing = read_cameras_document_from_disk(config_dir)
    if existing is not None and _canonical_document_json(existing) == _canonical_document_json(doc):
        log.debug("camera_sync: no changes, skipping write")
        return False
    path = write_cameras_json(config_dir, doc)
    log.info("camera_sync: wrote %d cameras to %s", len(doc["cameras"]), path)
    return True


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    if not _env("DEVICE_SERVICE_URL") or not _env("CAMERA_SYNC_TOKEN"):
        log.error("camera_sync: DEVICE_SERVICE_URL and CAMERA_SYNC_TOKEN required")
        raise SystemExit(1)
    try:
        run_camera_sync()
    except Exception:
        log.exception("camera_sync failed")
        raise SystemExit(1)
    raise SystemExit(0)
