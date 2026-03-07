# SudarshanChakra — System Architecture Document

## Enterprise Smart Farm Hazard Detection & Security System

---

## 1. System Overview

SudarshanChakra is a multi-tier IoT + Edge AI + Cloud platform for real-time farm security monitoring. The system detects snakes, scorpions, fire/smoke, intruders, monitors livestock containment and child safety around a pond on a 3000 sq ft agricultural farm in Sanga Reddy, India.

### 1.1 Architecture Tiers

```
┌─────────────────────────────────────────────────────────────────┐
│  TIER 1: SENSING LAYER                                          │
│  TP-Link RTSP cameras (8×) + ESP32/LoRa wearable tags (4×)     │
└────────────────────────────────┬────────────────────────────────┘
                                 │ RTSP H.264 + LoRa 433MHz
┌────────────────────────────────▼────────────────────────────────┐
│  TIER 2: EDGE AI LAYER                                          │
│  2× GPU PCs (i5-10400, RTX 3060 12GB, 32GB RAM)                │
│  Docker: YOLOv8n TensorRT + Zone Engine + Flask GUI             │
└────────────────────────────────┬────────────────────────────────┘
                                 │ MQTT over OpenVPN (10.8.0.0/24)
┌────────────────────────────────▼────────────────────────────────┐
│  TIER 3: CLOUD LAYER (vivasvan-tech.in VPS)                     │
│  PostgreSQL 16 + RabbitMQ 3 + 5× Spring Boot microservices     │
│  React Dashboard + Nginx reverse proxy                          │
└────────────────────────────────┬────────────────────────────────┘
                                 │ HTTPS + MQTTS
┌────────────────────────────────▼────────────────────────────────┐
│  TIER 4: USER INTERFACE LAYER                                    │
│  React web dashboard + Android app (Kotlin/Compose)             │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Single YOLOv8n model (not ensemble) | RTX 3060 budget: 8 cameras × 2.5 FPS requires <8ms/frame |
| TensorRT FP16 (not INT8) | FP16 gives 2.5× speedup with <0.5% mAP loss; INT8 drops 2-3% (too risky for safety) |
| MQTT over VPN (not direct internet) | Secure tunnel eliminates public MQTT exposure; auto-reconnects on ISP outage |
| Android foreground MQTT service (not FCM) | Real-time push delivery while app is backgrounded without vendor push dependency |
| Bottom-center point-in-polygon (not bbox center) | Feet position accurately represents where a person IS standing |
| Microservices (not monolith) | Independent deployment of alert, device, auth, siren services |
| PostgreSQL (not NoSQL) | Strong schema for audit trail; JSONB for flexible metadata |

---

## 2. Component Architecture

### 2.1 Edge AI Node

**Hardware:** Intel i5-10400, NVIDIA RTX 3060 12GB, 32GB RAM, USB ESP32 LoRa receiver

**Software Stack:**
```
Docker Container: ultralytics/ultralytics (CUDA + cuDNN)
├── farm_edge_node.py        ← Main orchestrator (CMD entrypoint)
├── pipeline.py              ← Multi-camera RTSP grabbing + YOLO inference
├── zone_engine.py           ← Virtual fence polygon checks (Shapely)
├── lora_receiver.py         ← ESP32 LoRa USB-Serial tag receiver
├── alert_engine.py          ← Central decision engine (fusion + dedup + publish)
├── detection_filters.py     ← 4-layer false positive reduction
└── edge_gui.py              ← Flask web GUI for polygon drawing (:5000)
```

**Data Flow:**
```
Camera RTSP → CameraGrabber threads → Frame Queue → YOLO Inference
    → Detection → Filters → Zone Check → LoRa Fusion → MQTT Publish
