# G-08: camera_sync.py — Edge ↔ Backend Camera Config Sync

## Status
NOT DONE — `cameras.json` is static. Cameras registered via dashboard/Android are stored in the database but edge never picks them up.

## File to CREATE

### `edge/camera_sync.py`
```python
"""
camera_sync.py — Periodically syncs camera configuration from backend API.

If the remote camera list differs from local cameras.json, updates the file
and signals the pipeline to restart affected grabbers.

Runs as a daemon thread started from farm_edge_node.py.
"""

import json
import logging
import os
import threading
import time

import requests

log = logging.getLogger("camera_sync")

DEFAULT_INTERVAL = 300  # 5 minutes
CONFIG_PATH = os.getenv("CAMERA_CONFIG", "/app/config/cameras.json")


class CameraSync:
    """Synchronise local cameras.json with the backend device-service API."""

    def __init__(
        self,
        api_base: str,
        node_id: str,
        auth_token: str = "",
        config_path: str = CONFIG_PATH,
        interval: int = DEFAULT_INTERVAL,
        on_change=None,
    ):
        self.api_base = api_base.rstrip("/")
        self.node_id = node_id
        self.auth_token = auth_token
        self.config_path = config_path
        self.interval = interval
        self.on_change = on_change  # Callback: def on_change(new_cameras: list)
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        self._thread = threading.Thread(target=self._loop, name="camera-sync", daemon=True)
        self._thread.start()
        log.info("Camera sync started (every %ds, node=%s)", self.interval, self.node_id)

    def stop(self):
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=10)

    def _loop(self):
        while not self._stop.is_set():
            try:
                self._sync()
            except Exception as e:
                log.warning("Camera sync failed: %s", e)
            self._stop.wait(self.interval)

    def _sync(self):
        url = f"{self.api_base}/api/v1/cameras?nodeId={self.node_id}"
        headers = {}
        if self.auth_token:
            headers["Authorization"] = f"Bearer {self.auth_token}"

        resp = requests.get(url, headers=headers, timeout=15)
        if resp.status_code != 200:
            log.warning("Camera sync API returned %d", resp.status_code)
            return

        remote_cameras = resp.json()
        local_cameras = self._load_local()

        # Compare by converting both to sorted JSON for deterministic diff
        remote_sorted = json.dumps(sorted(remote_cameras, key=lambda c: c.get("id", "")), sort_keys=True)
        local_sorted = json.dumps(sorted(local_cameras, key=lambda c: c.get("id", "")), sort_keys=True)

        if remote_sorted != local_sorted:
            log.info(
                "Camera config changed: %d remote vs %d local cameras — updating",
                len(remote_cameras), len(local_cameras),
            )
            self._save_local(remote_cameras)
            if self.on_change:
                self.on_change(remote_cameras)
        else:
            log.debug("Camera config unchanged (%d cameras)", len(local_cameras))

    def _load_local(self) -> list:
        if not os.path.isfile(self.config_path):
            return []
        try:
            with open(self.config_path) as f:
                data = json.load(f)
            # Handle both formats: {"cameras": [...]} or [...]
            if isinstance(data, dict) and "cameras" in data:
                return data["cameras"]
            if isinstance(data, list):
                return data
            return []
        except Exception:
            return []

    def _save_local(self, cameras: list):
        # Preserve the existing file format
        try:
            with open(self.config_path) as f:
                existing = json.load(f)
        except Exception:
            existing = {}

        if isinstance(existing, dict):
            existing["cameras"] = cameras
            output = existing
        else:
            output = {"cameras": cameras}

        tmp_path = self.config_path + ".tmp"
        with open(tmp_path, "w") as f:
            json.dump(output, f, indent=2)
        os.replace(tmp_path, self.config_path)
        log.info("Saved %d cameras to %s", len(cameras), self.config_path)

    def force_sync(self):
        """Trigger an immediate sync (called from MQTT command handler)."""
        threading.Thread(target=self._sync, daemon=True).start()
```

## File to MODIFY

### `edge/farm_edge_node.py`

After existing initialisation, start the camera sync:
```python
from camera_sync import CameraSync

# After pipeline start, add:
camera_sync = CameraSync(
    api_base=os.getenv("API_BASE", "http://localhost:8080"),
    node_id=os.getenv("NODE_ID", "edge-node-a"),
    auth_token=os.getenv("EDGE_AUTH_TOKEN", ""),
    on_change=lambda cams: log.info("Camera config updated, restart pipeline for %d cameras", len(cams)),
)
camera_sync.start()
```

## Verification
```bash
# Register a new camera via API
curl -X POST http://localhost:8080/api/v1/cameras \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"id":"cam-new","nodeId":"edge-node-a","name":"New Camera","rtspUrl":"rtsp://test:test@192.168.1.99:554/stream2","fpsTarget":2.0}'

# Wait 5 minutes (or set CAMERA_SYNC_INTERVAL=30 for testing)
# Check edge logs
docker logs edge-ai 2>&1 | grep "Camera config changed\|Saved.*cameras"
# Expected: "Camera config changed: 3 remote vs 2 local cameras — updating"

# Verify cameras.json updated
docker exec edge-ai cat /app/config/cameras.json | jq '.cameras | length'
# Expected: 3 (or however many cameras now exist)
```
