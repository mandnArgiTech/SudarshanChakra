# SudarshanChakra — Enterprise Smart Farm Hazard Detection & Security System

## Complete Implementation Blueprint

---

## Table of Contents

1. System Architecture Overview
2. Epic 1: VPN & Cloud Infrastructure Setup
3. Epic 2: Edge AI Pipeline & Local GUI
4. Epic 3: Message Broker & Microservices Backend
5. Epic 4: UI/UX Mock Screens & App Architecture
6. Epic 5: Deployment Strategy & CI/CD Pipeline

---

## System Architecture Overview

### High-Level Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        FARM SITE (Sanga Reddy)                         │
│                                                                         │
│  ┌──────────────────────┐        ┌──────────────────────┐              │
│  │   EDGE NODE A         │        │   EDGE NODE B         │              │
│  │   i5-10400 / RTX 3060│        │   i5-10400 / RTX 3060│              │
│  │                      │        │                      │              │
│  │  ┌────────────────┐  │        │  ┌────────────────┐  │              │
│  │  │ YOLOv8n        │  │        │  │ YOLOv8n        │  │              │
│  │  │ (TensorRT)     │  │        │  │ (TensorRT)     │  │              │
│  │  │ 2-3 FPS/cam    │  │        │  │ 2-3 FPS/cam    │  │              │
│  │  └───────┬────────┘  │        │  └───────┬────────┘  │              │
│  │          │            │        │          │            │              │
│  │  ┌───────┴────────┐  │        │  ┌───────┴────────┐  │              │
│  │  │ Flask GUI      │  │        │  │ Flask GUI      │  │              │
│  │  │ :5000          │  │        │  │ :5000          │  │              │
│  │  │ Polygon Editor │  │        │  │ Polygon Editor │  │              │
│  │  └────────────────┘  │        │  └────────────────┘  │              │
│  │          │            │        │          │            │              │
│  │  ┌───────┴────────┐  │        │  ┌───────┴────────┐  │              │
│  │  │ ESP32/LoRa     │  │        │  │ ESP32/LoRa     │  │              │
│  │  │ Receiver       │  │        │  │ Receiver       │  │              │
│  │  └────────────────┘  │        │  └────────────────┘  │              │
│  │          │            │        │          │            │              │
│  │    OpenVPN Client     │        │    OpenVPN Client     │              │
│  └──────────┼────────────┘        └──────────┼────────────┘              │
│             │  ISP A                          │  ISP B                   │
└─────────────┼─────────────────────────────────┼─────────────────────────┘
              │            INTERNET              │
              └──────────────┬───────────────────┘
                             │  VPN Tunnel (10.8.0.0/24)
              ┌──────────────┴───────────────────┐
              │   CLOUD VPS (vivasvan-tech.in)    │
              │                                    │
              │  ┌────────────┐  ┌─────────────┐  │
              │  │ OpenVPN    │  │ RabbitMQ    │  │
              │  │ Server     │  │ MQTT Broker │  │
              │  └────────────┘  └──────┬──────┘  │
              │                         │          │
              │  ┌──────────────────────┴───────┐  │
              │  │ Java Spring Boot             │  │
              │  │ Microservices                 │  │
              │  │  ├─ Alert Service             │  │
              │  │  ├─ Device Management Service │  │
              │  │  ├─ User/Auth Service (JWT)   │  │
              │  │  └─ Siren Command Service     │  │
              │  └──────────────────────┬───────┘  │
              │                         │          │
              │  ┌──────────┐  ┌───────┴───────┐  │
              │  │PostgreSQL│  │ React.js      │  │
              │  │          │  │ Dashboard     │  │
              │  └──────────┘  │ :443          │  │
              │                └───────────────┘  │
              └────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │  Android App    │
                    │  (Kotlin/MQTT)  │
                    │  Push Alerts    │
                    │  Siren Control  │
                    └─────────────────┘
```

### Network Addressing Plan

| Component           | VPN IP (10.8.0.x) | Role                    |
|:---------------------|:-------------------|:------------------------|
| VPS (OpenVPN Server) | 10.8.0.1           | Hub — broker, API, DB   |
| Edge Node A          | 10.8.0.10          | Camera AI processing    |
| Edge Node B          | 10.8.0.11          | Camera AI processing    |

### Docker Container Map

| Host     | Container          | Image                    | Ports              |
|:---------|:-------------------|:-------------------------|:-------------------|
| Node A/B | edge-ai            | sudarshanchakra/edge-ai  | 5000 (Flask GUI)   |
| Node A/B | openvpn-client     | dperson/openvpn-client   | —                  |
| VPS      | openvpn-server     | kylemanna/openvpn        | 1194/udp           |
| VPS      | rabbitmq           | rabbitmq:3-management    | 5672, 15672, 1883  |
| VPS      | alert-service      | sudarshanchakra/alert-svc| 8081               |
| VPS      | device-service     | sudarshanchakra/device-svc| 8082              |
| VPS      | auth-service       | sudarshanchakra/auth-svc | 8083               |
| VPS      | siren-service      | sudarshanchakra/siren-svc| 8084               |
| VPS      | api-gateway        | sudarshanchakra/gateway  | 8080               |
| VPS      | postgres           | postgres:16              | 5432               |
| VPS      | react-dashboard    | sudarshanchakra/dashboard| 443                |

---

## Epic 1: VPN & Cloud Infrastructure Setup

### Story 1.1 — Provision and Harden the Cloud VPS

**As a** system administrator,
**I want** a secure, hardened VPS at vivasvan-tech.in,
**So that** it serves as the central hub for all farm communications.

**Acceptance Criteria:**
- Ubuntu 22.04 LTS provisioned with minimum 4 vCPU, 8 GB RAM, 80 GB SSD
- SSH hardened: root login disabled, key-only auth, port changed from 22
- UFW firewall configured with only required ports open:
  - 1194/udp (OpenVPN)
  - 443/tcp (React dashboard + API gateway)
  - 8883/tcp (MQTTS — TLS-wrapped MQTT)
  - Custom SSH port
- fail2ban installed and configured for SSH and OpenVPN
- Automatic security updates enabled via `unattended-upgrades`
- Docker and Docker Compose v2 installed
- Swap configured at 4 GB (for PostgreSQL under load)

**Implementation Notes:**

```bash
# Initial hardening
sudo apt update && sudo apt upgrade -y
sudo apt install -y ufw fail2ban unattended-upgrades docker.io docker-compose-v2

# Firewall
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 1194/udp    # OpenVPN
sudo ufw allow 443/tcp     # HTTPS (dashboard + API)
sudo ufw allow 8883/tcp    # MQTTS
sudo ufw allow <SSH_PORT>/tcp
sudo ufw enable

