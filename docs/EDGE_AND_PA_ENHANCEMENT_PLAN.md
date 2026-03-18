# EDGE_AND_PA_ENHANCEMENT_PLAN.md

## Enhancement Plan for Edge AI & AlertManagement (PA System)

Both systems are functional but basic. This plan adds production-grade features that a real farm deployment needs.

---

## Part A: Edge AI Enhancements (15 Tasks)

### Current State

The edge system runs inference, checks zones, fuses LoRa data, and publishes alerts. But it lacks operational features needed for 24/7 unattended farm deployment.

**What's missing:**

| Gap | Impact |
|:---|:---|
| No alert snapshots saved to disk | Dashboard shows alert but no visual proof — useless for review |
| Edge GUI is polygon-editor only | No live camera grid, no alert log, no system status page |
| No health check endpoint | Docker healthcheck can't verify service is truly running |
| No Prometheus metrics | Can't monitor inference FPS, alert rates, GPU temp from Grafana |
| No config hot-reload | Changing cameras.json or zones.json requires container restart |
| No per-camera alert throttle | One camera pointing at a busy road floods alerts |
| No alert history buffer | Edge GUI can't show recent alerts — has to query cloud |
| No snapshot cleanup | /tmp/snapshots grows forever, fills disk |
| No camera connection status API | Dashboard can't show which cameras are online/offline |
| No RTSP stream health monitoring | Dead camera goes unnoticed for hours |
| No log rotation within container | Container logs grow unbounded |
| No audio alert on edge (local beep) | Farm workers near the edge PC hear nothing |
| No edge-local alert suppression rules | Can't suppress known false positives locally |
| No model info endpoint | Can't verify which YOLO model version is loaded |
| No graceful model hot-swap | Changing model requires container restart |

---

### Task A1: Alert Snapshot Saving

**File:** `edge/alert_engine.py`

Currently `_save_snapshot` is referenced but the frame is not passed through the callback chain. Fix by:

1. Modify `pipeline.py` `_process_results()` to include the frame in the detection dict
2. In `alert_engine.py`, extract frame, save as JPEG, serve via Flask

```python
# pipeline.py — add frame to detection
detection = {
    ...
    "_frame": packet.frame,  # Raw frame for snapshot (underscore = internal, not published)
}

# alert_engine.py — save snapshot
def _save_snapshot(self, alert_id: str, detection: dict) -> str:
    frame = detection.pop("_frame", None)
    if frame is None:
        return ""
    path = os.path.join(self.snapshot_dir, f"{alert_id}.jpg")
    cv2.imwrite(path, frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
    return f"http://{self._get_vpn_ip()}:5000/snapshots/{alert_id}.jpg"
```

3. Add Flask route to serve snapshots:
```python
# edge_gui.py
@app.route("/snapshots/<filename>")
def serve_snapshot(filename):
    return send_from_directory("/tmp/snapshots", filename)
```

4. Add cron-like cleanup: delete snapshots older than 24 hours every hour.

---

### Task A2: Edge GUI — Live Camera Grid Page

**File:** `edge/edge_gui.py` — Add new route and HTML template

```
GET / → Polygon editor (existing)
GET /cameras → NEW: Live camera grid (2×4 grid of all camera feeds)
```

The camera grid page shows:
- All 8 cameras in a responsive grid
- Each camera: live snapshot (auto-refreshes every 2s), name, FPS, status
- Click a camera → opens polygon editor for that camera
- Status indicator: green (connected), red (disconnected), yellow (slow)

---

### Task A3: Edge GUI — Alert History Page

**File:** `edge/edge_gui.py`

```
GET /alerts → NEW: Recent alerts with snapshots
```

Shows the last 100 alerts stored in an in-memory ring buffer:
- Alert card: snapshot thumbnail, class, zone, confidence, timestamp
- Priority color coding (critical=red, high=orange, warning=yellow)
- Click to see full-size snapshot
- Filter by priority, class, camera

