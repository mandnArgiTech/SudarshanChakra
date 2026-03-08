# SudarshanChakra — Troubleshooting Guide

## Diagnosis & Resolution for Common Issues

---

## 1. Edge Node Issues

### 1.1 Edge Node Not Starting

**Symptom:** `docker logs edge-ai` shows errors or container keeps restarting.

**Diagnosis:**
```bash
# Check container status
docker compose ps

# Check detailed logs
docker logs edge-ai 2>&1 | tail -50

# Check GPU availability
nvidia-smi
```

**Common Causes:**

| Error | Cause | Fix |
|-------|-------|-----|
| `nvidia-smi: command not found` | NVIDIA drivers not installed | `sudo apt install nvidia-driver-535` |
| `Failed to initialize NVML` | Container lacks GPU access | Add `runtime: nvidia` to compose |
| `No cameras.json found` | Config directory not mounted | Check `volumes:` in docker-compose.yml |
| `Cannot reach VPS broker` | VPN not connected | Check `docker logs openvpn-client` |

### 1.2 Camera Not Connecting

**Symptom:** Logs show `[cam-XX] Cannot open RTSP stream. Retrying...`

**Diagnosis:**
```bash
# Test RTSP from Edge Node host
ffprobe rtsp://admin:password@192.168.1.201:554/stream2

# Check camera is reachable
ping 192.168.1.201

# Check camera list
cat config/cameras.json | python3 -m json.tool
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| Wrong IP address | Update `cameras.json` with correct IP |
| Wrong credentials | Check Tapo app → Camera Settings → Advanced → Device Account |
| RTSP not enabled | Tapo cameras: enable in app. VIGI cameras: enabled by default |
| Camera firmware update | Some updates reset RTSP settings — re-enable |
| Bandwidth saturation | Use `stream2` (sub-stream) not `stream1` |

### 1.3 No Detections / No Alerts

**Symptom:** Cameras connected but no alerts appear.

**Diagnosis:**
```bash
# Check inference pipeline is running
docker logs edge-ai | grep "Inference pipeline running"
docker logs edge-ai | grep "ALERT"

# Check MQTT connection
docker logs edge-ai | grep "Connected to VPS MQTT"

# Check zone config
docker exec edge-ai cat /app/config/zones.json
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| No YOLO model | Place trained model in `edge/models/yolov8n_farm.pt` |
| No zones defined | Use Edge GUI (:5000) to draw zones |
| Confidence too high | Lower thresholds in detection_filters.py |
| Wrong target classes | Ensure zone's `target_classes` match model class names |
| Model not detecting | Default YOLOv8n only knows COCO classes; custom training needed |

### 1.4 High GPU Usage / Slow Inference

**Diagnosis:**
```bash
# Check GPU metrics
docker exec edge-ai nvidia-smi

# Check inference speed in logs
docker logs edge-ai | grep "inference"
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| TensorRT engine not built | First run takes 2-3 min; check `models/` for `.engine` file |
| Too many cameras at high FPS | Reduce FPS in `cameras.json` (2.0 is usually sufficient) |
| Using PyTorch instead of TensorRT | Ensure `.engine` file exists and is loaded |
| GPU memory leak | Restart container: `docker restart edge-ai` |

---

## 2. VPN & Network Issues

### 2.1 VPN Not Connecting

**Symptom:** Edge Node logs show `VPN connection failed`.

**Diagnosis:**
```bash
# Check OpenVPN client
docker logs openvpn-client

# Ping VPS from host
ping vivasvan-tech.in

# Check VPN interface
docker exec openvpn-client ip addr show tun0
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| `.ovpn` file missing | Copy from VPS: `scp user@vps:~/edge-node-a.ovpn vpn/` |
| UDP 1194 blocked by ISP | Switch to TCP in OpenVPN config |
| VPS firewall blocking | `sudo ufw allow 1194/udp` on VPS |
| Certificate expired | Regenerate: `docker run ... ovpn_getclient edge-node-a` |

