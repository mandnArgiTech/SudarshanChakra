# Camera, Video & Remote Control — Consolidated Plan

> **Replaces:** `VIDEO_STORAGE_AND_CAMERA_LIFECYCLE_PLAN.md` + `VIDEO_PLAYBACK_PLAN.md`
> **This is the single source of truth** for everything camera-related.

---

## The Scenario That Must Work

You're 200 km from the farm. A cow broke through a fence into a new area. You need to:

1. **Open Android app** → see live camera feed
2. **Pan/tilt the camera** remotely to face the new area
3. **Draw a containment zone** on the live feed using your finger
4. **Save the zone** → edge node starts monitoring immediately
5. **Watch the recorded footage** from earlier to understand how the cow escaped
6. **Get alerts** if the cow leaves the new zone

Every step above currently fails. This plan fixes all of it.

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────┐
│                        CAMERA (Tapo/VIGI)                         │
│   RTSP stream  │  ONVIF PTZ control  │  ONVIF device info         │
└───────┬────────┴────────┬─────────────┴─────────┬─────────────────┘
        │                 │                       │
        ▼                 ▼                       ▼
┌──────────────────────────────────────────────────────────────────┐
│                      EDGE NODE (RTX 3060)                        │
│                                                                    │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌────────────────┐  │
│  │ YOLO     │  │ Video    │  │ ONVIF     │  │ Zone Engine    │  │
│  │ Inference│  │ Recorder │  │ Controller│  │ (polygon check)│  │
│  │ 2-3 FPS  │  │ ffmpeg   │  │ PTZ+Info  │  │ hot-reload     │  │
│  └────┬─────┘  └────┬─────┘  └─────┬─────┘  └───────┬────────┘  │
│       │              │              │                │            │
│  ┌────┴──────────────┴──────────────┴────────────────┴──────┐    │
│  │                   Flask API (:5000)                       │    │
│  │  /api/live/{cam}         → MJPEG live stream              │    │
│  │  /api/snapshot/{cam}     → Latest JPEG                    │    │
│  │  /api/video/{cam}/{path} → Recorded MP4 (with seek)       │    │
│  │  /api/ptz/{cam}/move     → Pan/Tilt/Zoom commands         │    │
│  │  /api/ptz/{cam}/presets  → Saved positions                │    │
│  │  /api/ptz/{cam}/info     → Camera capabilities            │    │
│  │  /api/zones              → CRUD zones with polygon data   │    │
│  │  /api/recordings/{cam}   → List recorded segments         │    │
│  │  /api/clips/{alert_id}   → 30s alert clip                │    │
│  │  /api/storage/status     → SSD + archive disk usage       │    │
│  └──────────────────────────┬───────────────────────────────┘    │
│                              │                                     │
│  ┌───────────────────────────┴──────────────────────────────┐    │
│  │                    SSD Storage                            │    │
│  │  /data/video/cam-01/2026-03-22/08/cam-01_08-00.mp4       │    │
│  │  → 10-min segments, 10-day retention, circular overwrite  │    │
│  └───────────────────────────┬──────────────────────────────┘    │
│                              │ after 10 days                       │
│                              ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │              External HDD Archive                         │    │
│  │  Same structure + metadata.json per day                   │    │
│  └──────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────┬─────────────────────────┘
                                         │ OpenVPN tunnel
                                         ▼
┌──────────────────────────────────────────────────────────────────┐
│                     VPS (vivasvan-tech.in)                        │
│                                                                    │
│  API Gateway proxies:                                             │
│    /edge/live/{cam}        → edge:5000/api/live/{cam}             │
│    /edge/video/{path}      → edge:5000/api/video/{path}          │
│    /edge/ptz/{cam}/{cmd}   → edge:5000/api/ptz/{cam}/{cmd}       │
│    /edge/zones/...         → edge:5000/api/zones/...             │
│    /edge/recordings/...    → edge:5000/api/recordings/...        │
│                                                                    │
│  Backend services: alert, device, auth, siren (unchanged)        │
└────────────────────────────────────┬─────────────────────────────┘
                                     │
                    ┌────────────────┴────────────────┐
                    ▼                                  ▼
            ┌──────────────┐                  ┌──────────────┐
            │  Dashboard   │                  │  Android App │
            │  (Browser)   │                  │  (Mobile)    │
            │              │                  │              │
            │ Live feed    │                  │ Live feed    │
            │ PTZ joystick │                  │ PTZ joystick │
            │ Zone drawing │                  │ Zone drawing │
            │ Playback     │                  │ Playback     │
            │ Alert clips  │                  │ Alert clips  │
            └──────────────┘                  └──────────────┘
