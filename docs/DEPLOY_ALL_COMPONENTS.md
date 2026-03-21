# How to Deploy All Components

This guide shows how to deploy **all 7 components** of SudarshanChakra.

---

## 🎯 Quick Answer

**One command to deploy everything:**
```bash
./setup_and_build_all.sh build-all
```

This builds all components. Then start them:
```bash
./setup_and_build_all.sh deploy-all
```

---

## 📋 Component-by-Component Guide

### 1. **CLOUD** (Infrastructure: PostgreSQL + RabbitMQ)

**What it is:** Database and message bus (required for everything else)

**Deploy:**
```bash
./setup_and_build_all.sh deploy-infra
```

**What it does:**
- Starts PostgreSQL container (port 5432)
- Starts RabbitMQ container (ports 5672, 1883, 15672)
- Initializes database schema
- Sets up RabbitMQ exchanges/queues

**Check if running:**
```bash
docker ps | grep -E "postgres|rabbitmq"
```

**Access:**
- PostgreSQL: `psql -h localhost -U scadmin -d sudarshanchakra`
- RabbitMQ Management: http://localhost:15672 (admin / devpassword123)

---

### 2. **BACKEND** (5 Java Spring Boot Services)

**What it is:** 5 microservices that handle API, auth, devices, alerts, sirens

**Deploy:**
```bash
# First, build all services
cd backend
./gradlew build -x test

# Then start all services
cd ..
./setup_and_build_all.sh deploy-backend
```

**What it does:**
- Starts 5 Java services:
  - `api-gateway` (8080)
  - `alert-service` (8081)
  - `device-service` (8082)
  - `auth-service` (8083)
  - `siren-service` (8084)

**Check if running:**
```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

**Logs:**
```bash
tail -f logs/service-*.log
```

---

### 3. **DASHBOARD** (React Web UI)

**What it is:** Web interface for viewing alerts, cameras, zones, etc.

**Deploy:**
```bash
cd dashboard
npm install
npm run build  # Production build → dist/
```

**For development:**
```bash
npm run dev  # Starts Vite dev server on port 3000
```

**Or use script:**
```bash
./setup_and_build_all.sh deploy-dashboard
```

**Access:**
- Dev: http://localhost:3000
- Production: Serve `dist/` folder with nginx/apache

**Check if running:**
```bash
curl http://localhost:3000
```

---

### 4. **EDGE** (Python AI Camera Watcher)

**What it is:** Python program that watches cameras and detects threats

**Deploy:**
```bash
cd edge

# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Configure camera (edit config/cameras.json)
# Then start:
python3 farm_edge_node.py
```

**Environment variables:**
```bash
export NODE_ID=edge-node-local
export VPN_BROKER_IP=localhost  # or 127.0.0.1
export MQTT_PORT=1883
export MQTT_USER=admin
export MQTT_PASS=devpassword123
export CONFIG_DIR=$(pwd)/config
export MODEL_DIR=$(pwd)/models
export FLASK_PORT=5000
```

**Check if running:**
```bash
curl http://localhost:5000/health
```

**Access GUI:**
- http://localhost:5000 (Flask web interface)

---

### 5. **ANDROID** (Kotlin Mobile App)

**What it is:** Android app for viewing alerts, controlling sirens, water pumps

**Deploy (Build APK):**
```bash
cd android

# Set Android SDK path
export ANDROID_HOME=/path/to/android-sdk  # or use repo android-sdk

# Build debug APK
./gradlew assembleDebug

# APK location:
# android/app/build/outputs/apk/debug/app-debug.apk
```

**Or use script:**
```bash
./setup_and_build_all.sh build-android
```

**Install on device:**
```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

**Check if built:**
```bash
ls -lh android/app/build/outputs/apk/debug/app-debug.apk
```

---

### 6. **ALERTMGMT** (Raspberry Pi PA System)

**What it is:** Python scripts for Raspberry Pi public address system

**Deploy:**
```bash
# Just validate syntax (no runtime deployment needed)
cd AlertManagement
python3 -m py_compile scripts/*.py
```

**Or use script:**
```bash
./setup_and_build_all.sh build-alertmgmt
```

**To actually run on Raspberry Pi:**
```bash
# Copy scripts to Raspberry Pi
scp -r AlertManagement/scripts/* pi@raspberry-pi:/home/pi/alertmgmt/

# On Raspberry Pi, install dependencies
pip3 install paho-mqtt pygame

# Run
python3 /home/pi/alertmgmt/alert_speaker.py
```

---

### 7. **FIRMWARE** (ESP32 Arduino Sketches)

**What it is:** C++ code for ESP32 microcontrollers (LoRa bridge, tags, beacons)

**Deploy (Compile):**
```bash
# Install arduino-cli first (if not installed)
# Then compile each sketch:

arduino-cli core install esp32:esp32
arduino-cli lib install "LoRa"

# Compile bridge receiver
arduino-cli compile --fqbn esp32:esp32:esp32 \
  firmware/esp32_lora_bridge_receiver

# Compile LoRa tag
arduino-cli compile --fqbn esp32:esp32:esp32 \
  firmware/esp32_lora_tag

# Compile worker beacon
arduino-cli compile --fqbn esp32:esp32:esp32 \
  firmware/worker_beacon
```

