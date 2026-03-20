# Simple Explanation: What is What?

## 🏗️ The 3 Main Parts

Think of your system like a **restaurant**:

### 1. **INFRA** (Infrastructure) = The Kitchen Equipment
**What it is:**
- PostgreSQL = Database (stores all your data: cameras, alerts, users)
- RabbitMQ = Message Bus (like a waiter carrying messages between services)

**When to build:** FIRST, before everything else
**How to start:**
```bash
./setup_and_build_all.sh deploy-infra
```
**What it does:** Starts 2 Docker containers (PostgreSQL + RabbitMQ)

---

### 2. **BACKEND** = The Kitchen Staff (5 Services)
**What it is:**
- 5 Java programs that handle:
  - **auth-service** (8083): Login/register users
  - **device-service** (8082): Manage cameras, zones, devices
  - **alert-service** (8081): Store alerts, send notifications
  - **siren-service** (8084): Control sirens
  - **api-gateway** (8080): Front door - all requests come here

**When to build:** AFTER infra is running
**How to start:**
```bash
./setup_and_build_all.sh deploy-backend
```
**What it does:** Starts 5 Java services that talk to PostgreSQL and RabbitMQ

---

### 3. **EDGE** = The Camera Watcher
**What it is:**
- Python program that:
  - Connects to your camera (Tapo C110 at 192.168.68.56)
  - Watches video stream
  - Detects objects (snakes, intruders, etc.) using AI
  - Sends alerts when something is detected

**When to build:** AFTER backend is running
**How to start:**
```bash
cd edge
python3 farm_edge_node.py
```
**What it does:** Watches camera, detects threats, sends alerts via MQTT

---

## 🔄 How They Talk to Each Other

```
┌─────────────┐
│   CAMERA    │  (Tapo C110 at 192.168.68.56)
│  (Physical) │
└──────┬──────┘
       │ RTSP video stream
       ▼
┌─────────────┐
│    EDGE     │  (Python, watches camera, runs AI)
│  (Python)   │
└──────┬──────┘
       │ MQTT messages (alerts)
       ▼
┌─────────────┐
│  RABBITMQ   │  (Message bus - part of INFRA)
│  (MQTT)     │
└──────┬──────┘
       │ Messages
       ▼
┌─────────────┐
│   BACKEND   │  (Java services)
│  (5 services)│
└──────┬──────┘
       │ Stores data
       ▼
┌─────────────┐
│ POSTGRESQL  │  (Database - part of INFRA)
│  (Database) │
└─────────────┘
```

---

## 📡 What is MQTT?

**MQTT = Message Queue Telemetry Transport**

**Simple explanation:**
- Like WhatsApp for machines
- Edge sends messages: "Snake detected at camera 1!"
- RabbitMQ delivers the message
- Backend receives it and stores in database

**Where it lives:** Inside RabbitMQ (part of INFRA)

**Port:** 1883 (for MQTT messages)

---

## 🚀 Simple Deployment Order

### Step 1: Start INFRA (Database + Message Bus)
```bash
./setup_and_build_all.sh deploy-infra
```
**Wait for:** PostgreSQL and RabbitMQ to be ready (30 seconds)

---

### Step 2: Start BACKEND (5 Java Services)
```bash
./setup_and_build_all.sh deploy-backend
```
**Wait for:** All 5 services to start (1-2 minutes)

---

### Step 3: Start EDGE (Camera Watcher)
```bash
cd edge
python3 farm_edge_node.py
```
**What happens:** Edge connects to camera, starts watching

---

## 📦 All Buildable Modules in This Project

### Top-Level Build Steps (7 total)

When you run `./setup_and_build_all.sh build-all`, it builds these in order:

| # | Build Step | Folder | What It Is |
|---|------------|--------|------------|
| 1 | **`build-cloud`** | `cloud/` | Validate scripts/configs + start PostgreSQL + RabbitMQ (Docker) |
| 2 | **`build-backend`** | `backend/` | 5 Spring Boot microservices (Gradle) |
| 3 | **`build-dashboard`** | `dashboard/` | React/Vite web UI (npm build) |
| 4 | **`build-edge`** | `edge/` | Python edge AI (deps + lint + syntax check) |
| 5 | **`build-android`** | `android/` | Kotlin app → debug APK (Gradle) |
| 6 | **`build-alertmgmt`** | `AlertManagement/` | PA system scripts (Python syntax check) |
| 7 | **`build-firmware`** | `firmware/` | 3 ESP32 sketches (arduino-cli compile) |