```

---

## 20 Implementation Tasks (5 Phases)

### Phase 1: Video Recording + Storage (2 days)

#### Task 1: Video Recorder

**New:** `edge/video_recorder.py`

Records each camera's RTSP stream continuously using ffmpeg subprocess with `-c:v copy` (zero CPU — copies H.264 directly). Splits into 10-minute MP4 segments.

```
ffmpeg -rtsp_transport tcp -i rtsp://user:pass@ip:554/stream1
  -c:v copy -c:a copy
  -f segment -segment_time 600 -segment_format mp4
  -strftime 1 -reset_timestamps 1
  /data/video/cam-01/%Y-%m-%d/%H/cam-01_%H-%M.mp4
```

Uses stream1 (full quality) for recording — not stream2 (which is only for YOLO inference).

#### Task 2: Storage Directory Structure

```
/data/video/cam-01/2026-03-22/08/cam-01_08-00.mp4
                                   cam-01_08-10.mp4
                                   cam-01_08-20.mp4 ...
```

Portable, self-contained per camera/date. Copy a folder to any machine.

#### Task 3: Storage Manager + Circular Overwrite

**New:** `edge/storage_manager.py`

- Monitors SSD usage every hour
- At 85% full: deletes oldest segments (after archiving if enabled)
- Archives to external HDD after 10 days with metadata.json per day
- Multi-SSD support: assign cameras to different SSDs

#### Task 4: Storage Config

**New:** `edge/config/storage.json`

```json
{
    "ssd_pools": [
        { "path": "/data/video", "cameras": ["cam-01","cam-02","cam-03","cam-04"] },
        { "path": "/data/video2", "cameras": ["cam-05","cam-06","cam-07","cam-08"] }
    ],
    "retention_days": 10,
    "high_watermark_percent": 85,
    "archive": { "enabled": true, "path": "/archive/video" }
}
```

---

### Phase 2: Video Serving + Playback (2 days)

#### Task 5: Edge Flask — Serve Recorded MP4

Add to `edge/edge_gui.py`:

```
GET /api/video/{cam}/{date}/{hour}/{file}  → Serve MP4 with HTTP Range (seeking)
GET /api/recordings/{cam}                  → List dates
GET /api/recordings/{cam}/{date}           → List segments
GET /api/clips/{alert_id}.mp4              → 30-second alert clip
GET /api/storage/status                    → Disk usage JSON
```

Flask `send_file(conditional=True)` handles Range headers automatically — browser seeking works for free.

#### Task 6: Edge Flask — MJPEG Live Stream

```
GET /api/live/{cam}  → multipart/x-mixed-replace MJPEG stream
```

Streams inference frames at 2-3 FPS with detection boxes drawn. Browser plays with `<img src="...">`, no JavaScript needed.

#### Task 7: Dashboard — Video Player Page

**New:** `dashboard/src/pages/VideoPlayerPage.tsx`

```
┌─────────────────────────────────────────────────────────┐
│  cam-01: Front Gate      │ 📅 2026-03-22  │ [◀ ▶]      │
├─────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────┐ │
│  │                                                      │ │
│  │              <video> MP4 PLAYER                      │ │
│  │         seek · pause · fullscreen · speed            │ │
│  │                                                      │ │
│  └────────────────────────────────────────────────────┘ │
│                                                           │
│  ▐████░░░░████░░░░████░░░░░░████░░░░░░████░░░░░░████▐  │
│   00    04    08    12    16    20    24                  │
│          🔴snake        🔴person                         │
│                                                           │
│  Segments: [08:00][08:10][08:20][08:30][08:40][08:50]   │
│                                                           │
│  Disk: SSD 42% (120GB/288GB) │ Archive: 1.2TB used      │
└─────────────────────────────────────────────────────────┘
```

Native HTML5 `<video src="...">` — play, pause, seek, fullscreen, speed all built-in.

#### Task 8: Dashboard — Live Camera Grid with MJPEG

**Modify:** `dashboard/src/pages/CamerasPage.tsx`

```
┌──────────┬──────────┬──────────┬──────────┐
│ cam-01   │ cam-02   │ cam-03   │ cam-04   │
│ [LIVE    │ [LIVE    │ [LIVE    │ [LIVE    │
│  MJPEG]  │  MJPEG]  │  MJPEG]  │  MJPEG]  │
│ ● Online │ ● Online │ ⚠ Alert │ ● Online │
│[▶Play][🎯PTZ][📐Zone]                     │
└──────────┴──────────┴──────────┴──────────┘
```

Each camera card: live MJPEG feed, "Recordings" button, "PTZ" button (if capable), "Zones" button.

#### Task 9: Android — ExoPlayer + Live Feed

**New:** `android/.../ui/screens/video/VideoPlayerScreen.kt`

ExoPlayer for recorded MP4 playback. JPEG polling at 2 FPS for live view (MJPEG doesn't work in Android ImageView natively).

#### Task 10: Alert Detail — Video Evidence

Both dashboard and Android show on each alert:
- Alert snapshot (JPEG)
- 30-second video clip (15s before + 15s after)
- Link to full recording segment

---

### Phase 3: ONVIF PTZ Camera Control (2 days)

#### Task 11: ONVIF Discovery + Capabilities

**New:** `edge/onvif_controller.py`

Uses `onvif-zeep` library to query each camera:

```python
from onvif import ONVIFCamera