```

**Threading Model:**
- 1 thread per camera (CameraGrabber) — GIL released during cv2.read()
- 1 main thread for inference loop
- 1 daemon thread for Flask GUI
- 1 daemon thread for heartbeat
- 1 daemon thread for LoRa receiver
- 1 daemon thread for MQTT client loop

### 2.2 Cloud VPS

**Services (Docker Compose):**

| Container | Image | Port | Purpose |
|-----------|-------|------|---------|
| postgres | postgres:16-alpine | 5432 | Alert/device/user storage |
| rabbitmq | rabbitmq:3-management | 5672, 1883, 15672 | Message broker (AMQP + MQTT) |
| api-gateway | Spring Boot | 8080 | Route API requests |
| alert-service | Spring Boot | 8081 | Consume alerts, store, push via MQTT |
| device-service | Spring Boot | 8082 | Edge node/camera/zone CRUD |
| auth-service | Spring Boot | 8083 | JWT auth, user management |
| siren-service | Spring Boot | 8084 | Siren commands → RabbitMQ |
| react-dashboard | Nginx | 443 | Static React SPA |
| nginx-proxy | nginx:alpine | 80, 443 | TLS termination, reverse proxy |

### 2.3 Messaging Architecture

**Exchanges:**
- `farm.alerts` (topic) — Edge → Cloud alert routing
- `farm.commands` (direct) — Cloud → Edge siren commands
- `farm.events` (topic) — Status, heartbeat, suppression events
- `farm.dead-letter` (fanout) — Failed message catch-all

**Queue Routing:**
```
farm/alerts/critical  → alert.critical queue (priority=10, TTL=24h)
farm/alerts/high      → alert.high queue (TTL=24h)
farm/alerts/warning   → alert.warning queue (TTL=12h)
farm.siren.trigger    → siren.commands queue
farm.siren.stop       → siren.commands queue
node.*                → node.events queue (TTL=1h)
worker_identified     → worker.suppression queue (TTL=24h)
```

### 2.4 Database Schema

10 tables across 3 domains:

**Authentication:** `users`
**Devices:** `edge_nodes`, `cameras`, `zones`, `worker_tags`
**Operations:** `alerts`, `siren_actions`, `suppression_log`, `node_health_log`, `daily_alert_summary`

Key indexes optimized for: priority+time queries, active alert filtering, zone-based lookups.

### 2.5 Network Architecture

```
Farm Site (Sanga Reddy)              Cloud VPS (vivasvan-tech.in)
┌─────────────────────┐              ┌─────────────────────┐
│ Edge Node A         │──OpenVPN────▶│ OpenVPN Server      │
│ 10.8.0.10           │  Tunnel      │ 10.8.0.1            │
│ 4 cameras           │              │                     │
├─────────────────────┤              │ PostgreSQL :5432     │
│ Edge Node B         │──OpenVPN────▶│ RabbitMQ :5672/1883 │
│ 10.8.0.11           │  Tunnel      │ Spring Boot :8080-84│
│ 4 cameras           │              │ Nginx :443          │
├─────────────────────┤              └─────────┬───────────┘
│ Raspberry Pi        │                        │
│ PA System (siren)   │                        │ HTTPS/MQTTS
│ Local MQTT :1883    │              ┌─────────▼───────────┐
└─────────────────────┘              │ Android App         │
                                     │ REST + MQTT          │
                                     └─────────────────────┘
```

---

## 3. AI Detection Architecture

### 3.1 Class Taxonomy (10 Classes)

| ID | Class | Priority | Alert Action |
|----|-------|----------|-------------|
| 0 | person | high | Zone check + LoRa fusion |
| 1 | child | critical | Zone check, never suppressed in zero_tolerance |
| 2 | cow | warning | Containment check (OUTSIDE = alert) |
| 3 | snake | high | Geometric + temporal filter |
| 4 | scorpion | high | Size filter + 2-frame temporal |
| 5 | fire | critical | Color histogram + 3-frame temporal |
| 6 | smoke | high | Texture + color + 3-frame temporal |
| 7 | dog | info | Suppression class (no alert) |
| 8 | vehicle | info | Suppression class (no alert) |
| 9 | bird | info | Suppression class (no alert) |

### 3.2 Detection Pipeline

```
YOLO Detection
  │
  ├── Layer 0: Confidence threshold (class-specific, night-adjusted)
  ├── Layer 1: Geometric validation (aspect ratio, size, position)
  ├── Layer 2: Color validation (fire HSV histogram, smoke texture)
  ├── Layer 3: Temporal confirmation (fire: 3/5s, scorpion: 2/3s)
  ├── Layer 4: Zone relevance (point-in-polygon check)
  ├── Layer 5: LoRa fusion (worker tag suppression)
  └── Layer 6: Deduplication (30s window per zone+class)