### 2.2 VPN Connected But No MQTT

**Symptom:** VPN is up but `Cannot reach VPS broker at 10.8.0.1`.

**Diagnosis:**
```bash
# Ping VPS VPN IP
docker exec edge-ai ping -c 3 10.8.0.1

# Check RabbitMQ is running on VPS
ssh user@vivasvan-tech.in "docker ps | grep rabbitmq"

# Test MQTT port
docker exec edge-ai nc -zv 10.8.0.1 1883
```

---

## 3. Cloud / VPS Issues

When using the **VPS deploy script** (`./cloud/deploy.sh`), the stack runs with `docker-compose.vps.yml` from the `cloud/` directory. Nginx serves the dashboard and API on **port 9080** by default (so SudarshanChakra does not conflict with other apps on the VPS, e.g. Portainer on vivasvan-tech.in). Use `docker compose -f docker-compose.vps.yml <command>` when running compose from `cloud/`.

### 3.1 PostgreSQL Connection Refused

```bash
# Check container
docker compose ps postgres

# Check logs
docker compose logs postgres

# Test connection
docker exec postgres pg_isready -U scadmin -d sudarshanchakra
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| Container not started | `docker compose up -d postgres` |
| Wrong password in `.env` | Check `DB_PASS` matches what was set initially |
| Port conflict | Check `netstat -tlnp | grep 5432` |

### 3.2 RabbitMQ Issues

```bash
# Check status
docker exec rabbitmq rabbitmqctl status

# List queues
docker exec rabbitmq rabbitmqctl list_queues

# Check management UI
curl -u admin:password http://localhost:15672/api/overview
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| No queues created | Run RabbitMQ init: from `cloud/` with `RABBITMQ_PASS` set run `python3 scripts/rabbitmq_init.py` (requires pika), or use the one-off Docker method as in `deploy.sh`: `docker run --rm --network cloud_default -e RABBITMQ_HOST=rabbitmq -e RABBITMQ_USER=admin -e RABBITMQ_PASS=$RABBITMQ_PASS -v $(pwd)/cloud/scripts/rabbitmq_init.py:/script.py:ro python:3.12-slim bash -c "pip install -q pika && python /script.py"` from repo root |
| TLS cert missing | Start without custom rabbitmq.conf for dev; VPS compose uses only `enabled_plugins` and `RABBITMQ_ERLANG_COOKIE` |
| Memory alarm | Increase `vm_memory_high_watermark` or add RAM |

### 3.3 Nginx / TLS Issues

```bash
# When using full compose (TLS)
docker compose logs nginx-proxy

# When using deploy.sh / docker-compose.vps.yml (HTTP-only)
docker compose -f docker-compose.vps.yml logs nginx-proxy
# Config: cloud/nginx/nginx-vps.conf; default host port 9080

# Test TLS (production)
curl -v https://vivasvan-tech.in/health

# Test HTTP (VPS script deploy)
curl -s http://localhost:9080/health

# Check cert expiry
openssl x509 -in certs/fullchain.pem -noout -dates
```

---

## 4. Alert System Issues

### 4.1 Alerts Not Appearing in Dashboard

**Flow to trace:**
```
Camera → Edge AI → MQTT → RabbitMQ → Alert Service → PostgreSQL → Dashboard
```

**Diagnostic steps:**
```bash
# 1. Check Edge Node is publishing
docker logs edge-ai | grep "ALERT"

# 2. Check RabbitMQ received message
docker exec rabbitmq rabbitmqctl list_queues name messages

# 3. Check Alert Service consumed it
docker compose logs alert-service | grep "Processed"

# 4. Check PostgreSQL has the alert
docker exec postgres psql -U scadmin -d sudarshanchakra \
  -c "SELECT id, priority, detection_class, created_at FROM alerts ORDER BY created_at DESC LIMIT 5;"
```

### 4.2 Too Many False Alerts