**Command to build everything:**
```bash
./setup_and_build_all.sh build-all
```

---

### Backend: 5 Gradle Modules (Microservices)

Inside `backend/`, these are separate Gradle subprojects:

1. **`api-gateway`** (port 8080) - Front door, routes requests to other services
2. **`alert-service`** (port 8081) - Stores alerts, sends notifications
3. **`device-service`** (port 8082) - Manages cameras, zones, devices, water tanks/motors
4. **`auth-service`** (port 8083) - User login/registration, JWT tokens
5. **`siren-service`** (port 8084) - Controls sirens via RabbitMQ

**Build individually:**
```bash
cd backend
./gradlew :api-gateway:build
./gradlew :alert-service:build
# etc.
```

**Build all backend:**
```bash
cd backend
./gradlew build -x test
```

---

### Firmware: 3 ESP32 Sketches

Inside `firmware/`, these are Arduino sketches:

1. **`esp32_lora_bridge_receiver`** - LoRa bridge receiver
2. **`esp32_lora_tag`** - LoRa worker tag
3. **`worker_beacon`** - Worker beacon & child fall detector

**Build individually:**
```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32_lora_bridge_receiver
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32_lora_tag
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/worker_beacon
```

**Build all firmware:**
```bash
./setup_and_build_all.sh build-firmware
```

---

### Summary

- **7 top-level build steps** (cloud, backend, dashboard, edge, android, alertmgmt, firmware)
- **5 backend services** (api-gateway, alert-service, device-service, auth-service, siren-service)
- **3 firmware sketches** (bridge_receiver, lora_tag, worker_beacon)
- **1 dashboard** (React app)
- **1 edge** (Python app)
- **1 android** (Kotlin app)

---

## ✅ How to Check if Everything Works

### Check INFRA:
```bash
docker ps
# Should see: postgres and rabbitmq containers
```

### Check BACKEND:
```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

### Check EDGE:
```bash
curl http://localhost:5000/health
# Should return: OK
```

### Check MQTT (RabbitMQ):
```bash
# Open browser: http://localhost:15672
# Login: admin / devpassword123
# Look for MQTT connections
```

---

## 🎯 Your Specific Setup

**You want to:**
1. Deploy backend + edge on same machine
2. Test with 1 camera (Tapo C110 at 192.168.68.56)
3. Existing backend on port 9080 (via nginx)

**Simple commands:**
```bash
# 1. Start database + message bus
./setup_and_build_all.sh deploy-infra

# 2. Start backend services
./setup_and_build_all.sh deploy-backend

# 3. Register your camera
./scripts/register_camera.sh cam-tapo-c110-01 "Test Camera" \
  "rtsp://administrator:interOP@123@192.168.68.56:554/stream2" \
  edge-node-local "TP-Link Tapo C110"

# 4. Start edge (watches camera)
cd edge
python3 farm_edge_node.py
```

---

## ❓ Common Questions

**Q: Do I need to "build" infra?**
A: No, just START it. It's Docker containers, not code to compile.

**Q: Do I need to "build" backend?**
A: Yes, first run: `cd backend && ./gradlew build -x test`
Then start: `./setup_and_build_all.sh deploy-backend`

**Q: Do I need to "build" edge?**
A: No, just install Python packages: `pip install -r requirements.txt`
Then run: `python3 farm_edge_node.py`

**Q: Where is MQTT?**
A: Inside RabbitMQ. When you start INFRA, RabbitMQ starts with MQTT plugin enabled.

**Q: What port is MQTT?**
A: Port 1883 (RabbitMQ exposes MQTT on this port)

**Q: Why do I need nginx on 9080?**
A: Your old backend is there. New backend runs on 8080. Nginx forwards requests from 9080 → 8080.

---

## 🎬 One Command to Rule Them All

If you want everything automated:
```bash
./scripts/deploy_local.sh
```

This does:
1. ✅ Start INFRA
2. ✅ Build + Start BACKEND
3. ✅ Register CAMERA
4. ✅ Start EDGE

Just run it and wait!

---

## 📚 More Deployment Details

For detailed deployment instructions for **all 7 components** (cloud, backend, dashboard, edge, android, alertmgmt, firmware), see:
- **`docs/DEPLOY_ALL_COMPONENTS.md`** — Complete deployment guide for every component