**Or use script:**
```bash
./setup_and_build_all.sh build-firmware
```

**To flash to ESP32:**
```bash
# Upload to device (replace /dev/ttyUSB0 with your port)
arduino-cli upload -p /dev/ttyUSB0 \
  --fqbn esp32:esp32:esp32 \
  firmware/esp32_lora_bridge_receiver
```

---

## 🚀 Deploy Everything at Once

### Option 1: Automated Script

```bash
# Build all components
./setup_and_build_all.sh build-all

# Deploy all services (infra + backend + dashboard + edge)
./setup_and_build_all.sh deploy-all
```

### Option 2: Step by Step

```bash
# 1. Infrastructure (database + message bus)
./setup_and_build_all.sh deploy-infra
sleep 30  # Wait for services to be ready

# 2. Backend (5 Java services)
./setup_and_build_all.sh deploy-backend
sleep 60  # Wait for services to start

# 3. Dashboard (web UI)
./setup_and_build_all.sh deploy-dashboard

# 4. Edge (camera watcher)
cd edge
source .venv/bin/activate
export NODE_ID=edge-node-local VPN_BROKER_IP=localhost MQTT_USER=admin MQTT_PASS=devpassword123
python3 farm_edge_node.py
```

---

## 📊 Deployment Summary Table

| Component | Build Command | Deploy Command | Port/Output |
|-----------|---------------|----------------|-------------|
| **Cloud** | `build-cloud` | `deploy-infra` | PostgreSQL: 5432, RabbitMQ: 5672/1883/15672 |
| **Backend** | `build-backend` | `deploy-backend` | 8080 (gateway), 8081-8084 (services) |
| **Dashboard** | `build-dashboard` | `deploy-dashboard` | 3000 (dev) or `dist/` (prod) |
| **Edge** | `build-edge` | Manual: `python3 farm_edge_node.py` | 5000 (Flask GUI) |
| **Android** | `build-android` | `adb install` APK | APK file |
| **AlertMgmt** | `build-alertmgmt` | Copy to Raspberry Pi | Runs on Pi |
| **Firmware** | `build-firmware` | Flash to ESP32 | Binary for ESP32 |

---

## ✅ Verification Checklist

After deploying, verify each component:

```bash
# 1. Cloud (Infrastructure)
docker ps | grep -E "postgres|rabbitmq"
curl http://localhost:15672  # RabbitMQ Management UI

# 2. Backend
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/nodes

# 3. Dashboard
curl http://localhost:3000  # Dev server
# or check dist/ folder exists (production build)

# 4. Edge
curl http://localhost:5000/health

# 5. Android
adb devices  # Should show connected device
adb shell pm list packages | grep sudarshanchakra

# 6. AlertMgmt
# Check on Raspberry Pi: ps aux | grep alert_speaker

# 7. Firmware
# Check compiled binaries in firmware/*/build/esp32.esp32.esp32/
```

---

## 🔧 Troubleshooting

### Backend won't start
- Check PostgreSQL is running: `docker ps | grep postgres`
- Check database exists: `psql -h localhost -U scadmin -d sudarshanchakra -c "\dt"`
- Check logs: `tail -f logs/service-*.log`

### Edge can't connect to RabbitMQ
- Verify RabbitMQ: `docker ps | grep rabbitmq`
- Check MQTT plugin: `docker exec rabbitmq rabbitmq-plugins list | grep mqtt`
- Test connection: `mosquitto_pub -h localhost -p 1883 -t test -m "hello"`

### Dashboard build fails
- Check Node version: `node --version` (needs 22+)
- Clear cache: `rm -rf node_modules package-lock.json && npm install`

### Android build fails
- Check ANDROID_HOME: `echo $ANDROID_HOME`
- Verify SDK: `ls $ANDROID_HOME/platforms/android-34`

### Firmware compile fails
- Check arduino-cli: `arduino-cli version`
- Verify ESP32 core: `arduino-cli core list | grep esp32`

---

## 📝 Notes

- **Cloud** must be deployed FIRST (everything depends on it)
- **Backend** must be deployed before **Edge** (edge sends alerts to backend)
- **Dashboard** can run independently (uses backend APIs)
- **Android** and **Firmware** are standalone (just build/compile)
- **AlertMgmt** runs on separate Raspberry Pi hardware

---

## 🎬 Full Example: Deploy Everything

```bash
# Step 1: Build all
./setup_and_build_all.sh build-all

# Step 2: Start infrastructure
./setup_and_build_all.sh deploy-infra

# Step 3: Start backend
./setup_and_build_all.sh deploy-backend

# Step 4: Start dashboard (dev mode)
./setup_and_build_all.sh deploy-dashboard

# Step 5: Start edge (in separate terminal)
cd edge
source .venv/bin/activate
export NODE_ID=edge-node-local VPN_BROKER_IP=localhost MQTT_USER=admin MQTT_PASS=devpassword123
python3 farm_edge_node.py

# Step 6: Install Android APK (if device connected)
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Step 7: Flash firmware (if ESP32 connected)
arduino-cli upload -p /dev/ttyUSB0 --fqbn esp32:esp32:esp32 firmware/esp32_lora_bridge_receiver
```

That's it! All components deployed. 🎉