```

### 3.3 Zone Types

| Type | Trigger Condition | Worker Tag Suppression |
|------|------------------|----------------------|
| `intrusion` | Target INSIDE polygon | Yes — authorized workers suppress |
| `zero_tolerance` | Target INSIDE polygon | **No** — never suppressed (pond safety) |
| `livestock_containment` | Target OUTSIDE polygon | N/A |
| `hazard` | Target INSIDE polygon | Yes |

---

## 4. API Architecture

### 4.1 REST API (via API Gateway :8080)

```
Base: /api/v1

Auth:     POST /auth/login, /auth/register
Alerts:   GET /alerts, GET /alerts/:id, PATCH /alerts/:id/acknowledge
Devices:  GET /nodes, GET /cameras, GET/POST/DELETE /zones, GET/POST /tags
Siren:    POST /siren/trigger, POST /siren/stop, GET /siren/history
Users:    GET /users, PATCH /users/me/mqtt-client-id

WebSocket: ws://.../ws/alerts (STOMP over SockJS)
  → /topic/alerts (real-time new alerts)
  → /topic/nodes (node status changes)
```

### 4.2 MQTT Topics

```
Edge → Cloud:
  farm/alerts/{priority}     Alert payloads (QoS 1)
  farm/nodes/{id}/status     Online/offline status (retain)
  farm/nodes/{id}/heartbeat  GPU stats every 30s (QoS 0)
  farm/events/worker_identified  Suppression audit log

Cloud → Edge:
  farm/siren/trigger         Siren activation command
  farm/siren/stop            Siren stop command
  farm/admin/update          OTA update command

Edge → Cloud (acknowledgment):
  farm/siren/ack             Siren command acknowledgment
```

Android mobile clients subscribe to `farm/alerts/#` (primary) with `alerts/#` as a legacy fallback filter for backward compatibility.

---

## 5. Deployment Architecture

### 5.1 Container Strategy

- **Edge Nodes:** Docker with NVIDIA runtime, OpenVPN client in sidecar
- **Cloud VPS:** Docker Compose with healthchecks and restart policies
- **CI/CD:** GitHub Actions → GHCR → SSH deploy to VPS

### 5.2 Update Strategy

- **Cloud services:** `docker compose pull && docker compose up -d`
- **Edge AI model:** rsync over VPN + MQTT update command
- **Edge software:** Docker image pull over VPN
- **Firmware:** Physical USB flashing (no OTA currently)

---

## 6. Reliability & Recovery

| Scenario | Recovery Mechanism |
|----------|-------------------|
| Edge Node power loss | Docker `restart: always` + OpenVPN auto-reconnect (<90s) |
| VPN tunnel drop | `keepalive 10 60` + exponential backoff reconnect |
| RTSP camera offline | Per-camera exponential backoff (1s → 30s max) |
| MQTT broker down | Client auto-reconnect with Last Will for offline detection |
| LoRa serial disconnect | 5s retry loop with error logging |
| PostgreSQL overload | Connection pooling + 2G memory limit + healthcheck |

---

## 7. Local Development Bootstrap (GPU Workstation)

For full local setup/build on a developer GPU machine, use the repository root orchestrator:

```bash
./setup_and_build_all.sh
```

This script performs:
- toolchain and Docker checks
- PostgreSQL + RabbitMQ bootstrap and topology initialization
- backend build (`./gradlew clean build`)
- dashboard lint/build/test
- edge syntax/lint/test validation (lint/test are reported as warnings when baseline issues already exist)
- Android build + unit tests
- optional firmware compilation when `arduino-cli` is installed
