# Farm Event Simulator

Publishes MQTT events (and optional REST alert POSTs) to exercise SudarshanChakra: **alert-service**, **dashboard**, and **Android MQTT**.

## Broker URLs

| Environment | WebSocket URL | Notes |
|---------------|---------------|--------|
| Local RabbitMQ | `ws://localhost:15675/ws` | Requires `rabbitmq_web_mqtt` + port **15675** (see `cloud/rabbitmq/conf.d/`) |
| Local Mosquitto | `ws://localhost:9001/mqtt` | If using Mosquitto with WS |
| VPS | `ws://<host>:15675/ws` | Expose **15675** on RabbitMQ |

## REST

- **API base**: gateway root, e.g. `http://localhost:8080` (no `/api/v1` suffix).
- **JWT**: optional; paste token from dashboard login to authorize `POST /api/v1/alerts`.

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

## Features

- Scenario tabs (alerts, water, motor, siren, system), custom JSON publish, auto/random/chaos.
- **Water simulation**: fill / drain / fluctuate (interval publishes to `tank1_sim/water/level`).
- **Sequences**: intruder breach, water emergency, fire detection.
- Subscribes to `farm/siren/ack` when connected (inbound log lines).