# Docker permissions
sudo usermod -aG docker $USER
```

---

### Story 1.2 — Deploy OpenVPN Server on VPS

**As a** network engineer,
**I want** an OpenVPN server running on the VPS with a 10.8.0.0/24 subnet,
**So that** Edge Nodes can securely communicate with cloud services regardless of their ISP.

**Acceptance Criteria:**
- OpenVPN server deployed via Docker (kylemanna/openvpn)
- TLS 1.3 with AES-256-GCM cipher
- VPN subnet: 10.8.0.0/24
- Server assigned 10.8.0.1
- Client configs generated for Node A (static IP 10.8.0.10) and Node B (10.8.0.11)
- Inter-client communication enabled (so Node A can reach Node B if needed)
- Keepalive set to 10/60 for fast dead-peer detection
- DNS pushed to clients: 1.1.1.1, 8.8.8.8

**Implementation:**

```bash
# On VPS — initialize PKI and generate server config
OVPN_DATA="/opt/openvpn-data"
docker run -v $OVPN_DATA:/etc/openvpn --rm kylemanna/openvpn ovpn_genconfig \
  -u udp://vivasvan-tech.in \
  -s 10.8.0.0/24 \
  -e 'client-to-client' \
  -e 'keepalive 10 60' \
  -e 'cipher AES-256-GCM' \
  -e 'tls-crypt /etc/openvpn/pki/ta.key'

docker run -v $OVPN_DATA:/etc/openvpn --rm -it kylemanna/openvpn ovpn_initpki

# Generate client certs with static IPs
docker run -v $OVPN_DATA:/etc/openvpn --rm kylemanna/openvpn ovpn_getclient edge-node-a > edge-node-a.ovpn
docker run -v $OVPN_DATA:/etc/openvpn --rm kylemanna/openvpn ovpn_getclient edge-node-b > edge-node-b.ovpn

# Assign static IPs via CCD (client-config-directory)
echo "ifconfig-push 10.8.0.10 10.8.0.9" > $OVPN_DATA/ccd/edge-node-a
echo "ifconfig-push 10.8.0.11 10.8.0.9" > $OVPN_DATA/ccd/edge-node-b

# Start server
docker run -d --name openvpn --restart=always \
  -v $OVPN_DATA:/etc/openvpn \
  -p 1194:1194/udp \
  --cap-add=NET_ADMIN \
  kylemanna/openvpn