class OnvifController:
    def __init__(self, host, port, user, password):
        self.cam = ONVIFCamera(host, port, user, password)
        self.media = self.cam.create_media_service()
        self.ptz = self.cam.create_ptz_service()
        self.profiles = self.media.GetProfiles()
    
    def get_capabilities(self):
        """What can this camera do?"""
        return {
            "has_ptz": self._has_ptz(),
            "can_pan": True/False,
            "can_tilt": True/False,
            "can_zoom": True/False,
            "pan_range": {"min": -1.0, "max": 1.0},
            "tilt_range": {"min": -0.5, "max": 0.5},
            "zoom_range": {"min": 0.0, "max": 1.0},
            "presets": self.get_presets(),
            "model": info.Model,
            "firmware": info.FirmwareVersion,
            "serial": info.SerialNumber,
        }
    
    def continuous_move(self, pan_speed, tilt_speed, zoom_speed):
        """Joystick-style control — move until stop() is called."""
    
    def absolute_move(self, pan, tilt, zoom):
        """Move to exact position (for presets)."""
    
    def goto_preset(self, preset_name):
        """Jump to saved position like 'Front Gate', 'Pond View'."""
    
    def save_preset(self, name):
        """Save current position as a named preset."""
    
    def stop(self):
        """Stop all movement."""
