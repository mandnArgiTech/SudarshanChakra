# Deploy Edge AI on Your RTX 3060 Box — TODAY

## Your Questions Answered First

### Q: Can Tapo cameras use ONVIF?
**Yes.** Tapo cameras with ONVIF enabled expose an RTSP stream. Our edge system already uses RTSP — so ONVIF works out of the box. You don't need any ONVIF library. ONVIF is just the standard that tells you the RTSP URL format.

### Q: How do I use the Tapo camera account?
The "Device Account" you created in the Tapo app (username + password) IS the RTSP authentication. You put it in the camera URL like this:

```
rtsp://<your-username>:<your-password>@<camera-ip>:554/stream1
```

For example, if you created account `farmadmin` with password `MyPass123` and camera IP is `192.168.1.201`:

```
rtsp://farmadmin:MyPass123@192.168.1.201:554/stream1    ← Full quality (1080p/2K)
rtsp://farmadmin:MyPass123@192.168.1.201:554/stream2    ← Low quality (640×480, use THIS for AI)
```

**Always use `stream2`** for AI inference — it's lower resolution (640×480) which is exactly what YOLOv8 needs, and uses 8× less bandwidth than `stream1`.

### Q: Which guide do I follow?
**This one. Only this one.** Everything you need for today is below.

### Q: Does `setup_and_build_all.sh` build the edge?
**Yes**, it has a `build-edge` command that installs Python dependencies and validates code. But for running on your RTX 3060, we'll use Docker which is simpler. Follow the steps below.

---

## What You'll Do Today

```
Step 1: Find your camera IPs and test RTSP        (20 minutes)
Step 2: Edit cameras.json with YOUR camera details (10 minutes)
Step 3: Run the edge system on your RTX 3060       (10 minutes)
Step 4: Open the GUI and verify cameras work       (5 minutes)
Step 5: Draw virtual fence zones                   (15 minutes)
Step 6: Watch alerts flow                          (ongoing)
```

**Total: ~1 hour to fully deployed edge system**

---

## Step 1: Find Your Camera IPs and Test RTSP

### 1.1 Find Camera IPs

Open the **Tapo app** on your phone → tap each camera → **Settings** → **Device Info** → note the IP address.

Or scan your network:

```bash
# Install nmap if you don't have it
sudo apt install -y nmap

# Scan your network for cameras (they're usually on port 554)
nmap -p 554 192.168.1.0/24

# You'll see something like:
# 192.168.1.201  554/tcp open   rtsp
# 192.168.1.202  554/tcp open   rtsp
# ...
```

### 1.2 Verify Your Tapo Account Works

Before touching any code, test that your camera username/password works:

```bash
# Install ffmpeg and VLC if not already
sudo apt install -y ffmpeg vlc

# Test camera 1 — replace with YOUR credentials and IP
ffplay -rtsp_transport tcp "rtsp://farmadmin:MyPass123@192.168.1.201:554/stream2"

# You should see a live video window pop up.
# If it works → your credentials are correct.
# If "401 Unauthorized" → wrong username/password in Tapo app.
# If "connection refused" → wrong IP or RTSP not enabled.
```

**If you see video → move to Step 2.** Test each camera this way.

### 1.3 Enable ONVIF on Tapo Cameras (If Not Done)

In the **Tapo app**:
1. Tap camera → **Settings** (gear icon)
2. **Advanced Settings** → **Camera Account**
3. Create a username and password (this IS the RTSP login)
4. Go to **Advanced Settings** → **ONVIF** → Enable it

**VIGI cameras:** ONVIF and RTSP are enabled by default. Use the NVR admin credentials.

---

## Step 2: Edit cameras.json

### 2.1 Clone the Repo (If Not Done)

```bash
cd ~
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra
```

### 2.2 Edit Camera Config

```bash
nano edge/config/cameras.json
```

Change each camera entry to match YOUR cameras. Here's what to change:

```json
{
  "cameras": [
    {
      "id": "cam-01",
      "name": "Front Gate",
      "model": "TP-Link Tapo C320WS",
      "rtsp_url": "rtsp://farmadmin:MyPass123@192.168.1.201:554/stream2",
      "fps": 2.5,
      "enabled": true
    },
    {
      "id": "cam-02",
      "name": "Storage Shed",
      "model": "TP-Link Tapo C210",
      "rtsp_url": "rtsp://farmadmin:MyPass123@192.168.1.202:554/stream2",
      "fps": 2.0,
      "enabled": true
    },
    {
      "id": "cam-03",
      "name": "Pond Area",
      "model": "TP-Link VIGI C540-W",
      "rtsp_url": "rtsp://admin:VIGIpass@192.168.1.203:554/stream2",
      "fps": 3.0,
      "enabled": true
    }
  ]
}
```

**Change these for EACH camera:**
- `rtsp_url` → your username, password, and camera IP
- `name` → whatever you want to call it
- `enabled` → set to `false` for cameras not yet installed

**Start with 1-2 cameras** to test, then add more.

**If you only have 1 camera right now**, that's fine — just have 1 entry:

```json
{
  "cameras": [
    {
      "id": "cam-01",
      "name": "Test Camera",
      "model": "TP-Link Tapo C210",
      "rtsp_url": "rtsp://farmadmin:MyPass123@192.168.1.201:554/stream2",
      "fps": 2.0,
      "enabled": true
    }
  ]
}
```

Save and exit (`Ctrl+X`, then `Y`, then `Enter`).

---

## Step 3: Run the Edge System

### 3.1 Check Prerequisites

```bash
# Check NVIDIA driver
nvidia-smi
# Must show your RTX 3060

# Check Docker
docker --version
# Must show Docker 20+

# Check NVIDIA Container Toolkit (lets Docker use GPU)
docker run --rm --gpus all nvidia/cuda:12.2.0-base-ubuntu22.04 nvidia-smi
# Must show your GPU. If error, install toolkit:
```

**If NVIDIA Container Toolkit not installed:**
```bash
# Add NVIDIA repo
distribution=$(. /etc/os-release;echo $ID$VERSION_ID)
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg
curl -s -L https://nvidia.github.io/libnvidia-container/$distribution/libnvidia-container.list | \
    sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' | \
    sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list

sudo apt update
sudo apt install -y nvidia-container-toolkit
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker

# Verify
docker run --rm --gpus all nvidia/cuda:12.2.0-base-ubuntu22.04 nvidia-smi
```

### 3.2 Option A: Run with Real Cameras (Production Mode)

If your cameras are connected and Step 1 RTSP test worked:

```bash
cd ~/SudarshanChakra/edge

# First time: build the Docker image (takes 5-10 minutes)
docker compose build

# Start the edge system
docker compose up
```

**What you'll see:**
```
edge-ai  | ======================================================================
edge-ai  |   SudarshanChakra Edge Node: edge-node-a
edge-ai  |   VPN Broker: 10.8.0.1:1883
edge-ai  | ======================================================================
edge-ai  | Loading YOLO model (first run will build TensorRT engine)...
```

**First run takes 2-3 minutes** because it builds the TensorRT engine. Subsequent starts are instant.

**⚠️ VPN broker error?** If you don't have the VPS set up yet, the edge will keep retrying MQTT connection. That's OK — cameras and GUI still work. To run without MQTT:

```bash
# Set broker to localhost (won't connect, but won't crash)
VPN_BROKER_IP=127.0.0.1 docker compose up
```

### 3.3 Option B: Run in Dev Mode (No Cameras Needed)

If your cameras aren't set up yet or you just want to test:

```bash
cd ~/SudarshanChakra/edge

# Build and start dev mode
docker compose -f docker-compose.dev.yml up --build

# This starts:
# - Local MQTT broker (Mosquitto)
# - Edge AI with mock cameras (generates fake frames + detections)
# - No GPU needed, no cameras needed
```

### 3.4 Option C: Run Directly (No Docker)

If you prefer running Python directly on your machine:

```bash
cd ~/SudarshanChakra/edge

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies (with GPU support)
pip install -r requirements.txt

# Set environment variables
export NODE_ID=edge-node-a
export VPN_BROKER_IP=127.0.0.1    # Local MQTT or your broker IP
export MQTT_PORT=1883
export CONFIG_DIR=./config
export MODEL_DIR=./models
export FLASK_PORT=5000
export LORA_ENABLED=false          # No LoRa hardware yet
export DEV_MODE=false              # Set true for mock cameras
export LOG_LEVEL=DEBUG

# Start!
python farm_edge_node.py
```

---

## Step 4: Open the GUI and Verify

### 4.1 Open in Browser

```
http://localhost:5000
```

Or from another PC on the same network:
```
http://<edge-pc-ip>:5000
```

### 4.2 What You Should See

**Polygon Editor page:**
- Camera list on the left sidebar
- Live camera snapshot in the center (refreshes every 2 seconds)
- Click to draw polygon points
- Zone configuration form

**If camera snapshot is black/empty:**
- Check RTSP URL in cameras.json
- Run the ffplay test from Step 1.2 again
- Check camera is on the same network as the edge PC

### 4.3 Test with a Quick Detection

If running in production mode with real cameras, walk in front of a camera. Within 1-2 seconds the system should:
- Detect you as "person"
- Check if you're inside any zone (no zones drawn yet = no alert)

Check the terminal logs — you should see:
```
edge-ai | [INFO] pipeline: [cam-01] Connected to Test Camera at 2.0 FPS
edge-ai | [DEBUG] alert_engine: Total detections: 1, Zone violations: 0
```

---

## Step 5: Draw Virtual Fence Zones

### 5.1 Open the Polygon Editor

Go to `http://localhost:5000`

### 5.2 Select a Camera

Click on a camera in the left sidebar. The camera's live view appears.

### 5.3 Draw a Zone

1. **Click points on the image** to define the polygon boundary
   - Click at least 3 points (triangle = minimum polygon)
   - Click more points for complex shapes
   - Each click adds a numbered vertex

2. **Fill in the zone details:**
   - **Zone Name:** e.g., "Front Gate Perimeter"
   - **Zone Type:**
     - `intrusion` → alert when person enters
     - `zero_tolerance` → alert when ANYONE enters (never suppressed by worker tags)
     - `livestock_containment` → alert when cow EXITS this area
     - `hazard` → alert for snake/scorpion/fire inside
   - **Priority:** `critical`, `high`, or `warning`
   - **Target Classes:** comma-separated, e.g., `person, snake`

3. **Click "Save Zone"**

### 5.4 Recommended First Zones

Start with these 2-3 zones to test:

```
Zone 1: "Test Perimeter" on your main camera
  Type: intrusion
  Priority: high
  Target: person
  → Walk in front of camera → should trigger alert

Zone 2: "Test Hazard Zone" on a ground-level camera
  Type: hazard
  Priority: high
  Target: snake, scorpion
  → Place a rubber snake in view → should trigger alert

Zone 3: "Pond Safety" (if pond camera is connected)
  Type: zero_tolerance
  Priority: critical
  Target: person, child
  → Walk near pond → should trigger CRITICAL alert
```

---

## Step 6: Watch Alerts Flow

### 6.1 Check Terminal Logs

With zones drawn, walk in front of a camera. You should see:

```
[INFO] alert_engine: ALERT [HIGH] person: Front Gate Perimeter in cam-01 (87% conf)
```

### 6.2 If Using Dev Mode

Mock detections cycle automatically every 5 seconds. You'll see:

```
[INFO] mock_camera: MOCK DETECTION [cam-01] person: [200, 100, 300, 400] (87%) — Person walking through perimeter
[INFO] alert_engine: ALERT [HIGH] person: Front Gate Perimeter in cam-01 (87% conf)
```

### 6.3 Test Siren (If MQTT Broker Running)

```bash
# In another terminal:
# Install MQTT client
sudo apt install -y mosquitto-clients

# Subscribe to alerts (see them arrive)
mosquitto_sub -h localhost -t "farm/alerts/#" -v

# Trigger a siren manually
mosquitto_pub -h localhost -t "farm/siren/trigger" \
    -m '{"command":"trigger","siren_url":"test","triggered_by":"manual"}'

# You should see siren ack:
# farm/siren/ack {"node_id":"edge-node-a","status":"siren_activated",...}
```