```

---

### Story 1.3 — Configure Edge Nodes for Auto-Boot VPN Dial-In

**As a** field technician,
**I want** Edge Nodes to automatically connect to the VPN on boot,
**So that** the system recovers from power outages without manual intervention.

**Acceptance Criteria:**
- OpenVPN client runs as a Docker container with `restart: always`
- Container starts before the edge-ai container (dependency chain)
- VPN connection health checked every 30 seconds via ping to 10.8.0.1
- If VPN drops, container auto-restarts and reconnects
- Syslog captures VPN connection/disconnection events
- Tested: pull power from Node A, restore power, confirm VPN reconnects within 90 seconds

**docker-compose.yml (Edge Node):**

```yaml
version: "3.8"
services:
  openvpn-client:
    image: dperson/openvpn-client
    container_name: openvpn-client
    cap_add:
      - NET_ADMIN
    devices:
      - /dev/net/tun
    volumes:
      - ./vpn/edge-node-a.ovpn:/vpn/vpn.conf:ro
    environment:
      - FIREWALL=
    restart: always
    networks:
      - farm-net
    healthcheck:
      test: ["CMD", "ping", "-c", "1", "-W", "3", "10.8.0.1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 15s

  edge-ai:
    build: .
    container_name: edge-ai
    runtime: nvidia
    environment:
      - NVIDIA_VISIBLE_DEVICES=all
    volumes:
      - ./config:/app/config
      - ./models:/app/models
    ports:
      - "5000:5000"
    depends_on:
      openvpn-client:
        condition: service_healthy
    network_mode: "service:openvpn-client"  # Route through VPN
    restart: always

networks:
  farm-net:
    driver: bridge
```

---

### Story 1.4 — VPN Health Monitoring & Alerting

**As a** system administrator,
**I want** automated monitoring of VPN tunnel health,
**So that** I am alerted immediately if an Edge Node goes offline.

**Acceptance Criteria:**
- VPS runs a cron script every 60 seconds pinging 10.8.0.10 and 10.8.0.11
- If 3 consecutive pings fail, a "node_offline" alert is published to RabbitMQ
- Alert includes: node_id, last_seen_timestamp, failure_count
- Dashboard and Android app display node connectivity status
- When node reconnects, a "node_online" event is published

**VPS Health Monitor Script:**

```python
#!/usr/bin/env python3
"""vpn_health_monitor.py — runs as a systemd timer on VPS"""
import subprocess, json, time, pika

NODES = {"edge-node-a": "10.8.0.10", "edge-node-b": "10.8.0.11"}
FAIL_THRESHOLD = 3
fail_counts = {n: 0 for n in NODES}

def ping(ip):
    return subprocess.run(
        ["ping", "-c", "1", "-W", "2", ip],
        capture_output=True
    ).returncode == 0

def publish_event(node_id, event_type):
    conn = pika.BlockingConnection(pika.ConnectionParameters("localhost"))
    ch = conn.channel()
    ch.exchange_declare(exchange="farm.events", exchange_type="topic", durable=True)
    ch.basic_publish(
        exchange="farm.events",
        routing_key=f"node.{event_type}",
        body=json.dumps({
            "node_id": node_id,
            "event": event_type,
            "timestamp": time.time()
        })
    )
    conn.close()

for node_id, ip in NODES.items():
    if ping(ip):
        if fail_counts[node_id] >= FAIL_THRESHOLD:
            publish_event(node_id, "online")
        fail_counts[node_id] = 0
    else:
        fail_counts[node_id] += 1
        if fail_counts[node_id] == FAIL_THRESHOLD:
            publish_event(node_id, "offline")
```

---

## Epic 2: Edge AI Pipeline & Local GUI

### Story 2.1 — Dockerized YOLOv8n with TensorRT Optimization

**As a** ML engineer,
**I want** YOLOv8n exported to TensorRT FP16 engine on the RTX 3060,
**So that** inference runs at <15ms per frame enabling 2-3 FPS across multiple cameras.

**Acceptance Criteria:**
- Base image: `ultralytics/ultralytics:latest` (includes CUDA + cuDNN)
- YOLOv8n model exported to TensorRT FP16 `.engine` file on first boot
- Engine cached in mounted volume `/app/models/` (persists across container restarts)
- Custom training classes: `person, cow, snake, scorpion, fire, smoke, child`
- Inference benchmark: <15ms per 640×640 frame on RTX 3060
- Frame-skipping: each camera processed at 2-3 FPS (not full 30 FPS stream)

**Model Export & Caching Logic:**

```python
import os
from ultralytics import YOLO

ENGINE_PATH = "/app/models/yolov8n_farm.engine"
ONNX_PATH = "/app/models/yolov8n_farm.onnx"
PT_PATH = "/app/models/yolov8n_farm.pt"

def load_model():
    """Load TensorRT engine, building it on first run."""
    if os.path.exists(ENGINE_PATH):
        return YOLO(ENGINE_PATH)
    
    model = YOLO(PT_PATH)
    model.export(format="engine", half=True, device=0, workspace=4)
    return YOLO(ENGINE_PATH)
```

**Dockerfile (aligned with provided foundation):**

```dockerfile
FROM ultralytics/ultralytics:latest
WORKDIR /app

RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    openvpn \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
EXPOSE 5000
CMD ["python", "farm_edge_node.py"]
```

**requirements.txt:**

```
flask==3.0.*
flask-socketio==5.*
opencv-python-headless==4.9.*
paho-mqtt==2.*
pyserial==3.*
shapely==2.*
numpy
Pillow
gunicorn
eventlet
```

---

### Story 2.2 — Multi-Camera RTSP Frame-Skipping Pipeline

**As a** systems engineer,
**I want** a frame-skipping pipeline that processes each camera at 2-3 FPS,
**So that** the RTX 3060 can handle 6-8 cameras without dropping frames.

**Acceptance Criteria:**
- Each camera runs in its own thread with independent RTSP connection
- Frame grab rate: 2-3 FPS (configurable per camera via JSON config)
- Frames queued with max depth of 2 (drop oldest if full — no backpressure)
- Single inference thread batches frames from all cameras
- GPU memory usage stays below 8 GB (leaving headroom for TensorRT)
- Graceful reconnection if RTSP stream drops (exponential backoff, max 30s)

**Core Pipeline Architecture:**

```python
import cv2
import threading
import time
from queue import Queue
from dataclasses import dataclass
from typing import List, Optional
import numpy as np

@dataclass
class CameraConfig:
    id: str
    name: str
    rtsp_url: str
    fps: float = 2.0
    enabled: bool = True

@dataclass 
class FramePacket:
    camera_id: str
    frame: np.ndarray
    timestamp: float
    frame_number: int

class CameraGrabber(threading.Thread):
    """Grabs frames from a single RTSP camera at configured FPS."""
    
    def __init__(self, config: CameraConfig, queue: Queue):
        super().__init__(daemon=True, name=f"cam-{config.id}")
        self.config = config
        self.queue = queue
        self._stop_event = threading.Event()
        self.frame_count = 0
        
    def run(self):
        interval = 1.0 / self.config.fps
        backoff = 1.0
        
        while not self._stop_event.is_set():
            cap = cv2.VideoCapture(self.config.rtsp_url)
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
            
            if not cap.isOpened():
                time.sleep(min(backoff, 30))
                backoff *= 2
                continue
            
            backoff = 1.0  # Reset on successful connect
            
            while not self._stop_event.is_set():
                ret, frame = cap.read()
                if not ret:
                    break
                
                self.frame_count += 1
                packet = FramePacket(
                    camera_id=self.config.id,
                    frame=frame,
                    timestamp=time.time(),
                    frame_number=self.frame_count,
                )
                
                # Non-blocking put — drop oldest if full
                if self.queue.full():
                    try:
                        self.queue.get_nowait()
                    except Exception:
                        pass
                self.queue.put(packet)
                
                time.sleep(interval)
            
            cap.release()
    
    def stop(self):
        self._stop_event.set()


class InferencePipeline:
    """Consumes frames from all cameras and runs YOLO inference."""
    
    def __init__(self, model, cameras: List[CameraConfig]):
        self.model = model
        self.frame_queue = Queue(maxsize=len(cameras) * 2)
        self.grabbers = [CameraGrabber(c, self.frame_queue) for c in cameras if c.enabled]
        self.results_callbacks = []
    
    def start(self):
        for g in self.grabbers:
            g.start()
        
        # Inference loop in main thread
        while True:
            packet = self.frame_queue.get()
            results = self.model.predict(
                packet.frame,
                imgsz=640,
                conf=0.4,
                verbose=False,
            )
            self._process_results(packet, results[0])
    
    def _process_results(self, packet: FramePacket, result):
        """Extract detections and pass to zone/fusion logic."""
        for box in result.boxes:
            cls_id = int(box.cls[0])
            cls_name = result.names[cls_id]
            conf = float(box.conf[0])
            x1, y1, x2, y2 = box.xyxy[0].tolist()
            
            detection = {
                "camera_id": packet.camera_id,
                "class": cls_name,
                "confidence": conf,
                "bbox": [x1, y1, x2, y2],
                "bottom_center": [(x1 + x2) / 2, y2],  # Feet position
                "timestamp": packet.timestamp,
            }
            
            for callback in self.results_callbacks:
                callback(detection)
```

---

### Story 2.3 — Virtual Fence Polygon Engine

**As a** farm manager,
**I want** to draw virtual fence polygons on camera views via a web GUI,
**So that** alerts trigger when a person's feet enter a restricted zone.

**Acceptance Criteria:**
- Flask web GUI at port 5000 shows live camera snapshots
- User can draw polygons by clicking points on the image
- Polygons saved to `/app/config/zones.json` with structure:
  ```json
  {
    "camera_id": "cam-01",
    "zones": [
      {
        "id": "zone-pond",
        "name": "Pond Danger Zone",
        "type": "zero_tolerance",
        "priority": "critical",
        "target_classes": ["person", "child"],
        "polygon": [[100,200], [300,200], [300,400], [100,400]]
      },
      {
        "id": "zone-perimeter",
        "name": "Farm Perimeter",
        "type": "intrusion",
        "priority": "high",
        "target_classes": ["person"],
        "polygon": [[0,0], [640,0], [640,480], [0,480]]
      }
    ]
  }
  ```
- Point-in-polygon test uses Shapely library
- Bottom-center of bounding box (feet) is the test point
- Zone types: `intrusion`, `zero_tolerance`, `livestock_containment`

**Polygon Math Engine:**

```python
from shapely.geometry import Point, Polygon
from typing import Dict, List, Optional
import json

class ZoneEngine:
    """Manages virtual fence zones and performs point-in-polygon checks."""
    
    def __init__(self, config_path: str = "/app/config/zones.json"):
        self.config_path = config_path
        self.zones: Dict[str, List[dict]] = {}
        self.polygons: Dict[str, Polygon] = {}
        self.reload()
    
    def reload(self):
        with open(self.config_path) as f:
            data = json.load(f)
        
        self.zones = {}
        self.polygons = {}
        
        for cam in data.get("cameras", [data]):
            cam_id = cam["camera_id"]
            self.zones[cam_id] = cam["zones"]
            for zone in cam["zones"]:
                key = f"{cam_id}:{zone['id']}"
                self.polygons[key] = Polygon(zone["polygon"])
    
    def check_detection(self, detection: dict) -> Optional[dict]:
        """
        Check if a detection's bottom-center falls inside any zone.
        Returns zone info if violated, None otherwise.
        """
        cam_id = detection["camera_id"]
        cls = detection["class"]
        bx, by = detection["bottom_center"]
        point = Point(bx, by)
        
        zones = self.zones.get(cam_id, [])
        
        for zone in zones:
            if cls not in zone["target_classes"]:
                continue
            
            key = f"{cam_id}:{zone['id']}"
            polygon = self.polygons.get(key)
            
            if polygon and polygon.contains(point):
                # Special case: livestock containment triggers when OUTSIDE
                if zone["type"] == "livestock_containment":
                    continue  # Inside is OK for livestock
                
                return {
                    "zone_id": zone["id"],
                    "zone_name": zone["name"],
                    "zone_type": zone["type"],
                    "priority": zone["priority"],
                    "detection": detection,
                }
        
        # Livestock containment: trigger if cow is OUTSIDE designated area
        for zone in zones:
            if zone["type"] == "livestock_containment" and cls in zone["target_classes"]:
                key = f"{cam_id}:{zone['id']}"
                polygon = self.polygons.get(key)
                if polygon and not polygon.contains(point):
                    return {
                        "zone_id": zone["id"],
                        "zone_name": zone["name"],
                        "zone_type": zone["type"],
                        "priority": zone["priority"],
                        "detection": detection,
                    }
        
        return None
```

---

### Story 2.4 — ESP32/LoRa Sensor Fusion (Worker vs. Intruder)

**As a** security system,
**I want** to correlate person detections with ESP32/LoRa wearable tags,
**So that** authorized workers don't trigger false alarms.

**Acceptance Criteria:**
- ESP32 LoRa receiver connected to Edge Node via USB-Serial
- Each worker wearable broadcasts a unique ID every 5 seconds
- When a "person" detection occurs, fusion engine checks if any authorized tag was seen in the last 15 seconds
- If authorized tag present → suppress alarm, log as "worker_identified"
- If no tag → escalate as "intruder_detected" with high priority
- Tag registry stored in `/app/config/authorized_tags.json`
- RSSI-based proximity estimation (optional: if RSSI < threshold, tag is too far)

**LoRa Receiver Integration:**

```python
import serial
import threading
import time
import json
from collections import defaultdict

class LoRaReceiver(threading.Thread):
    """Receives ESP32 LoRa beacon packets via USB-Serial."""
    
    def __init__(self, port="/dev/ttyUSB0", baud=115200):
        super().__init__(daemon=True, name="lora-receiver")
        self.port = port
        self.baud = baud
        self.last_seen = {}  # tag_id → (timestamp, rssi)
        self.authorized_tags = self._load_authorized()
        self._lock = threading.Lock()
    
    def _load_authorized(self):
        try:
            with open("/app/config/authorized_tags.json") as f:
                return set(json.load(f).get("tags", []))
        except FileNotFoundError:
            return set()
    
    def run(self):
        while True:
            try:
                ser = serial.Serial(self.port, self.baud, timeout=1)
                while True:
                    line = ser.readline().decode("utf-8", errors="ignore").strip()
                    if line:
                        self._parse_beacon(line)
            except serial.SerialException:
                time.sleep(5)  # Reconnect backoff
    
    def _parse_beacon(self, line: str):
        """Parse: TAG:<id>,RSSI:<value>"""
        try:
            parts = dict(p.split(":") for p in line.split(","))
            tag_id = parts.get("TAG", "")
            rssi = int(parts.get("RSSI", -999))
            
            with self._lock:
                self.last_seen[tag_id] = (time.time(), rssi)
        except Exception:
            pass
    
    def is_worker_nearby(self, max_age_seconds: float = 15.0) -> bool:
        """Check if any authorized tag was seen recently."""
        now = time.time()
        with self._lock:
            for tag_id, (ts, rssi) in self.last_seen.items():
                if tag_id in self.authorized_tags and (now - ts) < max_age_seconds:
                    return True
        return False
    
    def get_nearby_workers(self, max_age_seconds: float = 15.0) -> list:
        """Return list of recently seen authorized tags."""
        now = time.time()
        workers = []
        with self._lock:
            for tag_id, (ts, rssi) in self.last_seen.items():
                if tag_id in self.authorized_tags and (now - ts) < max_age_seconds:
                    workers.append({"tag_id": tag_id, "rssi": rssi, "age": now - ts})
        return workers
```

---

### Story 2.5 — Pond Zero-Tolerance Safety System

**As a** parent and farm owner,
**I want** a zero-tolerance detection system around the pond,
**So that** any child detected near water triggers an immediate critical alert.

**Acceptance Criteria:**
- Pond zone marked as `zero_tolerance` in zones.json
- Target classes: `child`, `person` (any person near pond is flagged)
- Alert priority: CRITICAL (highest — bypasses all suppression)
- No worker-tag suppression for zero-tolerance zones (even workers get flagged)
- Dual-path alert: both AI visual detection AND ESP32 fall-detector
- ESP32 wearable on child detects sudden acceleration (fall into water)
- Both paths independently trigger alerts — either one is sufficient
- Latency requirement: alert must reach Android app within 3 seconds of detection

---

### Story 2.6 — Central Alert Decision Engine

**As a** the edge AI system,
**I want** a central decision engine that combines detections, zones, and LoRa data,
**So that** only validated alerts are published to the cloud.

**Acceptance Criteria:**
- All detections pass through: Detection → Zone Check → LoRa Fusion → Alert Decision
- Alert deduplication: same zone + same class cannot re-trigger within 30 seconds
- Alert payload published to RabbitMQ via MQTT over VPN:

```json
{
  "alert_id": "uuid-v4",
  "node_id": "edge-node-a",
  "camera_id": "cam-01",
  "zone_id": "zone-pond",
  "zone_name": "Pond Danger Zone",
  "zone_type": "zero_tolerance",
  "priority": "critical",
  "detection_class": "child",
  "confidence": 0.87,
  "bbox": [120, 200, 280, 450],
  "snapshot_url": "http://10.8.0.10:5000/snapshots/<alert_id>.jpg",
  "worker_suppressed": false,
  "timestamp": 1709712000.0,
  "metadata": {
    "lora_workers_nearby": [],
    "frame_number": 14523
  }
}
```

**Decision Engine:**

```python
import uuid
import time
import json
import paho.mqtt.client as mqtt
from typing import Optional

class AlertDecisionEngine:
    """Central brain: combines detection + zone + LoRa → alert decision."""
    
    def __init__(self, zone_engine, lora_receiver, mqtt_client, node_id):
        self.zone_engine = zone_engine
        self.lora = lora_receiver
        self.mqtt = mqtt_client
        self.node_id = node_id
        self.recent_alerts = {}  # key → timestamp (dedup)
        self.DEDUP_WINDOW = 30  # seconds
    
    def process_detection(self, detection: dict):
        """Main decision pipeline for a single detection."""
        
        # Step 1: Check if detection falls in any zone
        violation = self.zone_engine.check_detection(detection)
        if not violation:
            return  # Not in any zone — ignore
        
        # Step 2: Deduplication
        dedup_key = f"{violation['zone_id']}:{detection['class']}"
        now = time.time()
        if dedup_key in self.recent_alerts:
            if now - self.recent_alerts[dedup_key] < self.DEDUP_WINDOW:
                return  # Already alerted recently
        
        # Step 3: LoRa fusion (skip for zero-tolerance zones)
        worker_suppressed = False
        if violation["zone_type"] != "zero_tolerance":
            if detection["class"] == "person" and self.lora.is_worker_nearby():
                worker_suppressed = True
                # Log but don't alert
                self._log_suppressed(detection, violation)
                return
        
        # Step 4: Build and publish alert
        alert = {
            "alert_id": str(uuid.uuid4()),
            "node_id": self.node_id,
            "camera_id": detection["camera_id"],
            "zone_id": violation["zone_id"],
            "zone_name": violation["zone_name"],
            "zone_type": violation["zone_type"],
            "priority": violation["priority"],
            "detection_class": detection["class"],
            "confidence": detection["confidence"],
            "bbox": detection["bbox"],
            "worker_suppressed": False,
            "timestamp": now,
            "metadata": {
                "lora_workers_nearby": self.lora.get_nearby_workers(),
                "frame_number": detection.get("frame_number", 0),
            }
        }
        
        # Publish to cloud via MQTT over VPN
        topic = f"farm/alerts/{violation['priority']}"
        self.mqtt.publish(topic, json.dumps(alert), qos=1)
        
        # Update dedup tracker
        self.recent_alerts[dedup_key] = now
        
        # Cleanup old dedup entries
        self.recent_alerts = {
            k: v for k, v in self.recent_alerts.items()
            if now - v < self.DEDUP_WINDOW * 2
        }
    
    def _log_suppressed(self, detection, violation):
        """Log worker-suppressed events for audit trail."""
        self.mqtt.publish("farm/events/worker_identified", json.dumps({
            "node_id": self.node_id,
            "camera_id": detection["camera_id"],
            "zone_id": violation["zone_id"],
            "workers": self.lora.get_nearby_workers(),
            "timestamp": time.time(),
        }), qos=0)
```

---

### Story 2.7 — Siren Command Listener on Edge Nodes

**As an** Edge Node,
**I want** to subscribe to siren commands from the cloud,
**So that** farm managers can remotely trigger physical sirens via the Android app.

**Acceptance Criteria:**
- Edge Node subscribes to `farm/siren/trigger` and `farm/siren/stop` topics
- On `trigger`: activates PA system via the existing AlertManagement controller
- On `stop`: stops the siren
- Acknowledgment published to `farm/siren/ack` with node_id and status
- Integrates with the Ahuja SSA-250DP PA system already deployed

**Integration with AlertManagement PA Controller:**

```python
def on_siren_command(client, userdata, msg):
    """Handle siren commands from cloud/Android app."""
    payload = json.loads(msg.payload.decode())
    command = payload.get("command")
    
    if command == "trigger":
        # Forward to local PA controller
        pa_client.publish("pa/command", json.dumps({
            "command": "play_siren",
            "url": payload.get("siren_url", "http://localhost/audio/siren.mp3")
        }), qos=1)
        
        # Acknowledge
        client.publish("farm/siren/ack", json.dumps({
            "node_id": NODE_ID,
            "status": "siren_activated",
            "timestamp": time.time()
        }))
    
    elif command == "stop":
        pa_client.publish("pa/command", json.dumps({"command": "stop"}))
        client.publish("farm/siren/ack", json.dumps({
            "node_id": NODE_ID,
            "status": "siren_stopped",
            "timestamp": time.time()
        }))
```

---

## Epic 3: Message Broker & Microservices Backend

### Story 3.1 — RabbitMQ with MQTT Plugin on VPS

**As a** backend architect,
**I want** RabbitMQ deployed with the MQTT plugin enabled,
**So that** Edge Nodes publish via MQTT and Java services consume via AMQP.

**Acceptance Criteria:**
- RabbitMQ 3.13+ deployed via Docker with management UI
- MQTT plugin enabled on port 1883 (internal VPN) and 8883 (TLS for Android)
- TLS certificate from Let's Encrypt for public-facing MQTTS
- Exchanges: `farm.alerts` (topic), `farm.commands` (direct), `farm.events` (topic)
- Queues bound with routing keys:
  - `alert.critical` → priority queue (high priority consumers)
  - `alert.high` → standard queue
  - `alert.warning` → low priority queue
  - `siren.commands` → direct queue
- Dead-letter exchange for failed message processing
- User accounts: `edge-publisher` (publish only), `backend-consumer` (consume), `android-client` (pub/sub limited)

**docker-compose.yml (VPS — relevant section):**

```yaml
  rabbitmq:
    image: rabbitmq:3-management
    container_name: rabbitmq
    hostname: farm-broker
    ports:
      - "5672:5672"     # AMQP (internal)
      - "1883:1883"     # MQTT (VPN only)
      - "8883:8883"     # MQTTS (public, TLS)
      - "15672:15672"   # Management UI
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
      - ./rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf
      - ./rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins
      - ./certs:/etc/rabbitmq/certs
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: "${RABBITMQ_ADMIN_PASS}"
    restart: always
```

**enabled_plugins:**
```
[rabbitmq_management, rabbitmq_mqtt, rabbitmq_web_mqtt].
```

---

### Story 3.2 — PostgreSQL Schema Design

**As a** data architect,
**I want** a well-normalized PostgreSQL schema,
**So that** all alerts, devices, users, and audit trails are stored reliably.

**Acceptance Criteria:**
- Schema supports multi-tenant (future farms) via `farm_id`
- All tables have `created_at` and `updated_at` timestamps
- Indexes on frequently queried columns (priority, timestamp, zone_id)
- Alert table supports full-text search on `detection_class`

**Schema:**

```sql
-- Users & Authentication
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'manager', 'viewer')),
    mqtt_client_id VARCHAR(100),  -- MQTT client ID for direct push
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Edge Nodes
CREATE TABLE edge_nodes (
    id VARCHAR(50) PRIMARY KEY,  -- e.g., 'edge-node-a'
    farm_id UUID NOT NULL,
    display_name VARCHAR(100),
    vpn_ip INET,
    status VARCHAR(20) DEFAULT 'unknown',
    last_heartbeat TIMESTAMPTZ,
    config JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Cameras
CREATE TABLE cameras (
    id VARCHAR(50) PRIMARY KEY,
    node_id VARCHAR(50) REFERENCES edge_nodes(id),
    name VARCHAR(100),
    rtsp_url TEXT,
    location_description TEXT,
    enabled BOOLEAN DEFAULT TRUE
);

-- Virtual Fence Zones
CREATE TABLE zones (
    id VARCHAR(50) PRIMARY KEY,
    camera_id VARCHAR(50) REFERENCES cameras(id),
    name VARCHAR(100),
    zone_type VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    target_classes TEXT[] NOT NULL,
    polygon JSONB NOT NULL,  -- [[x1,y1], [x2,y2], ...]
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Alerts (core table — high write volume)
CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    node_id VARCHAR(50) REFERENCES edge_nodes(id),
    camera_id VARCHAR(50) REFERENCES cameras(id),
    zone_id VARCHAR(50) REFERENCES zones(id),
    zone_name VARCHAR(100),
    zone_type VARCHAR(30),
    priority VARCHAR(20) NOT NULL,
    detection_class VARCHAR(50) NOT NULL,
    confidence REAL,
    bbox REAL[],
    snapshot_url TEXT,
    worker_suppressed BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'new'
        CHECK (status IN ('new', 'acknowledged', 'resolved', 'false_positive')),
    acknowledged_by UUID REFERENCES users(id),
    acknowledged_at TIMESTAMPTZ,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_alerts_priority ON alerts(priority, created_at DESC);
CREATE INDEX idx_alerts_status ON alerts(status, created_at DESC);
CREATE INDEX idx_alerts_zone ON alerts(zone_id, created_at DESC);
CREATE INDEX idx_alerts_created ON alerts(created_at DESC);

-- Siren Actions (audit trail)
CREATE TABLE siren_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    triggered_by UUID REFERENCES users(id),
    target_node VARCHAR(50) REFERENCES edge_nodes(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('trigger', 'stop')),
    alert_id UUID REFERENCES alerts(id),
    acknowledged BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Worker Tags
CREATE TABLE worker_tags (
    tag_id VARCHAR(50) PRIMARY KEY,
    worker_name VARCHAR(100),
    farm_id UUID NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Suppression Log (workers identified, alarms suppressed)
CREATE TABLE suppression_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id VARCHAR(50),
    camera_id VARCHAR(50),
    zone_id VARCHAR(50),
    tag_id VARCHAR(50),
    worker_name VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

### Story 3.3 — Java Spring Boot Microservices Architecture

**As a** backend developer,
**I want** a microservices architecture with clear domain boundaries,
**So that** each service can be developed, deployed, and scaled independently.

**Acceptance Criteria:**
- 4 core services + 1 API gateway:
  - **alert-service**: Consumes alerts from RabbitMQ, stores in PostgreSQL, pushes via MQTT
  - **device-service**: Manages edge nodes, cameras, zones, and worker tags
  - **auth-service**: JWT-based authentication, user management, role-based access
  - **siren-service**: Receives siren commands from frontend/app, publishes to RabbitMQ
  - **api-gateway**: Spring Cloud Gateway routing all external requests
- Each service is a Spring Boot 3.2+ application with:
  - Spring Data JPA for PostgreSQL
  - Spring AMQP for RabbitMQ consumption
  - Spring Security with JWT validation
  - OpenAPI 3.0 documentation (springdoc-openapi)
  - Health checks at `/actuator/health`
  - Dockerized with multi-stage build

**Service Communication Matrix:**

```
┌──────────────┐     AMQP      ┌──────────────┐
│  RabbitMQ    │──────────────▶│ alert-service │──▶ PostgreSQL
│              │               │               │──▶ MQTT (push)
│  (MQTT in)   │               └──────────────┘
│              │     AMQP      ┌──────────────┐
│              │◀──────────────│ siren-service │◀── REST API
│  (MQTT out)  │               └──────────────┘
└──────────────┘
                               ┌──────────────┐
        REST ─────────────────▶│device-service │──▶ PostgreSQL
                               └──────────────┘
                               ┌──────────────┐
        REST ─────────────────▶│ auth-service  │──▶ PostgreSQL
                               └──────────────┘
```

**Alert Service — RabbitMQ Consumer (Java):**

```java
@Service
@Slf4j
public class AlertConsumerService {

    @Autowired private AlertRepository alertRepository;
    @Autowired private MqttPushService mqttPushService;
    @Autowired private WebSocketService wsService;

    @RabbitListener(queues = "alert.critical", priority = "10")
    public void handleCriticalAlert(String message) {
        processAlert(message, "critical");
    }

    @RabbitListener(queues = "alert.high")
    public void handleHighAlert(String message) {
        processAlert(message, "high");
    }

    private void processAlert(String message, String expectedPriority) {
        try {
            AlertPayload payload = objectMapper.readValue(message, AlertPayload.class);
            
            // Store in PostgreSQL
            Alert alert = Alert.builder()
                .id(UUID.fromString(payload.getAlertId()))
                .nodeId(payload.getNodeId())
                .cameraId(payload.getCameraId())
                .zoneId(payload.getZoneId())
                .zoneName(payload.getZoneName())
                .zoneType(payload.getZoneType())
                .priority(payload.getPriority())
                .detectionClass(payload.getDetectionClass())
                .confidence(payload.getConfidence())
                .snapshotUrl(payload.getSnapshotUrl())
                .status("new")
                .createdAt(Instant.ofEpochSecond((long) payload.getTimestamp()))
                .build();
            
            alertRepository.save(alert);
            
            // Push notification to Android app via MQTT
            mqttPushService.sendAlertNotification(alert);
            
            // Real-time WebSocket update to React dashboard
            wsService.broadcastAlert(alert);
            
            log.info("Processed {} alert: {} in zone {}",
                alert.getPriority(), alert.getDetectionClass(), alert.getZoneName());
            
        } catch (Exception e) {
            log.error("Failed to process alert: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e);
        }
    }
}
```

**Siren Service — Command Publisher (Java):**

```java
@RestController
@RequestMapping("/api/v1/siren")
@Slf4j
public class SirenController {

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private SirenActionRepository sirenActionRepo;

    @PostMapping("/trigger")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SirenResponse> triggerSiren(
            @RequestBody SirenRequest request,
            @AuthenticationPrincipal UserDetails user) {
        
        SirenCommand cmd = SirenCommand.builder()
            .command("trigger")
            .targetNode(request.getNodeId())
            .sirenUrl(request.getSirenUrl())
            .triggeredBy(user.getUsername())
            .timestamp(Instant.now())
            .build();
        
        // Publish to RabbitMQ → MQTT bridge → Edge Node
        rabbitTemplate.convertAndSend(
            "farm.commands",
            "farm.siren.trigger",
            objectMapper.writeValueAsString(cmd)
        );
        
        // Audit log
        sirenActionRepo.save(SirenAction.builder()
            .triggeredBy(UUID.fromString(user.getId()))
            .targetNode(request.getNodeId())
            .action("trigger")
            .alertId(request.getAlertId())
            .build());
        
        return ResponseEntity.ok(new SirenResponse("siren_triggered", request.getNodeId()));
    }
}
```

---

### Story 3.4 — MQTT Direct Push Notifications

**As a** mobile user,
**I want** instant push notifications on my Android phone,
**So that** I am alerted to critical farm events even when the app is backgrounded.

**Acceptance Criteria:**
- Android app maintains a persistent MQTT connection to the RabbitMQ broker
- Priority mapping: `critical` → high-priority MQTT QoS 1 (wakes device via foreground service), `high` → normal
- Notification payload includes: alert type, zone name, snapshot thumbnail URL
- Data payload includes: full alert JSON for in-app rendering
- MQTT client ID stored in `users` table, set on app login
- Android Foreground Service keeps the MQTT connection alive when app is backgrounded

---

## Epic 4: UI/UX Mock Screens & App Architecture

### Story 4.1 — React Admin Dashboard Architecture

**Technology Stack:**
- React 18+ with TypeScript
- Tailwind CSS for styling
- Recharts for analytics charts
- React-Leaflet for zone map overlay
- WebSocket (SockJS + STOMP) for real-time alert feed
- React Query for API state management
- React Router v6 for navigation

**Navigation Structure:**

```
┌─────────────────────────────────────────────────────┐
│  SIDEBAR                    MAIN CONTENT            │
│  ┌─────────────┐  ┌──────────────────────────────┐  │
│  │ 🏠 Dashboard │  │                              │  │
│  │ 🚨 Alerts    │  │   (Page content renders      │  │
│  │ 📹 Cameras   │  │    here based on route)      │  │
│  │ 🗺️ Zones     │  │                              │  │
│  │ 📡 Devices   │  │                              │  │
│  │ 🔊 Siren     │  │                              │  │
│  │ 👷 Workers   │  │                              │  │
│  │ 📊 Analytics │  │                              │  │
│  │ ⚙️ Settings  │  │                              │  │
│  └─────────────┘  └──────────────────────────────┘  │
│  ┌─────────────┐                                    │
│  │ Node A: 🟢  │  HEADER BAR                       │
│  │ Node B: 🟢  │  [Search] [Notifications 🔴3] [👤]│
│  └─────────────┘                                    │
└─────────────────────────────────────────────────────┘
```

**See: `dashboard-mockup.jsx` for interactive wireframe.**

---

### Story 4.2 — Android App Architecture

**Technology Stack:**
- Kotlin with Jetpack Compose UI
- MVVM architecture with Hilt DI
- Retrofit for REST API calls
- HiveMQ MQTT client for real-time subscriptions
- MQTT (real-time push via RabbitMQ broker)
- Room database for offline alert cache
- Navigation Compose for screen routing

**Screen Flow:**

```
Splash → Login → Main (Bottom Nav)
                    ├── Home (Alert Feed)
                    │     └── Alert Detail
                    │           └── Trigger Siren
                    ├── Cameras (Live Grid)
                    │     └── Camera Fullscreen
                    ├── Siren Control
                    │     ├── Trigger by Node
                    │     └── Siren History
                    ├── Devices (Node Status)
                    └── Profile / Settings
```

**See: `android-mockup.jsx` for interactive wireframe.**

---

## Epic 5: Deployment Strategy & CI/CD Pipeline

### Story 5.1 — Docker Compose Production Stacks

**As a** DevOps engineer,
**I want** separate Docker Compose stacks for Edge and Cloud,
**So that** each environment can be deployed and updated independently.

**Cloud VPS docker-compose.yml:**

```yaml
version: "3.8"
services:
  postgres:
    image: postgres:16
    volumes:
      - pg_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    environment:
      POSTGRES_DB: sudarshanchakra
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASS}
    restart: always
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3-management
    ports:
      - "1883:1883"
      - "8883:8883"
      - "15672:15672"
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
      - ./rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf
      - ./rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins
    restart: always

  api-gateway:
    image: ghcr.io/mandnargitech/api-gateway:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - auth-service
      - alert-service
      - device-service
      - siren-service
    restart: always

  alert-service:
    image: ghcr.io/mandnargitech/alert-service:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/sudarshanchakra
      - SPRING_RABBITMQ_HOST=rabbitmq
      - SPRING_RABBITMQ_HOST=rabbitmq
    depends_on:
      postgres: { condition: service_healthy }
      rabbitmq: { condition: service_started }
    restart: always

  device-service:
    image: ghcr.io/mandnargitech/device-service:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/sudarshanchakra
    depends_on:
      postgres: { condition: service_healthy }
    restart: always

  auth-service:
    image: ghcr.io/mandnargitech/auth-service:latest
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/sudarshanchakra
      - JWT_SECRET=${JWT_SECRET}
    depends_on:
      postgres: { condition: service_healthy }
    restart: always

  siren-service:
    image: ghcr.io/mandnargitech/siren-service:latest
    environment:
      - SPRING_RABBITMQ_HOST=rabbitmq
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/sudarshanchakra
    depends_on:
      postgres: { condition: service_healthy }
      rabbitmq: { condition: service_started }
    restart: always

  react-dashboard:
    image: ghcr.io/mandnargitech/dashboard:latest
    ports:
      - "443:443"
    volumes:
      - ./certs:/etc/nginx/certs:ro
    restart: always

  nginx-proxy:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      - api-gateway
      - react-dashboard
    restart: always

volumes:
  pg_data:
  rabbitmq_data:
```

---

### Story 5.2 — CI/CD Pipeline (GitHub Actions)

**As a** developer,
**I want** automated build, test, and deploy pipelines,
**So that** code changes are validated and deployed to production automatically.

**Pipeline Overview:**

```
Push to main
  │
  ├── [Java Services] ──▶ Build → Test → Docker Build → Push to GHCR → Deploy to VPS
  │
  ├── [React Dashboard] ──▶ Build → Lint → Docker Build → Push to GHCR → Deploy to VPS
  │
  ├── [Edge AI Python] ──▶ Lint → Test → Docker Build → Push to GHCR
  │                         (Manual deploy to Edge Nodes via SSH)
  │
  └── [Android App] ──▶ Build → Test → Sign APK → Distribute
```

**GitHub Actions Workflow (Java services):**

```yaml
name: Build & Deploy Java Services
on:
  push:
    branches: [main]
    paths: ['backend/**']

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [alert-service, device-service, auth-service, siren-service, api-gateway]
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build & Test
        run: cd backend/${{ matrix.service }} && ./gradlew build test
      
      - name: Docker Build & Push
        run: |
          echo "${{ secrets.GHCR_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker build -t ghcr.io/mandnargitech/${{ matrix.service }}:latest backend/${{ matrix.service }}
          docker push ghcr.io/mandnargitech/${{ matrix.service }}:latest
      
      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1
        with:
          host: vivasvan-tech.in
          username: deploy
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/sudarshanchakra
            docker compose pull ${{ matrix.service }}
            docker compose up -d ${{ matrix.service }}
```

---

### Story 5.3 — Edge Node Update Strategy

**As a** field operations manager,
**I want** a safe update mechanism for Edge Nodes,
**So that** AI models and software can be updated without farm visits.

**Acceptance Criteria:**
- Edge nodes pull new Docker images from GHCR via VPN
- Update triggered by MQTT command: `farm/admin/update`
- Update process:
  1. Pull new image
  2. Stop current container
  3. Start new container
  4. Health check (ping VPN, verify YOLO loads)
  5. If health check fails → rollback to previous image
- Model updates: new `.pt` files pushed to a shared volume via rsync over VPN
- Update logs published to `farm/events/update` topic

---

### Story 5.4 — Monitoring & Observability

**As a** SRE,
**I want** centralized monitoring across edge and cloud,
**So that** I can detect and resolve issues before they impact farm safety.

**Stack:**
- **Prometheus** scraping all Spring Boot `/actuator/prometheus` endpoints
- **Grafana** dashboards for:
  - Alert volume by priority and zone
  - Edge Node GPU utilization and inference latency
  - MQTT message throughput
  - VPN tunnel latency and packet loss
  - PostgreSQL query performance
- **Loki** for centralized log aggregation
- **Alertmanager** for infrastructure alerts (disk full, service down, VPN drop)

---

## Appendix A: TP-Link Camera RTSP Configuration

### Supported Models & RTSP URL Formats

| Camera Model | Type | Resolution | RTSP Main Stream | RTSP Sub Stream (use this) |
|:-------------|:-----|:-----------|:-----------------|:---------------------------|
| VIGI C540-W | Pan/Tilt | 2K | `rtsp://user:pass@IP:554/stream1` | `rtsp://user:pass@IP:554/stream2` |
| Tapo C210 | Indoor | 1080p | `rtsp://user:pass@IP:554/stream1` | `rtsp://user:pass@IP:554/stream2` |
| Tapo C320WS | Outdoor | 2K | `rtsp://user:pass@IP:554/stream1` | `rtsp://user:pass@IP:554/stream2` |

**Critical Notes:**
- Always use `stream2` (sub-stream, 640×480) for AI inference — reduces bandwidth from ~4 Mbps to ~512 Kbps per camera and gives adequate resolution for YOLOv8n at 640×640 input
- Tapo cameras require enabling RTSP first: Tapo App → Camera Settings → Advanced → Device Account (set username/password)
- VIGI cameras use NVR or standalone admin credentials — RTSP enabled by default
- For 8 cameras at stream2 on a single Edge Node, total bandwidth is approximately 4 Mbps — well within WiFi or wired network capacity

### Camera Placement Guidelines for AI Detection

| Zone | Camera | Mount Height | Notes |
|:-----|:-------|:-------------|:------|
| Pond (Zero Tolerance) | VIGI C540-W | 3-4m | Lock Pan/Tilt on pond. Set FPS to 3.0 for faster child detection. |
| Snake/Scorpion Zone | VIGI C540-W | 1-1.5m | Low mount for ground-level hazard detection. |
| Perimeter/Gate | Tapo C320WS | 3m | Outdoor rated IP66. Starlight sensor for night. |
| Cattle Pen | Tapo C210 | 2.5m | Wide angle to cover entire pen boundary. |
| Storage Shed | Tapo C210 | 2m | Indoor — primary for fire/smoke detection. |

---

## Appendix B: ESP32/LoRa Firmware

### Overview

The `esp32_lora_tag.ino` firmware runs in two modes:

**Mode 1 — WORKER_BEACON:** Broadcasts `TAG:<id>,TYPE:WORKER_PING` every 5 seconds. Edge Node LoRa receiver checks this against `authorized_tags.json` to suppress intrusion alarms for known workers.

**Mode 2 — CHILD_SAFETY:** Broadcasts `TAG:<id>,TYPE:CHILD_PING,ACCEL:<g>` every 2 seconds with accelerometer data. Includes two-phase fall detection:
1. **Free-fall phase:** Acceleration magnitude drops below 0.3g (body in free fall experiences near-zero gravity)
2. **Impact phase:** Acceleration spikes above 2.5g within 500ms (hitting water surface or ground)

When both phases are detected, the tag immediately transmits `TYPE:FALL,PRIORITY:CRITICAL` three times for reliability and sounds a local buzzer.

### Hardware BOM (per tag)

| Component | Part | Approx. Cost |
|:----------|:-----|:-------------|
| MCU | ESP32 DevKit V1 | ₹350 |
| LoRa | SX1276 433MHz module | ₹250 |
| Accelerometer | MPU6050 breakout | ₹100 |
| Battery | 3.7V 1000mAh LiPo | ₹200 |
| Charger | TP4056 USB-C module | ₹50 |
| Enclosure | IP65 ABS box 80×50×25mm | ₹80 |

### Wiring Diagram

```
ESP32 DevKit V1
┌─────────────────────┐
│ GPIO 5  ──────── SCK │──→ SX1276 SCK
│ GPIO 19 ──────── MISO│──→ SX1276 MISO
│ GPIO 27 ──────── MOSI│──→ SX1276 MOSI
│ GPIO 18 ──────── NSS │──→ SX1276 NSS
│ GPIO 14 ──────── RST │──→ SX1276 RESET
│ GPIO 26 ──────── DIO0│──→ SX1276 DIO0
│                       │
│ GPIO 21 ──────── SDA │──→ MPU6050 SDA
│ GPIO 22 ──────── SCL │──→ MPU6050 SCL
│                       │
│ GPIO 4  ──────── BUZ │──→ Piezo Buzzer +
│ 3.3V    ──────── VCC │──→ SX1276 VCC, MPU6050 VCC
│ GND     ──────── GND │──→ SX1276 GND, MPU6050 GND
│ GPIO 35 ──────── VBAT│──→ Battery voltage divider
└─────────────────────┘
```

---

## Appendix C: Edge Flask GUI — Polygon Zone Editor

The `edge_gui.py` provides a browser-based polygon drawing tool at `http://<edge-node-ip>:5000`. Key features:

- **Live camera snapshots** refreshed every 2 seconds from the inference pipeline
- **Click-to-draw polygons** on camera images — coordinates saved in image space
- **Zone types:** intrusion, zero_tolerance, livestock_containment, hazard
- **CRUD operations** — add, view, delete zones per camera
- **Auto-reload** — zone engine reloads automatically when zones are saved, no restart required
- **No external dependencies** — pure HTML/JS/Canvas embedded in Flask template

See `cameras.json` for camera configuration with TP-Link RTSP URLs.
See `authorized_tags.json` for worker tag registry.

---

## Appendix D: Repository Structure

```
SudarshanChakra/
├── AlertManagement/          # Pi Zero 2W PA system (already deployed)
├── docs/                         # Complete implementation — all deliverables
│   ├── BLUEPRINT.md              # This document
│   ├── dashboard-mockup.jsx      # Interactive React admin dashboard wireframe
│   ├── android-mockup.jsx        # Interactive Android app wireframe (3 screens)
│   ├── farm_edge_node.py         # Main entrypoint (Dockerfile CMD)
│   ├── pipeline.py               # Multi-camera RTSP frame-skipping + YOLO inference
│   ├── zone_engine.py            # Virtual fence polygon engine (Shapely)
│   ├── lora_receiver.py          # ESP32 LoRa USB-Serial receiver
│   ├── alert_engine.py           # Central alert decision engine
│   ├── edge_gui.py               # Flask polygon drawing GUI (:5000)
│   ├── Dockerfile                # Edge AI container
│   ├── requirements.txt          # Python dependencies
│   ├── docker-compose.edge.yml   # Edge Node stack (VPN + AI)
│   ├── docker-compose.cloud.yml  # VPS stack (all services)
│   ├── db-schema.sql             # PostgreSQL schema
│   ├── vpn_health_monitor.py     # VPN tunnel health check
│   ├── rabbitmq.conf             # RabbitMQ broker config
│   ├── enabled_plugins           # RabbitMQ MQTT plugin
│   ├── esp32_lora_tag.ino        # ESP32 worker beacon + child fall detector
│   ├── cameras.json              # TP-Link RTSP camera configs
│   ├── zones.json                # Virtual fence zone definitions
│   ├── authorized_tags.json      # Worker/child tag registry
│   └── .env.example              # Environment variable template
├── edge/                         # (Future: production edge code)
├── backend/                      # (Future: Java Spring Boot microservices)
├── dashboard/                    # (Future: React.js admin dashboard)
├── android/                      # (Future: Kotlin Android app)
└── .github/workflows/            # (Future: CI/CD pipelines)
```