Add in-memory ring buffer to alert_engine.py:
```python
from collections import deque

class AlertDecisionEngine:
    def __init__(self, ...):
        ...
        self.alert_history = deque(maxlen=100)
    
    def process_detection(self, detection):
        ...
        # After publishing alert:
        self.alert_history.appendleft(alert)
```

---

### Task A4: Edge GUI — System Status Page

**File:** `edge/edge_gui.py`

```
GET /status → NEW: System dashboard
```

Shows real-time:
- Node ID, uptime, mode (production/dev)
- Per-camera: name, FPS, frames processed, connection status, last frame time
- GPU: utilization %, memory, temperature (from nvidia-smi)
- MQTT: connected/disconnected, messages published, broker IP
- LoRa: tags seen, workers nearby, last beacon time
- Alert stats: total detections, zone violations, alerts published, suppressed, dedup'd
- Model: version, inference latency avg/max, engine path
- Disk: snapshot dir size, available space

API endpoint for programmatic access:
```
GET /api/status → JSON with all above data
```

---

### Task A5: Health Check Endpoint

**File:** `edge/edge_gui.py`

```
GET /health → {"status": "ok", "checks": {...}}
```

Checks:
- MQTT broker connected
- At least 1 camera connected
- YOLO model loaded (or mock active)
- Zone engine has zones
- Disk space > 100MB free

Used by Docker healthcheck:
```yaml
healthcheck:
  test: ["CMD", "curl", "-sf", "http://localhost:5000/health"]
  interval: 30s
  timeout: 5s
  retries: 3
```

---

### Task A6: Prometheus Metrics Endpoint

**File:** New `edge/metrics.py` + route in `edge_gui.py`

```
GET /metrics → Prometheus text format
```

Metrics:
```
# Edge AI Inference
sc_inference_total{camera="cam-01"} 14523
sc_inference_latency_seconds{quantile="0.5"} 0.006
sc_inference_latency_seconds{quantile="0.99"} 0.012
sc_detections_total{class="person"} 312
sc_detections_total{class="snake"} 3

# Alerts
sc_alerts_published_total{priority="critical"} 5
sc_alerts_published_total{priority="high"} 18
sc_alerts_suppressed_total{reason="worker"} 289
sc_alerts_deduplicated_total 47

# Cameras
sc_camera_connected{camera="cam-01"} 1
sc_camera_fps{camera="cam-01"} 2.5
sc_camera_frames_total{camera="cam-01"} 14523

# GPU
sc_gpu_utilization_ratio 0.12
sc_gpu_memory_bytes 1153433600
sc_gpu_temperature_celsius 52

# LoRa
sc_lora_tags_seen 3
sc_lora_workers_nearby 1

# System
sc_uptime_seconds 86400
sc_snapshot_disk_bytes 52428800
```

---

### Task A7: Config Hot-Reload via File Watcher

**File:** New `edge/config_watcher.py`

Watches `config/` directory for changes using `watchdog` library. When `zones.json`, `cameras.json`, or `authorized_tags.json` is modified:

- `zones.json` changed → `zone_engine.reload()` (already exists)
- `authorized_tags.json` changed → `lora_receiver._load_authorized()` (already exists)
- `cameras.json` changed → log warning "Camera config changed — restart required" (can't hot-swap RTSP connections safely)

Also triggerable via MQTT:
```
farm/admin/reload_config → reloads zones + tags
```

And via GUI:
```
POST /api/reload → triggers reload
```

---

### Task A8: Per-Camera Alert Throttle

**File:** `edge/alert_engine.py`

Currently dedup is per zone+class (30s). Add per-camera throttle:

```python
# Maximum alerts per camera per minute
CAMERA_THROTTLE = int(os.getenv("CAMERA_ALERT_LIMIT", "10"))  # 10 alerts/min/camera

class AlertDecisionEngine:
    def __init__(self, ...):
        ...
        self._camera_alert_counts = defaultdict(list)  # camera_id → [timestamps]
    
    def _is_camera_throttled(self, camera_id: str) -> bool:
        now = time.time()
        window = self._camera_alert_counts[camera_id]
        # Remove entries older than 60s
        window = [t for t in window if now - t < 60]
        self._camera_alert_counts[camera_id] = window
        return len(window) >= CAMERA_THROTTLE
```

A camera pointing at a busy road won't flood the system with 150 person-alerts per minute.

---

### Task A9: Snapshot Disk Cleanup

**File:** `edge/alert_engine.py` or new `edge/cleanup.py`

Background thread that runs every hour:
- Delete snapshots older than 24 hours
- If disk usage > 80%, delete oldest snapshots until under 70%
- Log cleanup stats

```python
import glob, os, time, threading

def snapshot_cleanup_loop(snapshot_dir, max_age_hours=24, check_interval=3600):
    while True:
        cutoff = time.time() - (max_age_hours * 3600)
        deleted = 0
        for path in glob.glob(os.path.join(snapshot_dir, "*.jpg")):
            if os.path.getmtime(path) < cutoff:
                os.remove(path)
                deleted += 1
        if deleted:
            log.info("Snapshot cleanup: deleted %d files older than %dh", deleted, max_age_hours)
        time.sleep(check_interval)
```

---

### Task A10: Camera Connection Status API

**File:** `edge/pipeline.py` + `edge/edge_gui.py`

```
GET /api/cameras/status → [
    {"id": "cam-01", "name": "Front Gate", "connected": true, "fps": 2.4, 
     "frames": 14523, "last_frame": "2026-03-07T10:15:30Z"},
    {"id": "cam-02", "connected": false, "reconnect_attempts": 3}
]
```

Pipeline already tracks `connected` and `frame_count` per camera — just expose it via API.

---

### Task A11: RTSP Stream Health Monitor

**File:** `edge/pipeline.py`

If no frame received from a camera for >30 seconds, publish an event:
```
farm/events/camera_offline {"camera_id": "cam-02", "last_frame": ..., "node_id": ...}
```

When camera reconnects:
```
farm/events/camera_online {"camera_id": "cam-02", ...}
```

Dashboard and Android app can show camera status from these events.

---

### Task A12: Model Info Endpoint

**File:** `edge/edge_gui.py`

```
GET /api/model → {
    "engine_path": "/app/models/yolov8n_farm.engine",
    "model_type": "TensorRT FP16",
    "classes": ["person", "child", "cow", "snake", ...],
    "input_size": 640,
    "avg_inference_ms": 6.2,
    "total_inferences": 145230,
    "loaded_at": "2026-03-07T08:00:00Z"
}
```

---

### Task A13: Edge-Local Alert Sound

**File:** New `edge/local_alert.py`

Play a short beep through the edge PC's audio output when a critical alert fires. Useful for farm workers near the edge server.

```python
import subprocess

def play_alert_sound(priority: str):
    if priority == "critical":
        # 3 short beeps
        for _ in range(3):
            subprocess.Popen(["aplay", "-q", "/app/sounds/beep_critical.wav"])
            time.sleep(0.3)
    elif priority == "high":
        subprocess.Popen(["aplay", "-q", "/app/sounds/beep_high.wav"])
```

Include small WAV files in `edge/sounds/`.

Optional — controlled by env var `LOCAL_ALERT_SOUND=true`.

---

### Task A14: Edge-Local Suppression Rules

**File:** New `edge/config/suppression_rules.json`

Local rules to suppress known false positives without cloud round-trip:

```json
{
  "rules": [
    {
      "id": "suppress-scarecrow-cam01",
      "camera_id": "cam-01",
      "class": "person",
      "bbox_region": [400, 100, 500, 350],
      "reason": "Scarecrow in fixed position",
      "enabled": true
    },
    {
      "id": "suppress-reflection-cam02",
      "camera_id": "cam-02",
      "class": "fire",
      "time_range": ["17:00", "19:00"],
      "reason": "Sunset reflection through window",
      "enabled": true
    }
  ]
}
```

Check in `detection_filters.py` before zone engine.

---

### Task A15: Graceful Model Hot-Swap

**File:** `edge/pipeline.py` + `edge/farm_edge_node.py`

Listen for MQTT command:
```
farm/admin/model_update {"model_path": "/app/models/yolov8n_farm_v1.1.engine"}
```

On receive:
1. Load new model in background thread
2. Run 10-frame warmup inference
3. Atomic swap: replace model reference
4. Old model garbage collected
5. Publish ack: `farm/events/model_updated {"version": "v1.1", "status": "success"}`

If load fails → keep old model, publish `{"status": "failed", "error": "..."}`.

---

## Part B: AlertManagement (PA System) Enhancements (10 Tasks)

### Current State

The PA controller handles `play_bgm`, `play_siren`, `stop`, and `status` commands via MQTT. It runs as a systemd service on a read-only Pi Zero 2W filesystem. But it lacks several features needed for a real farm PA system.

---

### Task B1: Multiple Siren Tones

**File:** `AlertManagement/scripts/pa_controller.py`

Currently plays one siren file. Add support for different tones per alert type:

```json
// pa/command payload:
{
    "command": "play_siren",
    "alert_type": "intruder",    // NEW: maps to specific audio file
    "url": "http://..."          // Fallback if alert_type not found
}
```

Tone mapping in config:
```
SIREN_INTRUDER=/opt/pa-system/sounds/siren_intruder.mp3
SIREN_FIRE=/opt/pa-system/sounds/siren_fire.mp3
SIREN_SNAKE=/opt/pa-system/sounds/siren_snake.mp3
SIREN_CHILD=/opt/pa-system/sounds/siren_child_emergency.mp3
SIREN_LIVESTOCK=/opt/pa-system/sounds/siren_livestock.mp3
SIREN_DEFAULT=/opt/pa-system/sounds/siren_default.mp3
```

Child emergency siren should be different (more urgent, repeating) from a livestock escape warning.

---

### Task B2: TTS Voice Announcements

**File:** `AlertManagement/scripts/pa_controller.py`

Use `espeak-ng` (pre-installed on Pi OS) or `pico2wave` for text-to-speech. When an alert arrives, announce it through the PA speakers:

```json
// pa/command payload:
{
    "command": "announce",
    "text": "Warning: snake detected near storage shed. Camera 5.",
    "language": "en",
    "repeat": 2
}
```

Implementation:
```python
def play_tts(text: str, language: str = "en", repeat: int = 1):
    wav_path = "/tmp/tts_output.wav"
    subprocess.run(["espeak-ng", "-v", language, "-w", wav_path, text])
    for _ in range(repeat):
        subprocess.run(["aplay", "-D", "default", wav_path])
```

The edge AI system can compose announcement text from the alert:
```python
# In farm_edge_node.py siren trigger:
text = f"Alert: {alert['detection_class']} detected at {alert['zone_name']}. Camera {alert['camera_id']}."
pa_client.publish("pa/command", json.dumps({
    "command": "announce",
    "text": text,
    "repeat": 2,
}))
```

---

### Task B3: Scheduled Playback (Morning Chants)

**File:** New `AlertManagement/scripts/scheduler.py` + cron entry

The farm plays Suprabhatam (morning chant) at 5:30 AM daily. Currently this is a manual cron job suggestion. Make it built-in:

```python
# scheduler.py — runs alongside pa_controller
import schedule, time, json
import paho.mqtt.client as mqtt

def morning_chant():
    client.publish("pa/command", json.dumps({
        "command": "play_bgm",
        "url": "http://192.168.1.100/audio/suprabatham.mp3",
    }))

schedule.every().day.at("05:30").do(morning_chant)

# Also support evening prayers, etc. via config
```

Configurable via `pa.env`:
```
SCHEDULE_MORNING=05:30,http://192.168.1.100/audio/suprabatham.mp3
SCHEDULE_EVENING=18:30,http://192.168.1.100/audio/evening_prayer.mp3
```

---

### Task B4: Health Heartbeat

**File:** `AlertManagement/scripts/pa_controller.py`

Publish health status every 60 seconds:

```
pa/health → {
    "status": "online",
    "state": "IDLE",
    "uptime_seconds": 86400,
    "last_siren_time": 1709712000.0,
    "siren_count_today": 3,
    "volume": 85,
    "cpu_temp": 52.3,
    "memory_percent": 34,
    "filesystem": "read-only",
    "audio_device": "snd_rpi_hifiberry_dac",
    "timestamp": 1709712345.0
}
```

Edge node and dashboard can show PA system status.

---

### Task B5: Remote Volume Control

**File:** `AlertManagement/scripts/pa_controller.py`

```json
// pa/command:
{"command": "set_volume", "volume": 75}   // 0-100
{"command": "get_volume"}                  // → publishes to pa/status
```

Uses ALSA `amixer` to adjust the SoftMaster control:
```python
def set_volume(volume: int):
    volume = max(0, min(100, volume))
    subprocess.run(["amixer", "sset", "SoftMaster", f"{volume}%"])
```

---

### Task B6: Audio File Caching

**File:** `AlertManagement/scripts/pa_controller.py`

Currently mpv streams from HTTP URL every time. Add local caching:

```python
import hashlib, urllib.request, os

CACHE_DIR = "/tmp/pa-cache"

def get_cached_audio(url: str) -> str:
    os.makedirs(CACHE_DIR, exist_ok=True)
    url_hash = hashlib.md5(url.encode()).hexdigest()
    cache_path = os.path.join(CACHE_DIR, f"{url_hash}.mp3")
    
    if os.path.exists(cache_path):
        return cache_path
    
    urllib.request.urlretrieve(url, cache_path)
    return cache_path
```

Benefits: faster siren response (no HTTP latency), works during network blips.

---

### Task B7: Daily Self-Test

**File:** New `AlertManagement/scripts/self_test.py` + cron/schedule

Run a brief PA system test at a configured time (e.g., 10:00 AM when farm is active):

1. Play a 1-second test tone at 30% volume
2. Check mpv process starts and exits cleanly
3. Check ALSA device is available
4. Check MQTT connection
5. Publish result: `pa/health/self_test {"passed": true, "checks": [...]}`

If test fails, publish `{"passed": false}` → dashboard shows PA system needs attention.

---

### Task B8: LED Status Indicator

**File:** `AlertManagement/scripts/pa_controller.py`

Use Pi GPIO to drive a status LED:

```
IDLE:         Green solid
PLAYING_BGM:  Green blinking slow
PLAYING_SIREN: Red blinking fast
ERROR:        Red solid
MQTT DISCONNECTED: Yellow blinking
```

GPIO pin configurable via `pa.env`:
```
STATUS_LED_PIN=17
```

Uses `gpiozero` library (pre-installed on Pi OS):
```python
from gpiozero import LED
status_led = LED(17)
```

---

### Task B9: Power/UPS Monitoring

**File:** New `AlertManagement/scripts/power_monitor.py`

Monitor the LiFePO4 battery voltage via an INA219 sensor on I2C:

```python
# Read battery voltage every 30 seconds
# If voltage < 12.0V (24V system at 50%): publish warning
# If voltage < 10.5V (critically low): publish critical + graceful shutdown

import smbus2

def read_battery_voltage():
    bus = smbus2.SMBus(1)
    # INA219 at address 0x40
    raw = bus.read_word_data(0x40, 0x02)
    voltage = raw * 0.001  # Calibration-dependent
    return voltage
```

Publish to: `pa/health/battery {"voltage": 25.2, "percent": 85, "charging": true}`

---

### Task B10: PA Web Dashboard

**File:** New `AlertManagement/scripts/pa_dashboard.py`

Minimal Flask web UI on port 8080 (accessible via edge node network):

```
http://<pi-ip>:8080/
```

Shows:
- Current state (IDLE / PLAYING_BGM / PLAYING_SIREN)
- Volume slider (AJAX → MQTT set_volume)
- Manual siren trigger / stop buttons
- Recent playback history (last 20 events)
- Health: CPU temp, uptime, memory, battery voltage
- Self-test result

Lightweight — no npm/React, just Jinja2 templates with vanilla JS.

---

## File Change Summary

### Edge (Part A) — 15 Tasks

| # | Files | Type | Description |
|:--|:------|:-----|:-----------|
| A1 | `pipeline.py`, `alert_engine.py`, `edge_gui.py` | MODIFY | Alert snapshot save + serve + cleanup |
| A2 | `edge_gui.py` | MODIFY | Live camera grid page |
| A3 | `edge_gui.py`, `alert_engine.py` | MODIFY | Alert history page + ring buffer |
| A4 | `edge_gui.py` | MODIFY | System status page |
| A5 | `edge_gui.py` | MODIFY | Health check endpoint |
| A6 | NEW `metrics.py`, `edge_gui.py` | NEW+MODIFY | Prometheus metrics |
| A7 | NEW `config_watcher.py`, `farm_edge_node.py` | NEW+MODIFY | Config hot-reload |
| A8 | `alert_engine.py` | MODIFY | Per-camera alert throttle |
| A9 | `alert_engine.py` or NEW `cleanup.py` | NEW/MODIFY | Snapshot disk cleanup |
| A10 | `pipeline.py`, `edge_gui.py` | MODIFY | Camera status API |
| A11 | `pipeline.py` | MODIFY | RTSP health monitor + MQTT events |
| A12 | `edge_gui.py` | MODIFY | Model info endpoint |
| A13 | NEW `local_alert.py`, `edge/sounds/` | NEW | Edge-local alert beep |
| A14 | NEW `config/suppression_rules.json`, `detection_filters.py` | NEW+MODIFY | Local suppression rules |
| A15 | `pipeline.py`, `farm_edge_node.py` | MODIFY | Model hot-swap via MQTT |

### AlertManagement (Part B) — 10 Tasks

| # | Files | Type | Description |
|:--|:------|:-----|:-----------|
| B1 | `pa_controller.py`, `pa.env.example` | MODIFY | Multiple siren tones per alert type |
| B2 | `pa_controller.py` | MODIFY | TTS voice announcements via espeak-ng |
| B3 | NEW `scheduler.py` | NEW | Scheduled morning chants + evening prayers |
| B4 | `pa_controller.py` | MODIFY | Health heartbeat every 60s |
| B5 | `pa_controller.py` | MODIFY | Remote volume control via MQTT |
| B6 | `pa_controller.py` | MODIFY | Audio file local caching |
| B7 | NEW `self_test.py` | NEW | Daily PA self-test + result publish |
| B8 | `pa_controller.py` | MODIFY | GPIO LED status indicator |
| B9 | NEW `power_monitor.py` | NEW | LiFePO4 battery voltage monitoring |
| B10 | NEW `pa_dashboard.py` | NEW | Minimal web dashboard for PA system |

---

## Priority Order

**Phase 1 (Critical for deployment):**
A1 (snapshots), A5 (health check), A4 (status page), B1 (siren tones), B4 (heartbeat)

**Phase 2 (Operational quality):**
A2 (camera grid), A3 (alert history), A8 (throttle), A9 (cleanup), A11 (RTSP monitor), B2 (TTS), B5 (volume), B6 (cache)

**Phase 3 (Production hardening):**
A6 (metrics), A7 (config reload), A10 (camera API), A12 (model info), A14 (suppression), A15 (model swap), B3 (scheduler), B7 (self-test)

**Phase 4 (Nice to have):**
A13 (local sound), B8 (LED), B9 (battery), B10 (PA dashboard)
