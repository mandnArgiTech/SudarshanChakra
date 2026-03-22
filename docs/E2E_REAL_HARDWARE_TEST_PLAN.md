# End-to-End Real Hardware Test Plan

## Overview

Fully automated E2E testing across the entire SudarshanChakra stack using real hardware and emulated devices. One command runs everything.

```bash
./e2e/run_full_e2e.sh --cameras real --water real --siren real --android emulator
```

---

## Infrastructure

### VM 1 — Backend (VPS / Cloud VM)

Ubuntu 22.04+, 4 vCPU, 8 GB RAM, 50 GB disk.

Runs via Docker Compose:
- API Gateway (:8080)
- Auth Service
- Alert Service (+ WebSocket /ws/alerts)
- Device Service (water, motors, cameras, zones)
- Siren Service
- RabbitMQ (:5672 AMQP, :1883 MQTT, :15675 WebSocket MQTT)
- PostgreSQL (:5432)
- Dashboard (:3000)
- Simulator (:3001)

```yaml
# cloud/docker-compose.e2e.yml
services:
  postgres: ...
  rabbitmq: ...
  auth-service: ...
  alert-service: ...
  device-service: ...
  siren-service: ...
  api-gateway: ...
  dashboard: ...
  simulator: ...
```

### VM 2 — Edge Node

Ubuntu 22.04+, i5 / RTX 3060, 32 GB RAM, SSD.
Connected to LAN with cameras and ESP8266.

Runs via Docker Compose:
- Edge AI pipeline (2 real cameras via RTSP)
- Flask GUI (:5000) — live MJPEG, snapshots, PTZ API, zone API, recordings
- Video recorder (ffmpeg segments to SSD)
- PA siren controller (audio out to PC speaker via ALSA)
- Mosquitto bridge to VM 1 RabbitMQ

```yaml
# edge/docker-compose.e2e.yml
services:
  edge-ai:
    build: .
    devices:
      - /dev/video0   # If USB camera fallback
    volumes:
      - /dev/snd:/dev/snd           # PC speaker access
      - ./config:/app/config
      - /mnt/ssd:/data/video        # Recording storage
    environment:
      - MQTT_BROKER=<VM1_IP>
      - DEV_MODE=false
      - SIREN_AUDIO_DEVICE=hw:0,0   # Real speaker
```

### Physical Devices (LAN)

| Device | IP | Role |
|:-------|:---|:-----|
| Tapo cam-01 (VIGI C540-W) | 192.168.1.201 | PTZ camera, RTSP + ONVIF |
| Tapo cam-02 (Tapo C210) | 192.168.1.202 | PTZ camera, RTSP + ONVIF |
| ESP8266 water sensor | 192.168.1.150 | Publishes `tank1_{chipId}/water/level` every 60s |
| PC speaker | Connected to VM 2 | Receives siren audio via ALSA |

### Test Runner (Dev Machine or CI)

- Python 3.11+, Node.js 20+
- Playwright (TypeScript) for browser tests
- Maestro for Android emulator tests
- pytest as orchestrator
- paho-mqtt for MQTT assertion
- Android Studio emulator (API 34)

---

## Test Configuration

