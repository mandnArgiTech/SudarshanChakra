# SudarshanChakra — Deployment Guide

## Step-by-Step Deployment for All Components

---

## Prerequisites

| Component | Minimum Requirement |
|-----------|-------------------|
| Cloud VPS | Ubuntu 22.04 LTS, 4 vCPU, 8GB RAM, 80GB SSD |
| Edge Node (×2) | i5-10400, RTX 3060 12GB, 32GB RAM, Ubuntu 22.04 |
| Raspberry Pi | Pi Zero 2 W, HiFiBerry MiniAMP, Ahuja SSA-250DP |
| ESP32 Tags (×4) | ESP32 DevKit V1 + SX1276 LoRa + MPU6050 (child tag) |
| Cameras (×8) | TP-Link VIGI C540-W, Tapo C210, Tapo C320WS |
| Domain | vivasvan-tech.in with DNS A record pointing to VPS IP |

---

## Phase 0: Local GPU Workstation (Single Command)

Use this when you want to set up and build the complete monorepo on a local development box.

```bash
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra
chmod +x setup_and_build_all.sh
./setup_and_build_all.sh
```

Optional flags:

```bash
# If Android SDK is not installed yet
SKIP_ANDROID=1 ./setup_and_build_all.sh

# If firmware toolchain is not installed yet
SKIP_FIRMWARE=1 ./setup_and_build_all.sh

# Custom Android SDK path
ANDROID_HOME=/path/to/android-sdk ./setup_and_build_all.sh
```

What this script handles:
- Docker infra bootstrap (PostgreSQL + RabbitMQ + queue/exchange initialization)
- Backend full build
- Dashboard lint/build/test
- Edge syntax/lint/test validation
- Android debug build + unit tests
- AlertManagement script syntax checks
- Firmware compile when `arduino-cli` is available

---

## Phase 1: Cloud VPS Setup

### 1.1 Initial Server Hardening

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ufw fail2ban unattended-upgrades docker.io docker-compose-v2

# Firewall
sudo ufw default deny incoming && sudo ufw default allow outgoing
sudo ufw allow 1194/udp      # OpenVPN
sudo ufw allow 443/tcp       # HTTPS
sudo ufw allow 8883/tcp      # MQTTS
sudo ufw allow <SSH_PORT>/tcp
sudo ufw enable

# Docker
sudo usermod -aG docker $USER
```

### 1.2 Clone Repository & Configure

```bash
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra/cloud

# Create environment file
cp .env.example .env
# Edit .env — set strong passwords:
#   DB_PASS=<strong_password>
#   RABBITMQ_PASS=<strong_password>
#   JWT_SECRET=$(openssl rand -hex 32)
#   MQTT_EDGE_PASS=<edge_password>
```

### 1.3 OpenVPN Server

```bash
OVPN_DATA="/opt/openvpn-data"
docker run -v $OVPN_DATA:/etc/openvpn --rm kylemanna/openvpn \
  ovpn_genconfig -u udp://vivasvan-tech.in -s 10.8.0.0/24 \
  -e 'client-to-client' -e 'keepalive 10 60'

docker run -v $OVPN_DATA:/etc/openvpn --rm -it kylemanna/openvpn ovpn_initpki

# Generate client configs
docker run -v $OVPN_DATA:/etc/openvpn --rm kylemanna/openvpn ovpn_getclient edge-node-a > edge-node-a.ovpn
docker run -v $OVPN_DATA:/etc/openvpn --rm kylemanna/openvpn ovpn_getclient edge-node-b > edge-node-b.ovpn

# Assign static VPN IPs
echo "ifconfig-push 10.8.0.10 10.8.0.9" > $OVPN_DATA/ccd/edge-node-a
echo "ifconfig-push 10.8.0.11 10.8.0.9" > $OVPN_DATA/ccd/edge-node-b
```

### 1.4 TLS Certificates

```bash
sudo apt install certbot
sudo certbot certonly --standalone -d vivasvan-tech.in
mkdir -p certs
cp /etc/letsencrypt/live/vivasvan-tech.in/fullchain.pem certs/
cp /etc/letsencrypt/live/vivasvan-tech.in/privkey.pem certs/
```

### 1.5 Start Services

```bash
docker compose up -d

# Wait for RabbitMQ to be healthy (~30s)
docker compose exec rabbitmq rabbitmq-diagnostics check_running

# Initialize RabbitMQ topology
pip install pika
RABBITMQ_PASS=<your_pass> python scripts/rabbitmq_init.py

# Verify
docker compose ps
curl -k https://localhost/health
```

---

## Phase 2: Edge Node Setup

### 2.1 Per-Node Setup (repeat for Node A and Node B)

```bash
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra/edge