```

#### Task 12: Edge Flask — PTZ API

```
GET  /api/ptz/{cam}/info             → Camera capabilities + current position
GET  /api/ptz/{cam}/presets          → List saved presets
POST /api/ptz/{cam}/move             → {"pan": 0.5, "tilt": -0.3, "zoom": 0.0}
POST /api/ptz/{cam}/move/continuous  → {"pan_speed": 0.5, "tilt_speed": 0} (joystick)
POST /api/ptz/{cam}/stop             → Stop movement
POST /api/ptz/{cam}/preset/goto      → {"name": "Pond View"}
POST /api/ptz/{cam}/preset/save      → {"name": "New Zone Alpha"}
POST /api/ptz/{cam}/zoom             → {"level": 0.7} (0.0=wide, 1.0=max zoom)
```

#### Task 13: Dashboard — PTZ Joystick Control

**New:** `dashboard/src/components/PtzJoystick.tsx`

```
┌─────────────────────────────────────────────┐
│  cam-01: Front Gate (VIGI C540-W)  [PTZ ●]  │
├─────────────────────────────────────────────┤
│  ┌───────────────────────────────────────┐  │
│  │           LIVE MJPEG FEED             │  │
│  │                                       │  │
│  │                                       │  │
│  └───────────────────────────────────────┘  │
│                                               │
│  ┌─────────┐  Presets:                       │
│  │    ▲    │  [Front Gate] [Pond View]       │
│  │  ◄ ● ► │  [Storage]   [+ Save Current]   │
│  │    ▼    │                                  │
│  └─────────┘  Zoom: ──────●────── [−] [+]   │
│                                               │
│  Model: VIGI C540-W │ Pan: 360° │ Tilt: 115° │
└─────────────────────────────────────────────┘
```

Joystick sends `continuous_move` on press/hold, `stop` on release. Arrow keys also work. Zoom slider. Preset buttons for quick positions.

#### Task 14: Android — PTZ Control Screen

**New:** `android/.../ui/screens/cameras/PtzControlScreen.kt`

Same as dashboard but touch-optimized:
- Swipe on video feed to pan/tilt (gesture control)
- Pinch to zoom
- Preset buttons below the feed
- Joystick overlay on the video

---

### Phase 4: Remote Zone Drawing (2 days)

#### Task 15: Dashboard — Zone Drawing on Live Feed

**New:** `dashboard/src/components/ZoneDrawer.tsx`

Draw polygons on top of the live camera feed:

```
┌─────────────────────────────────────────────┐
│  Draw Zone on cam-01                 [Done]  │
├─────────────────────────────────────────────┤
│  ┌───────────────────────────────────────┐  │
│  │  LIVE FEED                            │  │
│  │       ●────────────●                  │  │
│  │      /              \                 │  │
│  │     ●                ●                │  │
│  │      \              /                 │  │
│  │       ●────────────●                  │  │
│  │   (click to add points)              │  │
│  └───────────────────────────────────────┘  │
│                                               │
│  Zone Name: [Cow Containment Area    ]       │
│  Type:      [livestock_containment ▾]        │
│  Priority:  [warning ▾]                      │
│  Classes:   [☑ cow] [☐ person] [☐ child]     │
│  Suppress with worker tag: [☑]               │
│                                               │
│  [Save Zone]  [Clear]  [Cancel]              │
└─────────────────────────────────────────────┘
```

Click points on the live feed image → polygon forms → submit saves to backend API + syncs to edge zone engine.

The zone is active IMMEDIATELY — edge hot-reloads via config watcher or API push.

#### Task 16: Android — Touch Zone Drawing

**New:** `android/.../ui/screens/zones/ZoneDrawerScreen.kt`

Same as dashboard but touch-optimized:
- Tap on video feed to place polygon points
- Drag existing points to adjust
- Long-press to delete a point
- Pinch/zoom the feed while drawing
- If camera has PTZ: first position the camera, then draw

```
┌─────────────────────────────┐
│  Draw Zone: cam-01           │
│ ┌─────────────────────────┐ │
│ │  LIVE FEED               │ │
│ │     ●───●                │ │
│ │    /     \               │ │
│ │   ●       ●              │ │
│ │    \     /               │ │
│ │     ●───●                │ │
│ │  (tap to add points)     │ │
│ └─────────────────────────┘ │
│                               │
│ Name: [Cow Zone        ]     │
│ Type: [Containment  ▾]      │
│ Priority: [Warning  ▾]      │
│ Classes: [cow] [person]      │
│                               │
│   [Save]   [Undo]   [Clear] │
└─────────────────────────────┘
```

#### Task 17: Zone Sync — Backend → Edge (Real-Time)

When a zone is created/modified via dashboard or Android:

```
Dashboard/Android → POST /api/v1/zones → device-service stores in DB
  → publishes MQTT: farm/admin/zone_updated {zone_id, camera_id}
  → edge node receives → downloads new zone from API
  → zone_engine.reload() → active immediately (no restart)