```yaml
# e2e/config/e2e_config.yml
backend:
  api_base: http://<VM1_IP>:8080
  dashboard_url: http://<VM1_IP>:3000
  simulator_url: http://<VM1_IP>:3001
  mqtt_broker: <VM1_IP>
  mqtt_port: 1883
  ws_mqtt_port: 15675

edge:
  flask_url: http://<VM2_IP>:5000
  cameras:
    - id: cam-01
      rtsp: rtsp://farmadmin:pass@192.168.1.201:554/stream2
      onvif_host: 192.168.1.201
      onvif_port: 2020
      has_ptz: true
    - id: cam-02
      rtsp: rtsp://farmadmin:pass@192.168.1.202:554/stream2
      onvif_host: 192.168.1.202
      onvif_port: 2020
      has_ptz: true

water:
  esp8266_ip: 192.168.1.150
  device_tag: tank1_a9ad51
  topic_prefix: water
  level_topic: tank1_a9ad51/water/level
  status_topic: tank1_a9ad51/water/status
  command_topic: tank1_a9ad51/water/command
  motor_command_topic: tank1_a9ad51/water/motor/command
  http_api: http://192.168.1.150/api
  poll_interval_sec: 60

siren:
  audio_device: hw:0,0
  verify_method: mqtt_ack  # or 'audio_capture'

auth:
  admin_user: testadmin
  admin_pass: TestPass123!
  viewer_user: testviewer
  viewer_pass: ViewerPass!

android:
  emulator_name: Pixel_7_API_34
  apk_path: android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Test Suites (11 suites, 68 tests)

### Suite 1: Infrastructure Health (8 tests)

Verifies all services are up before running functional tests.

```
test_01_backend_health
  ├── T1.1  API gateway responds 200 on /actuator/health
  ├── T1.2  Auth service: POST /api/v1/auth/login returns JWT
  ├── T1.3  Alert service WebSocket /ws/alerts connects
  ├── T1.4  RabbitMQ management API responds (:15672)
  ├── T1.5  PostgreSQL accepts connection
  ├── T1.6  Dashboard loads (HTTP 200 on /)
  ├── T1.7  Edge Flask /health returns {"status": "ok"}
  └── T1.8  MQTT broker accepts connection on :1883
```

**Implementation:** pytest + requests + paho-mqtt + websocket-client.

### Suite 2: Auth & RBAC (6 tests)

```
test_02_auth_rbac
  ├── T2.1  Login as admin → JWT contains role=admin, modules=[all]
  ├── T2.2  Login as viewer → JWT contains role=viewer
  ├── T2.3  Viewer cannot POST /api/v1/sirens/trigger (403)
  ├── T2.4  Viewer can GET /api/v1/alerts (200)
  ├── T2.5  Invalid credentials → 401
  └── T2.6  Expired JWT → 401
```

**Implementation:** pytest + requests.

### Suite 3: Real Camera — Live Feed (7 tests)

```
test_03_cameras_live [Playwright]
  ├── T3.1  Login to dashboard
  ├── T3.2  Navigate to /cameras → 2 camera cards visible
  ├── T3.3  cam-01 card shows MJPEG feed (img element loads, naturalWidth > 0)
  ├── T3.4  cam-02 card shows live feed
  ├── T3.5  cam-01 shows "REC" indicator
  ├── T3.6  Click cam-01 card → snapshot endpoint returns JPEG (content-type check)
  └── T3.7  /api/cameras/status returns 2 cameras with status=online
```

**Implementation:**
```typescript
// e2e/playwright/cameras.spec.ts
test('camera feed loads', async ({ page }) => {
  await page.goto(config.dashboard_url + '/cameras');
  const feed = page.locator('[data-testid="cam-01-feed"]');
  await expect(feed).toBeVisible();
  // Verify image has loaded (naturalWidth > 0)
  const width = await feed.evaluate((el: HTMLImageElement) => el.naturalWidth);
  expect(width).toBeGreaterThan(0);
});
```

### Suite 4: PTZ Camera Control (8 tests)

```
test_04_ptz_control [Playwright + ONVIF verification]
  ├── T4.1  Navigate to cam-01 PTZ page
  ├── T4.2  PTZ info shows "Pan 360°, Tilt 115°"
  ├── T4.3  Click Pan Right → ONVIF getStatus shows position changed
  ├── T4.4  Click Pan Left → position changed opposite direction
  ├── T4.5  Click Tilt Up → tilt value increased
  ├── T4.6  Zoom slider → zoom level changed via ONVIF
  ├── T4.7  Save preset "E2E Test Position" → preset appears in list
  └── T4.8  Goto preset "E2E Test Position" → position matches saved values
```

**Verification method:** After each PTZ command via dashboard, query ONVIF directly from pytest to verify the camera actually moved:

```python
# e2e/helpers/onvif_verify.py
from onvif import ONVIFCamera

def get_ptz_position(host, port, user, password):
    cam = ONVIFCamera(host, port, user, password)
    ptz = cam.create_ptz_service()
    media = cam.create_media_service()
    profile = media.GetProfiles()[0]
    status = ptz.GetStatus({"ProfileToken": profile.token})
    return {
        "pan": status.Position.PanTilt.x,
        "tilt": status.Position.PanTilt.y,
        "zoom": status.Position.Zoom.x,
    }
