# Quick Start: Local Backend + Edge Deployment

**Goal:** Deploy backend services and edge AI on the same machine, test with Tapo C110 camera (192.168.68.56).

**📖 Confused? Read `START_HERE.md` first!**

## Prerequisites Check

```bash
# Check Java 21
java -version  # Should show 21.x

# Check Python 3.10+
python3 --version

# Check Docker (for PostgreSQL/RabbitMQ)
docker ps
```

## Option 1: Automated Script (Recommended)

```bash
# Full deployment (infra + backend + edge)
./scripts/deploy_local.sh

# Or step-by-step
./scripts/deploy_local.sh --skip-backend --skip-edge  # Only infra
./scripts/deploy_local.sh --skip-infra --skip-edge    # Only backend
./scripts/deploy_local.sh --skip-infra --skip-backend # Only edge
```

## Option 2: Manual Steps

### 1. Start Infrastructure

```bash
./setup_and_build_all.sh deploy-infra
```

`setup_and_build_all.sh --menu` is exclusive (other args on the same command line are ignored). `deploy-backend` appends to `logs/service.pids` and removes stale backend port lines; use `RESET_SERVICE_PIDS=1` to clear the file first.

This starts:
- PostgreSQL on `localhost:5432`
- RabbitMQ on `localhost:5672` (AMQP) and `localhost:1883` (MQTT)

### 2. Update Nginx (if port 9080 is in use)

Edit your nginx config (e.g., `/etc/nginx/sites-available/default`):

```nginx
server {
    listen 9080;
    server_name localhost;

    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws/ {
        proxy_pass http://localhost:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Reload: `sudo nginx -t && sudo systemctl reload nginx`

### 3. Start Backend

```bash
./setup_and_build_all.sh deploy-backend
```

Services start on:
- `8080` (API Gateway) → proxied by nginx on `9080`
- `8081` (Alert Service)
- `8082` (Device Service)
- `8083` (Auth Service)
- `8084` (Siren Service)

### 4. Register Camera

```bash
./scripts/register_camera.sh \
  cam-tapo-c110-01 \
  "Test Camera (Tapo C110)" \
  "rtsp://administrator:interOP@123@192.168.68.56:554/stream2" \
  edge-node-local \
  "TP-Link Tapo C110"
```

`register_camera.sh` **auto-detects** the API if `API_BASE` is unset: tries **127.0.0.1:8080** (gateway), **:9080** (nginx), **:8082** (device-service direct). Override with `export API_BASE=http://127.0.0.1:8082/api/v1` if needed. It **skips JWT** by default (`SKIP_AUTH=1`). The **node id** must exist in `edge_nodes` (seed uses `edge-node-a`).

### 5. Start Edge AI

```bash
cd edge
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

export NODE_ID=edge-node-local
export VPN_BROKER_IP=localhost
export MQTT_PORT=1883
export MQTT_USER=admin
export MQTT_PASS=devpassword123
export CONFIG_DIR=$(pwd)/config
export MODEL_DIR=$(pwd)/models
export FLASK_PORT=5000
export LORA_ENABLED=false

python3 farm_edge_node.py
```

## Verify Deployment

### Backend Health

```bash
curl http://localhost:9080/api/v1/nodes
curl http://localhost:9080/api/v1/cameras
```

### Edge Status

```bash
curl http://localhost:5000/health
```

### Camera RTSP Test

```bash
# Test with ffplay (or VLC)
ffplay rtsp://administrator:interOP@123@192.168.68.56:554/stream2
```

### RabbitMQ Management

Open `http://localhost:15672` (admin / devpassword123) and check:
- MQTT connections (should see `edge-node-local`)
- Exchanges: `farm.alerts`, `farm.siren.commands`
- Queues: `alert-service.queue`

## Troubleshooting

| Issue | Solution |
|-------|----------|
| **Edge can't connect to RabbitMQ** | Check `docker ps | grep rabbitmq`, verify MQTT plugin enabled |
| **Camera RTSP fails** | Enable RTSP in Tapo app: Settings → Advanced → Device Account |
| **Backend 502 Bad Gateway** | Verify API Gateway: `curl http://localhost:8080/actuator/health` |
| **Nginx 502** | Check nginx error log: `sudo tail -f /var/log/nginx/error.log` |
| **Vite crashes with `Error: read EIO` (readline)** | Often a flaky SSH/tmux TTY. Use **`npm run dev:bg`** in `dashboard/` or `simulator/` (sets `CI=true` so Vite skips keyboard shortcuts), or run `CI=true npm run dev`. `setup_and_build_all.sh` deploy-dashboard uses `dev:bg` automatically. |

## Next Steps

1. **Create zones** for the camera: `POST /api/v1/zones`
2. **Configure alert rules**: Edit `edge/config/zones.json`
3. **Test inference**: Trigger an alert and verify in dashboard
4. **Monitor logs** — see below

### Tailing local logs (`logs/`)

Follow everything started by `setup_and_build_all.sh deploy-*`:

```bash
tail -f logs/*.log
```

**Gradle `Input/output error` / Node `EIO` on `read`:** Background `bootRun` should use **stdin from `/dev/null`** (setup script does this). **Vite** also registers readline for `h`/`q` shortcuts when stdin looks like a TTY; a half-broken TTY can still throw `EIO`. The script starts dashboard/simulator with **`npm run dev:bg`** (`CI=true vite`), which skips that path. For manual `npm run dev`, switch to **`npm run dev:bg`** if you hit EIO. **Stop and re-run** deploy if logs still show old stack traces.

## Files Modified

- `edge/config/cameras.json` — Added Tapo C110 camera config
- `scripts/register_camera.sh` — Helper to register camera in backend
- `scripts/deploy_local.sh` — Automated deployment script

## Camera Details

- **Model:** TP-Link Tapo C110
- **IP:** 192.168.68.56
- **Username:** administrator
- **Password:** interOP@123
- **RTSP URL:** `rtsp://administrator:interOP@123@192.168.68.56:554/stream2`
- **Stream:** `stream2` (sub-stream, 640x480, recommended for inference)