```

**Also works in reverse:** if someone draws a zone on the edge GUI (localhost:5000), it syncs to the backend.

---

### Phase 5: Video File Input + Camera Lifecycle (1 day)

#### Task 18: Video File / Stream Input

**Modify:** `edge/pipeline.py` + new grabber classes

Accept MP4 files or HTTP streams as camera sources:

```json
{
    "id": "review-01",
    "name": "Incident Footage Review",
    "source_type": "file",
    "source_url": "/data/uploads/incident.mp4",
    "recording_enabled": false,
    "fps": 5.0,
    "loop": false
}
```

Source types: `rtsp` (existing), `file` (MP4/AVI), `http` (MJPEG/HLS), `directory` (folder of JPEGs for training review).

#### Task 19: Camera Registration from Dashboard/Android

Register new cameras from the UI:

```
┌─────────────────────────────────────────────┐
│  Add Camera                          [Save]  │
├─────────────────────────────────────────────┤
│  Source Type:  (●) RTSP  ( ) Video File     │
│               ( ) HTTP Stream               │
│                                               │
│  For RTSP:                                   │
│  IP Address:  [192.168.1.201    ]            │
│  Username:    [farmadmin         ]            │
│  Password:    [••••••••          ]            │
│  Stream:      [stream2 (640×480) ▾]          │
│                                               │
│  [Test Connection]  → ✅ Connected, PTZ: Yes  │
│                                               │
│  Name:        [Front Gate Camera  ]           │
│  Assign to:   [Edge Node A ▾]                │
│  FPS:         [2.5              ]             │
│  Record:      [☑ Enable recording ]           │
│                                               │
│  For Video File:                              │
│  [📁 Choose MP4 File]  selected: incident.mp4│
│  Loop: [☐]  FPS: [5.0]                       │
└─────────────────────────────────────────────┘
```

Backend stores camera → edge syncs config → pipeline starts grabbing.

#### Task 20: API Gateway — Proxy Edge Endpoints

```yaml
# All edge endpoints proxied through VPS for remote access
routes:
  - /edge/live/**     → edge:5000/api/live/**
  - /edge/video/**    → edge:5000/api/video/**
  - /edge/ptz/**      → edge:5000/api/ptz/**
  - /edge/zones/**    → edge:5000/api/zones/**
  - /edge/recordings/** → edge:5000/api/recordings/**
  - /edge/clips/**    → edge:5000/api/clips/**
  - /edge/storage/**  → edge:5000/api/storage/**
```

---

## Database Schema Additions

```sql
-- Camera enhancements
ALTER TABLE cameras ADD COLUMN source_type VARCHAR(20) DEFAULT 'rtsp';
    -- 'rtsp' | 'file' | 'http' | 'directory'
ALTER TABLE cameras ADD COLUMN source_url TEXT;
    -- For file: /data/uploads/video.mp4
    -- For http: http://server/stream.mjpeg
ALTER TABLE cameras ADD COLUMN recording_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE cameras ADD COLUMN has_ptz BOOLEAN DEFAULT FALSE;
ALTER TABLE cameras ADD COLUMN ptz_presets JSONB DEFAULT '[]';
ALTER TABLE cameras ADD COLUMN onvif_info JSONB DEFAULT '{}';

-- Video recordings
CREATE TABLE video_recordings (
    id BIGSERIAL PRIMARY KEY,
    camera_id VARCHAR(50) REFERENCES cameras(id),
    node_id VARCHAR(50),
    segment_start TIMESTAMPTZ NOT NULL,
    segment_end TIMESTAMPTZ NOT NULL,
    file_path TEXT NOT NULL,
    file_size_bytes BIGINT,
    duration_seconds REAL,
    storage_location VARCHAR(20) DEFAULT 'ssd',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Alert ↔ video link
CREATE TABLE alert_video_clips (
    alert_id UUID REFERENCES alerts(id),
    recording_id BIGINT REFERENCES video_recordings(id),
    clip_path TEXT,
    offset_seconds REAL,
    PRIMARY KEY (alert_id)
);
```

---

## File Summary

### New Files (17)

| File | Purpose |
|:-----|:--------|
| `edge/video_recorder.py` | ffmpeg continuous recording in 10-min segments |
| `edge/storage_manager.py` | SSD monitor, circular overwrite, archive trigger |
| `edge/archiver.py` | Move to external HDD + metadata.json |
| `edge/onvif_controller.py` | ONVIF discovery + PTZ commands |
| `edge/grabbers/file_grabber.py` | MP4/AVI file input |
| `edge/grabbers/http_grabber.py` | HTTP MJPEG/HLS stream input |
| `edge/camera_sync.py` | Backend ↔ edge camera config sync |
| `edge/config/storage.json` | Storage configuration |
| `edge/scripts/export_training_data.py` | Extract frames for AI training |
| `dashboard/src/pages/VideoPlayerPage.tsx` | MP4 playback with timeline + alerts |
| `dashboard/src/components/PtzJoystick.tsx` | Pan/tilt/zoom joystick + presets |
| `dashboard/src/components/ZoneDrawer.tsx` | Draw zones on live feed |
| `dashboard/src/components/LiveCameraFeed.tsx` | MJPEG live stream component |
| `android/.../screens/video/VideoPlayerScreen.kt` | ExoPlayer MP4 playback |
| `android/.../screens/cameras/PtzControlScreen.kt` | Touch PTZ control |
| `android/.../screens/zones/ZoneDrawerScreen.kt` | Touch zone drawing on feed |
| `android/.../screens/cameras/AddCameraScreen.kt` | Register new camera from phone |

### Modified Files (12)

| File | Changes |
|:-----|:--------|
| `edge/edge_gui.py` | Video serve, MJPEG live, PTZ API, zone API, recording list, clip serve, storage status |
| `edge/pipeline.py` | Grabber factory (RTSP/file/HTTP), frame → recorder |
| `edge/farm_edge_node.py` | Start recorder, storage manager, ONVIF init, camera sync |
| `edge/alert_engine.py` | Link alerts → video segments, extract 30s clips |
| `edge/requirements.txt` | Add `onvif-zeep` |
| `dashboard/src/pages/CamerasPage.tsx` | MJPEG live, PTZ button, zone button, recordings button |
| `dashboard/src/App.tsx` | Routes for video player, PTZ control, zone drawer |
| `android/.../NavGraph.kt` | Routes for video, PTZ, zone drawer, add camera |
| `android/build.gradle.kts` | Add media3-exoplayer |
| `backend/api-gateway/application.yml` | Proxy routes for edge endpoints |
| `cloud/db/init.sql` | Schema additions above |
| `edge/docker-compose.yml` | SSD + archive volume mounts |

---

## Phase Timeline

| Phase | Tasks | Days | What You Can Do After |
|:------|:------|:-----|:---------------------|
| 1 | 1-4 | 2 | Video recording works, SSD managed, archival running |
| 2 | 5-10 | 2 | Watch live + recorded video on dashboard and Android, see alert clips |
| 3 | 11-14 | 2 | Pan/tilt/zoom cameras from phone while 200km away |
| 4 | 15-17 | 2 | Draw zones on live feed from phone, zones active immediately |
| 5 | 18-20 | 1 | Feed MP4 files for review, register cameras from UI, full remote access |
| **Total** | **20** | **9** | **Complete camera lifecycle** |

---

## Storage Math

```
Sub-stream recording (640×480, 15 FPS, H.264):
  ~150 MB/hour → 3.6 GB/day → 36 GB per camera for 10 days
  8 cameras = 288 GB → 2× 256 GB SSDs

Full-quality recording (1080p/2K, 25 FPS, H.264):
  ~1.5 GB/hour → 36 GB/day → 360 GB per camera for 10 days
  8 cameras = 2.88 TB → 2× 2 TB SSDs

Archive (external HDD, per month):
  Sub-stream: 8 cameras × 108 GB/month = 864 GB/month
  Full quality: 8 cameras × 1.08 TB/month = 8.64 TB/month
  → 4 TB external HDD lasts ~4.5 months (sub) or ~2 weeks (full)
```

Recommendation: Record sub-stream for AI (enough for detection). If you need full-quality evidence, record stream1 on a separate 2TB SSD.