```

### Suite 5: Zone Drawing (6 tests)

```
test_05_zone_drawing [Playwright]
  ├── T5.1  Navigate to cam-01 zone drawing page
  ├── T5.2  Click 4 points on live feed → polygon SVG overlay appears
  ├── T5.3  Fill zone config: name="E2E Cow Zone", type=livestock_containment,
  │         priority=warning, classes=[cow, person]
  ├── T5.4  Click "Save Zone" → API returns 201
  ├── T5.5  Zone appears in /api/zones list
  └── T5.6  Edge node zone_engine has reloaded (GET /api/zones shows new zone)
```

**Implementation:**
```typescript
test('draw zone on live feed', async ({ page }) => {
  await page.goto(config.dashboard_url + '/cameras/cam-01/zones/draw');
  const feed = page.locator('[data-testid="zone-canvas"]');
  const box = await feed.boundingBox();
  // Click 4 polygon points
  await feed.click({ position: { x: box.width * 0.2, y: box.height * 0.2 } });
  await feed.click({ position: { x: box.width * 0.8, y: box.height * 0.2 } });
  await feed.click({ position: { x: box.width * 0.8, y: box.height * 0.8 } });
  await feed.click({ position: { x: box.width * 0.2, y: box.height * 0.8 } });
  // Fill config
  await page.fill('[data-testid="zone-name"]', 'E2E Cow Zone');
  await page.click('[data-testid="zone-type-livestock_containment"]');
  await page.click('[data-testid="zone-save"]');
  await expect(page.locator('text=Zone saved')).toBeVisible();
});
```

### Suite 6: Alert Pipeline — Simulator → Dashboard → Android (10 tests)

The core E2E flow. Simulator fires an event, dashboard and Android both receive it.

```
test_06_alert_pipeline
  ├── T6.1  Subscribe MQTT client to farm/alerts/#
  ├── T6.2  Open dashboard alerts page in Playwright
  ├── T6.3  Simulator fires "snake at storage" (critical)
  │         → POST /api/v1/alerts with mock=true
  ├── T6.4  MQTT client receives alert message within 3s
  ├── T6.5  Dashboard: alert card appears in real-time (WebSocket)
  │         → verify: priority=critical, class=snake, zone=storage
  ├── T6.6  Dashboard: alert count badge increments
  ├── T6.7  Android emulator: push notification appears
  │         → Maestro checks notification drawer
  ├── T6.8  Android: tap notification → deep links to alert detail
  ├── T6.9  Android: tap "Acknowledge" → alert status changes
  └── T6.10 Dashboard: same alert now shows "acknowledged" status