# Install NVIDIA drivers + Docker with GPU support
sudo apt install -y nvidia-driver-535 nvidia-container-toolkit
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker

# Copy VPN config from VPS
scp user@vivasvan-tech.in:~/edge-node-a.ovpn vpn/

# Configure cameras
# Edit config/cameras.json — update IPs and credentials for your cameras

# Create .env file
cat > .env << 'EOF'
NODE_ID=edge-node-a
MQTT_USER=edge-publisher
MQTT_EDGE_PASS=<your_edge_password>
EOF

# Start
docker compose up -d

# Verify
docker logs -f edge-ai
# Expected: "LoRa receiver started", "Flask Edge GUI started on port 5000"
# Expected: "Connected to VPS MQTT broker", "Starting inference pipeline"
```

### 2.2 Zone Configuration

1. Open browser: `http://<edge-node-ip>:5000`
2. Select a camera from the sidebar
3. Click points on the image to draw zone polygons
4. Set zone name, type (intrusion/zero_tolerance/livestock_containment), priority, and target classes
5. Click "Save Zone" — zones are saved to `config/zones.json` and auto-loaded

### 2.3 Verify Camera Connectivity

```bash
# Test RTSP stream
ffprobe rtsp://admin:password@192.168.1.201:554/stream2

# Check Edge Node GPU
docker exec edge-ai nvidia-smi
```

---

## Phase 3: Raspberry Pi PA System

```bash
# On Raspberry Pi Zero 2 W
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra/AlertManagement

# Run setup (installs dependencies, configures I2S DAC, systemd service)
sudo bash setup.sh

# Configure
cp config/pa.env.example config/pa.env
# Edit pa.env: set MQTT broker IP to Edge Node's local IP

# Start
sudo systemctl start pa-controller
sudo systemctl enable pa-controller

# Test
python3 scripts/test_pa.py siren
python3 scripts/test_pa.py stop
```

---

## Phase 4: ESP32 Firmware

### 4.1 Worker Beacon Tags

```
1. Open firmware/worker_beacon/esp32_lora_tag.ino in Arduino IDE
2. Install libraries: LoRa by Sandeep Mistry, Adafruit MPU6050
3. Set: TAG_ID = "TAG-W001" (unique per tag)
4. Set: DEVICE_MODE = "WORKER_BEACON"
5. Select board: ESP32 Dev Module
6. Flash via USB
7. Repeat for TAG-W002, TAG-W003
```

### 4.2 Child Safety Tag

```
1. Same firmware, but set: TAG_ID = "TAG-C001"
2. Set: DEVICE_MODE = "CHILD_SAFETY"
3. Connect MPU6050 to I2C (SDA=21, SCL=22)
4. Flash via USB
```

### 4.3 LoRa Bridge Receivers

```
1. Open firmware/lora_bridge/esp32_lora_bridge_receiver.ino
2. Flash to ESP32 (no config needed)
3. Plug into Edge Node USB port
4. Verify: screen /dev/ttyUSB0 115200
5. Should see LoRa packets
```

---

## Phase 5: Verification Checklist

```
□ VPS services all healthy:          docker compose ps
□ PostgreSQL schema loaded:          docker exec postgres psql -U scadmin -d sudarshanchakra -c '\dt'
□ RabbitMQ queues created:           curl -u admin:pass http://localhost:15672/api/queues
□ VPN tunnel established:            ping 10.8.0.10 (from VPS)
□ Edge Node A connected:             docker logs edge-ai | grep "Connected to VPS"
□ Edge Node B connected:             docker logs edge-ai | grep "Connected to VPS"
□ Cameras streaming:                 Open http://<edge-ip>:5000
□ LoRa receiver active:              docker logs edge-ai | grep "LoRa receiver started"
□ PA system responding:              python3 test_pa.py status
□ Worker tags broadcasting:          docker logs edge-ai | grep "LoRa packet"
□ Dashboard accessible:              https://vivasvan-tech.in
□ Android app connects:              Test login + alert feed
```

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Edge Node: "Cannot reach VPS broker" | VPN not connected | Check `docker logs openvpn-client`, verify `.ovpn` file |
| Camera: "Cannot open RTSP stream" | Wrong credentials or IP | Test with `ffprobe`, check Tapo app settings |
| RabbitMQ: "Connection refused" | Service not started or wrong port | `docker compose logs rabbitmq`, check `.env` |
| YOLO: "Model not found" | Custom model not trained yet | Place `yolov8n_farm.pt` in `edge/models/` |
| PA System: No sound | I2S not configured | Run `aplay -l`, check `setup.sh` output |
| ESP32: No LoRa packets | Wrong frequency or wiring | Check 433MHz, verify SPI pins |
| Dashboard: 502 Bad Gateway | Backend services not running | `docker compose ps`, check service logs |