**Solutions by detection class:**

| Class | False Positive Source | Fix |
|-------|---------------------|-----|
| person | Scarecrow, poster, mannequin | Raise person confidence threshold |
| snake | Rope, hose, branch | Increase temporal confirmation window |
| fire | Sunset glow, orange clothing | Fire color validation should reject (needs frame passing fix) |
| smoke | Clouds, dust, fog | Smoke texture validation should reject |
| scorpion | Large beetle, leaf | Increase to 3-frame temporal confirmation |

**Quick fix:** Edit `detection_filters.py` and increase `CLASS_THRESHOLDS` for the problematic class.

### 4.3 Worker Tags Not Suppressing Alarms

```bash
# Check LoRa receiver is running
docker logs edge-ai | grep "LoRa"

# Check tags are being received
docker logs edge-ai | grep "LoRa packet"

# Check authorized_tags.json
cat config/authorized_tags.json
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| Tag not in `tags` array | Add tag ID to `authorized_tags.json` |
| Tag battery dead | Charge via USB-C |
| LoRa bridge not connected | Check USB connection, `ls /dev/ttyUSB*` |
| Tag on wrong frequency | Verify 433MHz in firmware `#define LORA_FREQ` |

---

## 5. PA System Issues

### 5.1 Siren Not Sounding

```bash
# Check PA controller is running
sudo systemctl status pa-controller

# Check MQTT connection
journalctl -u pa-controller | tail -20

# Manual test
python3 AlertManagement/scripts/test_pa.py siren
```

**Common Causes:**

| Cause | Fix |
|-------|-----|
| I2S not configured | Re-run `setup.sh` |
| Wrong MQTT broker IP | Update `pa.env` with correct broker address |
| Amplifier off | Check Ahuja SSA-250DP power switch |
| Volume at zero | `amixer set Master 100%` |

### 5.2 No Audio Output

```bash
# Check audio devices
aplay -l

# Test audio directly
speaker-test -t wav -c 2

# Check I2S overlay
cat /boot/config.txt | grep i2s
```

---

## 6. Log Locations

When using **deploy.sh** / **docker-compose.vps.yml**, run compose from `cloud/` with `-f docker-compose.vps.yml` (e.g. `docker compose -f docker-compose.vps.yml logs -f`).

| Component | Log Command |
|-----------|-------------|
| Edge AI | `docker logs edge-ai` |
| OpenVPN Client | `docker logs openvpn-client` |
| PostgreSQL | `docker compose logs postgres` |
| RabbitMQ | `docker compose logs rabbitmq` |
| Alert Service | `docker compose logs alert-service` |
| Nginx | `docker compose logs nginx-proxy` |
| PA Controller | `journalctl -u pa-controller` |
| VPN Health Monitor | `docker compose logs vpn-health-monitor` |

### Log Level Configuration

Edge AI supports `LOG_LEVEL` environment variable:
```bash
# In docker-compose.yml or .env
LOG_LEVEL=DEBUG    # Most verbose
LOG_LEVEL=INFO     # Default
LOG_LEVEL=WARNING  # Errors and warnings only
```

---

## 7. Emergency Procedures

### Complete System Down

1. Check VPS: `ssh user@vivasvan-tech.in "docker compose ps"`
2. Check Edge Nodes: physically verify power
3. If VPS down: `docker compose up -d` (auto-recovers PostgreSQL + RabbitMQ data from volumes)
4. Edge Nodes auto-reconnect within 90 seconds of VPN restoration

### Active Security Breach

1. **Trigger siren** from Android app or Dashboard (big red button)
2. Check camera feed on Dashboard → Cameras page
3. Call local authorities
4. Siren will continue until manually stopped

### False Alarm Flood

1. Open Dashboard → Alerts
2. Filter by source camera
3. Mark false positives
4. Consider raising detection thresholds in `detection_filters.py`
5. Consider temporarily disabling specific cameras in `cameras.json`