```

**Implementation — alert creation via simulator REST:**
```python
def fire_alert(api_base, token, scenario):
    payload = {
        "alert_id": str(uuid4()),
        "node_id": "edge-node-a",
        "camera_id": scenario["cam"],
        "zone_name": scenario["zone"],
        "detection_class": scenario["cls"],
        "confidence": scenario["conf"],
        "priority": scenario["priority"],
        "status": "new",
        "timestamp": time.time(),
        "mock": True, "simulator": True,
    }
    resp = requests.post(
        f"{api_base}/api/v1/alerts",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    assert resp.status_code in (200, 201)
    return payload["alert_id"]
```

**Android verification via Maestro:**
```yaml
# e2e/maestro/flows/alert_notification.yaml
appId: com.sudarshanchakra
---
- openNotifications
- assertVisible: "Snake detected at Storage"
- tapOn: "Snake detected at Storage"
- assertVisible: "CRITICAL"
- assertVisible: "cam-05"
- tapOn: "Acknowledge"
- assertVisible: "acknowledged"
```

### Suite 7: Siren — Real Audio (5 tests)

```
test_07_siren_real_audio
  ├── T7.1  Subscribe MQTT to farm/siren/ack
  ├── T7.2  Playwright: click "Trigger Siren" on dashboard siren page
  │         → Playwright verifies confirmation dialog appears
  │         → Playwright clicks "Confirm"
  ├── T7.3  MQTT client receives siren command on farm/siren/trigger
  ├── T7.4  Edge node processes → pa_controller.py plays audio via ALSA
  │         → MQTT ack received on farm/siren/ack within 5s
  │         → Ack payload: {"status": "siren_activated", "node_id": "edge-node-a"}
  ├── T7.5  Playwright: click "Stop Siren"
  │         → MQTT ack: {"status": "siren_stopped"}
```

**Audio verification (optional, for CI):**
```python
# Record 3 seconds of audio from the same sound card, verify non-silence
import subprocess, wave, struct

def verify_audio_played(device="hw:0,0", duration=3):
    subprocess.run([
        "arecord", "-D", device, "-d", str(duration),
        "-f", "S16_LE", "-r", "44100", "/tmp/siren_test.wav"
    ], timeout=duration + 2)
    with wave.open("/tmp/siren_test.wav") as w:
        frames = w.readframes(w.getnframes())
        samples = struct.unpack(f"<{len(frames)//2}h", frames)
        rms = (sum(s*s for s in samples) / len(samples)) ** 0.5
        assert rms > 100, f"Silence detected (RMS={rms}), siren did not play"
```

### Suite 8: Water Level — Real ESP8266 Sensor (8 tests)

Tests the full chain: ESP8266 → MQTT → RabbitMQ → device-service → PostgreSQL → REST API → Dashboard → Android.

```
test_08_water_level_real
  ├── T8.1  ESP8266 HTTP API responds: GET http://192.168.1.150/api/status → 200
  ├── T8.2  ESP8266 MQTT status: subscribe to tank1_a9ad51/water/status
  │         → verify {"online": true} received
  ├── T8.3  Wait for next water/level publish (max 90s, sensor publishes every 60s)
  │         → capture full JSON payload
  ├── T8.4  Verify payload structure:
  │         {percentFilled, percentRemaining, waterHeightMm, waterHeightCm,
  │          volumeLiters, volumeRemaining, distanceMm, distanceCm,
  │          temperatureC, valid, sensorOk, state, timestamp}
  ├── T8.5  Verify payload reaches PostgreSQL:
  │         → GET /api/v1/water/tanks/{tank_id}/readings?limit=1
  │         → latest reading matches MQTT payload values
  ├── T8.6  Playwright: navigate to /water → tank gauge shows matching percentage
  ├── T8.7  Android emulator: navigate to Water tab → WaterTanksScreen shows percentage
  └── T8.8  Verify ESP8266 HTTP API matches:
            → GET http://192.168.1.150/api/level returns same data as MQTT
```

**Implementation — waiting for real sensor publish:**
```python
import paho.mqtt.client as mqtt
import json, time

def wait_for_water_level(broker, topic, timeout=90):
    """Block until ESP8266 publishes a water level reading."""
    result = {"payload": None}
    
    def on_message(client, userdata, msg):
        result["payload"] = json.loads(msg.payload.decode())
    
    client = mqtt.Client()
    client.connect(broker, 1883)
    client.subscribe(topic)
    client.on_message = on_message
    client.loop_start()
    
    deadline = time.time() + timeout
    while result["payload"] is None and time.time() < deadline:
        time.sleep(1)
    
    client.loop_stop()
    client.disconnect()
    
    assert result["payload"] is not None, f"No water level received within {timeout}s"
    return result["payload"]

def test_water_level_full_chain():
    # Step 1: Capture real ESP8266 reading
    reading = wait_for_water_level(
        broker=config["backend"]["mqtt_broker"],
        topic=config["water"]["level_topic"],
    )
    assert "percentFilled" in reading
    assert reading["valid"] == True
    assert reading["sensorOk"] == True
    
    # Step 2: Verify it reached the database via REST API
    time.sleep(3)  # Allow processing time
    resp = requests.get(
        f"{config['backend']['api_base']}/api/v1/water/tanks/tank-1/readings?limit=1",
        headers={"Authorization": f"Bearer {token}"},
    )
    db_reading = resp.json()[0]
    assert abs(db_reading["percentFilled"] - reading["percentFilled"]) < 0.5
    
    # Step 3: Verify dashboard shows correct value (Playwright)
    # (called from the Playwright test that runs in parallel)
```

### Suite 9: Water Motor/Pump Control (4 tests)

```
test_09_pump_control
  ├── T9.1  Playwright: navigate to pump control page
  ├── T9.2  Playwright: click "Start Pump"
  │         → MQTT message on motor command topic
  │         → Dashboard shows pump status: RUNNING
  ├── T9.3  Playwright: click "Stop Pump"
  │         → Pump status changes to IDLE
  └── T9.4  Verify motor_run_log has new entry in database
```

### Suite 10: Video Recording & Playback (6 tests)

```
test_10_video_playback [Playwright]
  ├── T10.1  Edge /api/recordings/cam-01 returns dates list
  ├── T10.2  Edge /api/recordings/cam-01/{today} returns segment list
  ├── T10.3  Navigate to recordings page → select today → segments visible
  ├── T10.4  Click segment → <video> element loads (duration > 0)
  ├── T10.5  Video is seekable (seek to 50%, verify currentTime changed)
  └── T10.6  Storage status shows SSD usage > 0
```

### Suite 11: Android Full Flow (8 tests)

All Android tests run on the emulator via Maestro flows.

```
test_11_android [Maestro]
  ├── T11.1  Launch app → server settings screen → configure VM1 IP
  ├── T11.2  Login with admin credentials → dashboard loads
  ├── T11.3  Bottom nav shows all tabs (full subscription)
  ├── T11.4  Alerts tab → alert list visible
  ├── T11.5  Water tab → tank gauge shows percentage
  ├── T11.6  Cameras tab → camera cards visible
  ├── T11.7  Settings → user info shows role=admin
  └── T11.8  Back press → app minimizes (not killed)
```

**Maestro flow:**
```yaml
# e2e/maestro/flows/full_android_flow.yaml
appId: com.sudarshanchakra
---
- launchApp
# Configure server
- tapOn: "Configure Server"
- clearText
- inputText: "http://<VM1_IP>:8080"
- tapOn: "Save"
# Login
- tapOn:
    id: "username_field"
- inputText: "testadmin"
- tapOn:
    id: "password_field"
- inputText: "TestPass123!"
- tapOn: "Login"
# Verify dashboard
- assertVisible: "Alerts"
- assertVisible: "Cameras"
- assertVisible: "Water"
# Check water tab
- tapOn: "Water"
- assertVisible: "Tank"
- assertVisible: "%"
# Check cameras
- tapOn: "Cameras"
- assertVisible: "cam-01"
# Back press minimizes
- pressKey: back
- pressKey: back
- assertNotVisible: "Login"  # App still running, not killed
```

---

## Test Execution

### Directory Structure

```
e2e/
├── run_full_e2e.sh              # Master runner script
├── conftest.py                  # pytest fixtures: config, auth tokens, MQTT client
├── config/
│   └── e2e_config.yml           # VM IPs, camera configs, credentials
├── helpers/
│   ├── mqtt_helper.py           # MQTT subscribe, wait, assert
│   ├── onvif_verify.py          # ONVIF PTZ position verification
│   ├── audio_verify.py          # Siren audio capture verification
│   └── wait_for_service.py      # Health check polling
├── tests/
│   ├── test_01_health.py
│   ├── test_02_auth.py
│   ├── test_03_cameras.py       # Calls Playwright
│   ├── test_04_ptz.py           # Calls Playwright + ONVIF verify
│   ├── test_05_zones.py         # Calls Playwright
│   ├── test_06_alerts.py        # Playwright + Maestro + MQTT
│   ├── test_07_siren.py         # Playwright + MQTT + audio verify
│   ├── test_08_water.py         # MQTT wait + REST verify + Playwright
│   ├── test_09_pump.py          # Playwright + MQTT
│   ├── test_10_video.py         # Playwright
│   └── test_11_android.py       # Calls Maestro CLI
├── playwright/
│   ├── playwright.config.ts
│   ├── cameras.spec.ts
│   ├── ptz.spec.ts
│   ├── zones.spec.ts
│   ├── alerts.spec.ts
│   ├── siren.spec.ts
│   ├── water.spec.ts
│   ├── video.spec.ts
│   └── helpers/
│       └── login.ts             # Reusable login helper
├── maestro/
│   └── flows/
│       ├── server_config.yaml
│       ├── login.yaml
│       ├── alert_notification.yaml
│       ├── water_check.yaml
│       └── full_android_flow.yaml
└── docker/
    ├── docker-compose.e2e-backend.yml
    └── docker-compose.e2e-edge.yml
```

### Master Runner

```bash
#!/bin/bash
# e2e/run_full_e2e.sh
set -euo pipefail

echo "═══ SudarshanChakra E2E Test Suite ═══"
echo ""

# 1. Start backend VM services
echo "[1/6] Starting backend services on VM1..."
ssh vm1 "cd SudarshanChakra && docker compose -f cloud/docker-compose.e2e.yml up -d"

# 2. Start edge VM services
echo "[2/6] Starting edge services on VM2..."
ssh vm2 "cd SudarshanChakra && docker compose -f edge/docker-compose.e2e.yml up -d"

# 3. Wait for all services healthy
echo "[3/6] Waiting for services..."
python3 e2e/helpers/wait_for_service.py --config e2e/config/e2e_config.yml --timeout 120

# 4. Start Android emulator
echo "[4/6] Starting Android emulator..."
emulator -avd Pixel_7_API_34 -no-window -no-audio &
adb wait-for-device
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# 5. Run all tests
echo "[5/6] Running tests..."
cd e2e && pytest tests/ \
  --config config/e2e_config.yml \
  --html=reports/e2e_report.html \
  --junitxml=reports/e2e_results.xml \
  -v --tb=short \
  -x  # Stop on first failure (optional)

# 6. Cleanup
echo "[6/6] Cleanup..."
ssh vm1 "cd SudarshanChakra && docker compose -f cloud/docker-compose.e2e.yml down"
ssh vm2 "cd SudarshanChakra && docker compose -f edge/docker-compose.e2e.yml down"
adb emu kill

echo ""
echo "═══ E2E Complete. Report: e2e/reports/e2e_report.html ═══"
```

### Execution Order and Dependencies

```
Suite 1: Health         → MUST pass first (prerequisite for all)
Suite 2: Auth/RBAC      → Independent
Suite 3: Cameras Live   → Requires real cameras on LAN
Suite 4: PTZ            → Requires PTZ-capable camera
Suite 5: Zone Drawing   → Requires live camera feed
Suite 6: Alert Pipeline → Requires backend + dashboard + Android emulator
Suite 7: Siren          → Requires edge node + PC speaker
Suite 8: Water Level    → Requires real ESP8266 on LAN (60s wait for publish)
Suite 9: Pump Control   → Requires water module setup
Suite 10: Video         → Requires video recorder running + recordings on disk
Suite 11: Android       → Requires emulator + APK installed
```

**Parallel execution:** Suites 2-5 can run in parallel. Suite 6 must wait for Suites 3+5. Suite 8 runs independently (waits for real sensor). Suite 11 runs last (uses all prior setup).

---

## Pass/Fail Criteria

| Level | Definition |
|:------|:-----------|
| GREEN | All 68 tests pass |
| YELLOW | Suites 1,2,6 pass. Others have < 3 failures |
| RED | Suite 1 or Suite 6 fails (infrastructure or core alert pipeline broken) |

---

## CI Integration

For nightly runs, add a GitHub Actions workflow that SSHes into the VMs:

```yaml
# .github/workflows/e2e-nightly.yml
name: E2E Nightly
on:
  schedule:
    - cron: '0 2 * * *'  # 2 AM IST daily
  workflow_dispatch:

jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run E2E
        env:
          VM1_SSH_KEY: ${{ secrets.VM1_SSH_KEY }}
          VM2_SSH_KEY: ${{ secrets.VM2_SSH_KEY }}
        run: |
          chmod 600 /tmp/vm1_key /tmp/vm2_key
          ./e2e/run_full_e2e.sh --config e2e/config/e2e_config.yml
      - uses: actions/upload-artifact@v4
        with:
          name: e2e-report
          path: e2e/reports/
```
