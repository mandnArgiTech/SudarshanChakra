# Farm Event Simulator

Publishes MQTT events (and optional REST alert POSTs) to exercise SudarshanChakra: **alert-service**, **dashboard**, and **Android MQTT**.

## Broker URLs

| Environment | WebSocket URL | Notes |
|---------------|---------------|--------|
| Local RabbitMQ | `ws://localhost:15675/ws` | Requires `rabbitmq_web_mqtt` + port **15675** (see `cloud/rabbitmq/conf.d/`). **Auth:** same as RabbitMQ login (default UI: `admin` / `devpassword123` from `cloud/.env`). |
| Local Mosquitto | `ws://localhost:9001/mqtt` | If using Mosquitto with WS |
| VPS | `ws://<host>:15675/ws` | Expose **15675** on RabbitMQ |

## REST / API gateway (inventory + alerts)

- **API base**: gateway root — e.g. `http://localhost:8080` — **no** `/api/v1` suffix in the field.
- **Dev (`npm run dev`)**: leave API base **empty** to use Vite’s **`/api` → `http://localhost:8080`** proxy (avoids CORS when using `--host`).
- **Requirements for meaningful alerts:** **api-gateway** and **device-service** must be running. Click **Refresh inventory** to load `GET /api/v1/nodes`, `/api/v1/cameras?nodeId=…`, `/api/v1/zones`. Alert MQTT + REST payloads are built from **real zone rows** (camera_id, zone_id, priority, zone_type, target_classes) so PostgreSQL FK checks pass.
- **JWT**: optional; paste token from dashboard login if your gateway requires auth for device or alert APIs.
- **Empty zones:** seed or register data — see `docs/POSTGRES_DOCKER_SQL.md`, `cloud/db/seed_simulator_cameras_zones.sql`, or `scripts/register_camera.sh`.
- **REST 400 on POST /api/v1/alerts:** still means a referenced `camera_id` / `zone_id` / `node_id` is missing from the DB relative to what you sent.

## Run

```bash
cd simulator
npm install
npm run dev
# http://localhost:3001
```

## Docker

With compose profile `dev` (see `cloud/docker-compose.vps.yml`): simulator on **3001**.

```bash
docker compose -f cloud/docker-compose.vps.yml --profile dev up -d simulator
```

Set **API base** in the UI to your public gateway URL (or nginx on **9080** + `/api` routing) — the static Docker build has no Vite proxy.

## Features

- **Alerts tab:** zone cards from device-service (per selected edge node), detection_class from `target_classes` (or free text if none), **Fire alert**, optional **presets** (narrative labels that only set `detection_class` on the **selected** zone when it matches targets).
- Scenario tabs: water, motor, siren, system; custom JSON publish; auto/random/chaos (alerts use loaded zones).
- **Water simulation**: fill / drain / fluctuate (interval publishes to `tank1_sim/water/level`).
- **Sequences**: alert steps reference **zone IDs** present in inventory (e.g. `front_gate`, `pond_safety`); load the matching node first.
- **System → camera online/offline:** uses the **first camera** on the selected node from inventory (no hardcoded `cam-03`).
- Subscribes to `farm/siren/ack` when connected (inbound log lines).
