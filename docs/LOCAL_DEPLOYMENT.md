# Local Deployment: Backend + Edge on Same Machine

This guide covers deploying backend services and edge AI on the same machine, with an existing nginx reverse proxy on port 9080.

## Prerequisites

- PostgreSQL and RabbitMQ running (via Docker or `setup_and_build_all.sh deploy-infra`)
- Java 21, Python 3.10+, Node.js 22+ (for dashboard, optional)
- Camera: Tapo C110 with RTSP enabled (username: `administrator`, password: `interOP@123`)

## Step 1: Start Infrastructure

```bash
# Start PostgreSQL and RabbitMQ
./setup_and_build_all.sh deploy-infra

# Or manually:
sudo docker network create sc-net 2>/dev/null
sudo docker run -d --name postgres --network sc-net \
  -e POSTGRES_DB=sudarshanchakra -e POSTGRES_USER=scadmin -e POSTGRES_PASSWORD=devpassword123 \
  -p 127.0.0.1:5432:5432 \
  -v $(pwd)/cloud/db/init.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro \
  postgres:16-alpine

sudo docker run -d --name rabbitmq --network sc-net --hostname farm-broker \
  -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=devpassword123 \
  -p 5672:5672 -p 1883:1883 -p 15672:15672 \
  -v $(pwd)/cloud/rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro \
  rabbitmq:3-management

# Initialize RabbitMQ topology
RABBITMQ_PASS=devpassword123 python3 cloud/scripts/rabbitmq_init.py
```

## Step 2: Configure Camera

### 2.1 Update Edge Camera Config

Edit `edge/config/cameras.json` to include your Tapo C110:

```json
{
  "cameras": [
    {
      "id": "cam-tapo-c110-01",
      "name": "Test Camera (Tapo C110)",
      "model": "TP-Link Tapo C110",
      "rtsp_url": "rtsp://administrator:interOP@123@192.168.68.56:554/stream2",
      "fps": 2.0,
      "enabled": true,
      "location": "Test deployment",
      "notes": "Tapo C110 — sub-stream for inference"
    }
  ]
}
```

**Note:** Tapo C110 RTSP format:
- Main stream: `rtsp://user:pass@ip:554/stream1` (1080p)
- Sub-stream: `rtsp://user:pass@ip:554/stream2` (640x480, recommended for inference)

### 2.2 Register Camera in Backend

After backend is running, register the camera via API:

```bash
# Get auth token first (if needed)
TOKEN=$(curl -s -X POST http://localhost:9080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.token')

# Register camera
curl -X POST http://localhost:9080/api/v1/cameras \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "cam-tapo-c110-01",
    "nodeId": "edge-node-local",
    "name": "Test Camera (Tapo C110)",
    "rtspUrl": "rtsp://administrator:interOP@123@192.168.68.56:554/stream2",
    "model": "TP-Link Tapo C110",
    "locationDescription": "Test deployment",
    "fpsTarget": 2.0,
    "resolution": "640x480",
    "enabled": true,
    "status": "online"
  }'
```

Or use the helper script: `./scripts/register_camera.sh`

## Step 3: Update Nginx for New Backend

If nginx on port 9080 is proxying to the old backend, update `/etc/nginx/sites-available/default` (or your config):

```nginx
server {
    listen 9080;
    server_name localhost;

    # API Gateway (new backend)
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket for alerts
    location /ws/ {
        proxy_pass http://localhost:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }
}
```

Reload nginx: `sudo nginx -t && sudo systemctl reload nginx`

## Step 4: Start Backend Services

```bash
# Build backend first
cd backend && ./gradlew build -x test

# Start services (in order)
./gradlew :auth-service:bootRun &
./gradlew :device-service:bootRun &
./gradlew :alert-service:bootRun &
./gradlew :siren-service:bootRun &
./gradlew :api-gateway:bootRun &

# Or use the unified script
cd .. && ./setup_and_build_all.sh deploy-backend
```

Services will run on:
- Auth Service: `8083`
- Device Service: `8082`
- Alert Service: `8081`
- Siren Service: `8084`
- API Gateway: `8080` (proxied by nginx on `9080`)

## Step 5: Start Edge AI

```bash
cd edge

# Install dependencies
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Set environment variables
export NODE_ID=edge-node-local
export VPN_BROKER_IP=localhost  # or 127.0.0.1 if RabbitMQ is local
export MQTT_PORT=1883
export MQTT_USER=admin
export MQTT_PASS=devpassword123
export CONFIG_DIR=$(pwd)/config
export MODEL_DIR=$(pwd)/models
export FLASK_PORT=5000

# Start edge node
python3 farm_edge_node.py
```

Edge will:
- Load cameras from `config/cameras.json`
- Connect to RabbitMQ MQTT on `localhost:1883`
- Run YOLO inference pipeline
- Publish alerts to `farm/alerts/#`
- Serve Flask GUI on `http://localhost:5000`

## Step 6: Verify Deployment

### Check Backend Health

```bash
curl http://localhost:9080/api/v1/devices/nodes
curl http://localhost:9080/api/v1/cameras
```

### Check Edge Status

```bash
# Edge GUI
curl http://localhost:5000/health

# Check MQTT connection (RabbitMQ Management UI)
# http://localhost:15672 (admin / devpassword123)
# Look for MQTT client: edge-node-local
```

### Test Camera Stream

```bash
# Test RTSP connection (requires ffmpeg/vlc)
ffplay rtsp://administrator:interOP@123@192.168.68.56:554/stream2
```

## Troubleshooting

### Edge can't connect to RabbitMQ

- Check RabbitMQ is running: `docker ps | grep rabbitmq`
- Verify MQTT plugin: `docker exec rabbitmq rabbitmq-plugins list | grep mqtt`
- Check firewall: `sudo ufw status`
- Test connection: `mosquitto_pub -h localhost -p 1883 -t test -m "hello"`

### Camera RTSP fails

- Verify RTSP is enabled in Tapo app: Settings → Advanced → Device Account
- Test with VLC: `vlc rtsp://administrator:interOP@123@192.168.68.56:554/stream2`
- Check network: `ping 192.168.68.56`
- Use sub-stream (`stream2`) for lower bandwidth

### Backend services fail to start

- Check PostgreSQL: `docker ps | grep postgres`
- Verify database schema: `psql -h localhost -U scadmin -d sudarshanchakra -c "\dt"`
- Check logs: `tail -f logs/service-*.log` (if using deploy-backend)

### Nginx 502 Bad Gateway

- Verify API Gateway is running: `curl http://localhost:8080/actuator/health`
- Check nginx error log: `sudo tail -f /var/log/nginx/error.log`
- Test proxy: `curl -v http://localhost:9080/api/v1/devices/nodes`

## Next Steps

1. Create zones for the camera in backend: `POST /api/v1/zones`
2. Configure alert rules in edge: `config/zones.json`
3. Test inference: trigger an alert and verify it appears in dashboard
4. Scale: add more cameras to `edge/config/cameras.json`