---

## Troubleshooting

### Camera won't connect

```
Symptom: "Cannot open RTSP stream. Retrying..."
Fix:
  1. Test URL: ffplay -rtsp_transport tcp "rtsp://user:pass@ip:554/stream2"
  2. Check camera is on → power LED lit?
  3. Check same network → can you ping the camera IP?
     ping 192.168.1.201
  4. Check credentials → Tapo app → Settings → Camera Account
  5. Check ONVIF enabled → Tapo app → Advanced → ONVIF → ON
  6. Some Tapo cameras need firmware update for RTSP to work
```

### Docker GPU not working

```
Symptom: "NVIDIA runtime not found" or no GPU detected
Fix:
  sudo nvidia-ctk runtime configure --runtime=docker
  sudo systemctl restart docker
  docker run --rm --gpus all nvidia/cuda:12.2.0-base-ubuntu22.04 nvidia-smi
```

### MQTT connection failing

```
Symptom: "Cannot reach broker at 10.8.0.1"
Fix: This is expected if VPS/VPN not set up yet.
  Option A: Run local Mosquitto: docker run -d -p 1883:1883 eclipse-mosquitto:2
            Then set VPN_BROKER_IP=localhost
  Option B: Use dev mode (includes its own broker)
```

### No detections happening

```
Symptom: Camera connected, zone drawn, but no alerts
Fix:
  1. Check zone polygon covers the area where the object is
  2. Check target_classes includes the right class
  3. Check confidence threshold (default 0.40) — model might be below
  4. If using base YOLOv8n (no custom training): it knows person, cow, dog
     but NOT snake, scorpion, fire, smoke. Train a custom model (Step 8 in TRAINING_PLAYBOOK.md)
```

### Model not loading

```
Symptom: "No model found at /app/models/yolov8n_farm.engine"
Fix: First run uses the base YOLOv8n model. It auto-downloads:
  mkdir -p edge/models
  # The system downloads yolov8n.pt automatically on first run
  # Then exports to TensorRT (takes 2-3 minutes)
```

---

## What Works Without Custom Training?

The **base YOLOv8n model** (downloads automatically) can detect:

| Class | Works Out of Box? | Notes |
|:---|:---|:---|
| person | ✅ YES | Pre-trained on COCO |
| cow | ✅ YES | Pre-trained on COCO |
| dog | ✅ YES | Pre-trained on COCO |
| bird | ✅ YES | Pre-trained on COCO |
| vehicle | ✅ YES | Pre-trained on COCO (car, truck) |
| snake | ❌ NO | Needs custom training |
| scorpion | ❌ NO | Needs custom training |
| fire | ❌ NO | Needs custom training |
| smoke | ❌ NO | Needs custom training |
| child | ❌ NO | Needs custom training (detected as "person") |

**You can deploy today** with person, cow, dog, bird, vehicle detection. Intrusion detection and livestock containment work immediately. Snake/fire detection needs custom training (see `docs/TRAINING_PLAYBOOK.md`).

---

## File Reference

| File | What It Does | When to Edit |
|:---|:---|:---|
| `edge/config/cameras.json` | Camera IPs, credentials, FPS | **EDIT NOW** — put your camera details |
| `edge/config/zones.json` | Virtual fence polygons | Edit via GUI at :5000 (don't edit manually) |
| `edge/config/authorized_tags.json` | Worker LoRa tag IDs | Edit when you have ESP32 tags |
| `edge/docker-compose.yml` | Production Docker config | Edit NODE_ID if running Node B |
| `edge/docker-compose.dev.yml` | Dev mode Docker config | Use this if no cameras/GPU |
| `edge/Dockerfile` | Production container (GPU) | Don't edit |
| `edge/Dockerfile.dev` | Dev container (CPU) | Don't edit |
| `edge/farm_edge_node.py` | Main program | Don't edit |
| `edge/pipeline.py` | Camera + YOLO inference | Don't edit |
| `edge/zone_engine.py` | Virtual fence logic | Don't edit |
| `edge/alert_engine.py` | Alert decisions | Don't edit |
