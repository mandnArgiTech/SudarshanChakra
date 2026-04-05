# G-08: Edge ↔ backend camera config sync (`camera_sync.py`)

## Status
**COMPLETE (baseline)** — [`edge/camera_sync.py`](edge/camera_sync.py) pulls cameras from device-service and writes `CONFIG_DIR/cameras.json`. [`farm_edge_node.py`](edge/farm_edge_node.py) can run sync on a background thread when `CAMERA_SYNC_ENABLED=true`. **Inference still does not hot-reload cameras**; restart the edge process after `cameras.json` changes (see [AGENTS.md](../../AGENTS.md)).

## Implementation (authoritative)

| Piece | Location |
|-------|----------|
| Sync logic | [`edge/camera_sync.py`](edge/camera_sync.py) — `run_camera_sync()`, `fetch_cameras()`, `map_api_camera_to_edge()`, `build_cameras_document()`, `write_cameras_json()` |
| HTTP | Stdlib **`urllib`** (no `requests` dependency) |
| Periodic run | [`farm_edge_node.py`](edge/farm_edge_node.py) — thread when `CAMERA_SYNC_ENABLED` is true; interval `CAMERA_SYNC_INTERVAL_SEC` (default **900**); first run after **15s**; loop uses `max(60, interval)` seconds between runs |
| Unit tests | [`edge/tests/test_camera_sync.py`](edge/tests/test_camera_sync.py) |

## Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DEVICE_SERVICE_URL` | Yes | Base URL including **`/api/v1`** (e.g. `http://api-gateway:8080/api/v1` or `http://device-service:8082/api/v1`). |
| `CAMERA_SYNC_TOKEN` | Yes | JWT `Bearer` for `GET /cameras?nodeId=…` (must not be empty or sync skips). |
| `EDGE_NODE_ID` or `NODE_ID` | No | Query `nodeId` (default `edge-node-a`). |
| `CONFIG_DIR` | No | Directory for `cameras.json` (default `/app/config`). |
| `CAMERA_SYNC_ENABLED` | For daemon | Set `true` / `1` / `yes` on edge to enable the thread in `farm_edge_node`. |
| `CAMERA_SYNC_INTERVAL_SEC` | No | Seconds between sync attempts (default `900`; effective minimum **60** in `farm_edge_node`). |

## API contract

- **GET** `/api/v1/cameras?nodeId=<EDGE_NODE_ID>` — returns JSON array of cameras (device-service). Requires authenticated JWT with access to that tenant’s data; `getCamerasByNodeId` uses `@PreAuthorize('PERMISSION_cameras:view')`.

## Behavior notes

- **Diff-before-write:** If the normalized `cameras` document matches the existing file, sync **skips the write** and returns `False` (no “updated” log spam from `farm_edge_node`). See `read_cameras_document_from_disk` in [`camera_sync.py`](edge/camera_sync.py).
- **CLI (`python -m camera_sync`):** Exits **0** after a successful run (whether the file changed or not); **1** if env missing, fetch fails, or other exception.
- **Empty API list** writes `{"cameras":[]}` — can clear local config; ensure that matches ops expectations.
- **JWT expiry:** Sync fails until the token is refreshed; use a rotation strategy suitable for unmanned edge nodes.
- **Networking:** From inside Docker, use a hostname the edge container can resolve (not host `localhost` unless using host network).

## Verification

```bash
# Edge container env (example)
# DEVICE_SERVICE_URL=http://host.docker.internal:8080/api/v1
# CAMERA_SYNC_TOKEN=<JWT with cameras:view for the farm>
# EDGE_NODE_ID=edge-node-a
# CAMERA_SYNC_ENABLED=true
# CAMERA_SYNC_INTERVAL_SEC=60

# After interval + first 15s delay, logs may show:
docker logs <edge-container> 2>&1 | grep -E "camera_sync:|Camera sync thread"

# Confirm file
docker exec <edge-container> cat /app/config/cameras.json | head
```

Manual one-shot (from `edge/`):

```bash
DEVICE_SERVICE_URL=http://localhost:8080/api/v1 CAMERA_SYNC_TOKEN=$TOKEN \\
  EDGE_NODE_ID=edge-node-a CONFIG_DIR=./config python3 -m camera_sync
```

## Historical note

An older story draft proposed a **`CameraSync` class** using **`requests`**, env names `API_BASE` / `EDGE_AUTH_TOKEN`, and **`on_change` pipeline hooks**. The repo uses the **functional** module above; that snippet is **not** the source of truth.

## Follow-ups (out of scope for G-08 baseline)

- Hot-reload inference pipeline when `cameras.json` changes (new grabbers without full restart).
- Long-lived device / service credentials for edge.
- MQTT-triggered forced sync.
