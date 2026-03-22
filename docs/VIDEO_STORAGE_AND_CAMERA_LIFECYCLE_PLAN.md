# Video Storage & Camera Lifecycle Plan

## Current State — What Exists vs What's Missing

### What EXISTS Today

```
Camera registered in DB (cameras table)
  → Edge reads cameras.json
  → pipeline.py opens RTSP stream via OpenCV
  → Grabs frames at 2-3 FPS
  → YOLO inference on each frame
  → Detection results → zone engine → alert engine → MQTT
  → Alert snapshots saved as JPEG (24h retention, then deleted)
```

### What's MISSING (Big Gaps)

| Gap | Impact |
|:----|:-------|
| **No video recording** | RTSP frames are processed and thrown away. Zero evidence. If a snake was detected at 2 AM, there's only a single JPEG snapshot — no video clip of what happened before, during, and after |
| **No storage management** | No SSD mount configuration, no folder structure, no disk space monitoring |
| **No archival system** | No automatic move to external drive after retention period |
| **No circular overwrite** | Disks fill up and system crashes |
| **No video file input** | Can only process RTSP streams — can't feed an MP4 file or a video stream URL for analysis |
| **Camera registration is disconnected** | Backend has cameras table, edge has cameras.json — they're not synced |

---

## Complete Camera Lifecycle (What It SHOULD Be)

### In Simple Terms

```
1. REGISTER    → You add a camera (RTSP or video file) through the dashboard or app
2. CONNECT     → Edge node connects to the stream and starts recording + AI analysis
3. LIVE        → AI analyzes every frame, alerts on hazards, video records continuously
4. STORE       → Video saved to SSD in 10-minute segments, organized by camera/date/hour
5. RETAIN      → Keep 10 days on fast SSD
6. ARCHIVE     → After 10 days, auto-move to external hard drive (structured, trainable)
7. OVERWRITE   → SSD writes circularly — oldest segments deleted when disk reaches 85%
8. UNREGISTER  → Camera removed — recording stops, but existing videos kept until archived
```

### Data Flow Diagram

```
                    ┌─────────────────────────────────────────────────┐
                    │           CAMERA / VIDEO SOURCE                 │
                    │  RTSP stream  │  MP4 file  │  HTTP stream URL   │
                    └───────┬───────┴─────┬──────┴────────┬──────────┘
                            │             │               │
                            ▼             ▼               ▼
                    ┌─────────────────────────────────────────────────┐
                    │              INPUT ADAPTER                      │
                    │   RtspGrabber │ FileGrabber │ HttpStreamGrabber │
                    └───────────────────────┬─────────────────────────┘
                                            │ raw frames
                            ┌───────────────┼───────────────┐
                            ▼               ▼               ▼
                    ┌──────────────┐ ┌─────────────┐ ┌─────────────┐
                    │ YOLO         │ │ VIDEO       │ │ SNAPSHOT    │
                    │ INFERENCE    │ │ RECORDER    │ │ ON ALERT    │
                    │ (2-3 FPS)    │ │ (full FPS)  │ │ (JPEG)      │
                    └──────┬───────┘ └──────┬──────┘ └──────┬──────┘
                           │                │               │
                    Zone engine       10-min MP4        /snapshots/
                    Alert engine      segments           alert_id.jpg
                    MQTT publish           │
                           │               ▼
                           │        ┌──────────────────────┐
                           │        │    SSD STORAGE        │
                           │        │ /data/video/          │
                           │        │  cam-01/              │
                           │        │   2026-03-22/         │
                           │        │    08/                 │
                           │        │     cam-01_08-00.mp4  │
                           │        │     cam-01_08-10.mp4  │
                           │        │     cam-01_08-20.mp4  │
                           │        └──────────┬───────────┘
                           │                   │ after 10 days
                           │                   ▼
                           │        ┌──────────────────────┐
                           │        │  ARCHIVE (Ext Drive)  │
                           │        │ /archive/video/       │
                           │        │  Same structure       │
                           │        │  + manifest.json      │
                           │        │  + metadata per file   │
                           │        └──────────────────────┘
                           │
                    Dashboard / Android / Simulator
```

---

## Implementation Plan — 12 Tasks

### Task 1: Video Recorder Module

**New file:** `edge/video_recorder.py`

Records the RTSP stream continuously in 10-minute MP4 segments using ffmpeg subprocess. This runs PARALLEL to the YOLO inference pipeline — it records at the camera's full FPS (15-25 FPS) while YOLO only processes 2-3 FPS.

```python
class VideoRecorder:
    """
    Records camera streams into 10-minute MP4 segments.
    Uses ffmpeg subprocess for efficiency (no Python frame-by-frame encoding).
    """
    
    def __init__(self, camera_id, rtsp_url, storage_path, segment_minutes=10):
        self.camera_id = camera_id
        self.rtsp_url = rtsp_url
        self.storage_path = storage_path  # /data/video
        self.segment_minutes = segment_minutes
    
    def start(self):
        """Start ffmpeg recording in background."""
        # ffmpeg -rtsp_transport tcp -i <rtsp_url>
        #   -c:v copy              ← No re-encoding (zero CPU cost)
        #   -f segment             ← Segment muxer
        #   -segment_time 600      ← 10 minutes per file
        #   -segment_format mp4
        #   -strftime 1
        #   -reset_timestamps 1
        #   /data/video/cam-01/2026-03-22/08/cam-01_%H-%M.mp4
```

**Why ffmpeg subprocess instead of OpenCV VideoWriter:**
- **Zero CPU cost**: `-c:v copy` copies the H.264 stream directly, no decoding/re-encoding
- **Full quality**: Records at camera's native resolution and FPS
- **Independent from YOLO**: If YOLO crashes, recording continues
- **Segment muxer**: Built-in 10-minute file splitting

---

### Task 2: Storage Directory Structure

```
/data/video/                          ← SSD mount point (configurable)
├── cam-01/
│   ├── 2026-03-22/
│   │   ├── 00/
│   │   │   ├── cam-01_00-00.mp4      ← 00:00-00:10
│   │   │   ├── cam-01_00-10.mp4      ← 00:10-00:20
│   │   │   ├── cam-01_00-20.mp4
│   │   │   ├── cam-01_00-30.mp4
│   │   │   ├── cam-01_00-40.mp4
│   │   │   └── cam-01_00-50.mp4      ← 6 segments per hour
│   │   ├── 01/
│   │   ├── ...
│   │   └── 23/
│   ├── 2026-03-23/
│   └── ...
├── cam-02/
├── ...
└── _metadata/
    ├── manifest.json                  ← Index of all recordings
    └── cam-01/
        └── 2026-03-22.json           ← Per-day metadata (segments, sizes, alerts)
```

**Why this structure:**
- **Portable**: Copy `cam-01/2026-03-22/` to any machine — self-contained
- **Retrievable**: Know the camera + date + hour → find the file instantly
- **Trainable**: For AI training, just point to `cam-01/` and get all frames
- **Performant**: 6 files per hour per camera, no single huge file to seek through

---

### Task 3: Storage Manager

**New file:** `edge/storage_manager.py`

```python
class StorageManager:
    """
    Manages video storage lifecycle:
    - Monitors disk usage on SSD
    - Circular overwrite: deletes oldest segments when SSD > 85% full
    - Archives to external drive after 10 days
    - Maintains manifest.json index
    """
    
    # Configuration (via env vars)
    SSD_PATH = "/data/video"
    ARCHIVE_PATH = "/archive/video"       # External HDD mount
    RETENTION_DAYS = 10                    # Keep on SSD for 10 days
    SSD_HIGH_WATERMARK = 0.85             # Start deleting at 85%
    SSD_LOW_WATERMARK = 0.70              # Delete until 70%
    ARCHIVE_ENABLED = True
    CHECK_INTERVAL = 3600                  # Check every hour
```

**Circular overwrite logic:**
```
Every hour:
  1. Check SSD disk usage
  2. If usage > 85%:
     a. Find oldest camera/date folders
     b. If older than 10 days AND archive enabled → move to archive first
     c. If older than 10 days AND archive disabled → delete
     d. If younger than 10 days but disk critical (>95%) → delete oldest anyway
     e. Continue until disk < 70%
  3. Update manifest.json
```

---

### Task 4: Archive System

**New file:** `edge/archiver.py`

Archives video to external hard drive maintaining the same structure:

```
/archive/video/                        ← External HDD mount point
├── cam-01/
│   ├── 2026-03-12/                    ← Archived from SSD after 10 days
│   │   ├── 00/
│   │   │   ├── cam-01_00-00.mp4
│   │   │   └── ...
│   │   └── 23/
│   ├── manifest.json                  ← Archive-level manifest
│   └── metadata/
│       └── 2026-03-12.json            ← Day metadata + alert cross-references
├── cam-02/
└── _archive_index.json                ← Master index: all archived dates per camera
```

Each day's metadata file:

```json
{
    "camera_id": "cam-01",
    "date": "2026-03-12",
    "segments": 144,
    "total_size_gb": 12.5,
    "duration_hours": 24.0,
    "alerts_in_period": [
        {
            "alert_id": "abc-123",
            "time": "2026-03-12T02:15:30Z",
            "class": "snake",
            "priority": "critical",
            "segment_file": "02/cam-01_02-10.mp4",
            "snapshot_file": "snapshots/abc-123.jpg"
        }
    ],
    "training_notes": {
        "has_night_footage": true,
        "has_rain_footage": false,
        "detected_classes": ["person", "cow", "dog", "snake"]
    },
    "archived_at": "2026-03-22T03:00:00Z",
    "source_node": "edge-node-a"
}
```

**Why this metadata matters for training:**
- You can search: "show me all archived days with snake detections"
- You can filter: "give me all night footage from cam-03"
- You can export: copy the folder to a training machine — metadata travels with it

---

### Task 5: Video File Input Support

**Modify:** `edge/pipeline.py`

Currently only supports RTSP URLs. Add support for:

| Input Type | How It Works |
|:-----------|:------------|
| RTSP stream | `rtsp://user:pass@ip:554/stream2` (existing) |
| MP4/AVI file | `/data/uploads/incident_video.mp4` (NEW) |
| HTTP stream | `http://server/live/stream.m3u8` (NEW) |
| Directory of images | `/data/frames/cam-01/*.jpg` (NEW — for training review) |

```json
// cameras.json — new source_type field
{
    "cameras": [
        {
            "id": "cam-01",
            "name": "Front Gate",
            "source_type": "rtsp",
            "source_url": "rtsp://farmadmin:pass@192.168.1.201:554/stream2",
            "recording_enabled": true,
            "fps": 2.5
        },
        {
            "id": "video-01",
            "name": "Incident Review",
            "source_type": "file",
            "source_url": "/data/uploads/suspicious_activity.mp4",
            "recording_enabled": false,
            "fps": 5.0,
            "loop": false
        },
        {
            "id": "stream-01",
            "name": "IP Cam HTTP",
            "source_type": "http",
            "source_url": "http://192.168.1.50/live/stream.mjpeg",
            "recording_enabled": true,
            "fps": 2.0
        }
    ]
}
```

The `CameraGrabber` class needs a factory:
```python
def create_grabber(config: CameraConfig) -> CameraGrabber:
    if config.source_type == "file":
        return FileGrabber(config)      # Reads MP4/AVI frame by frame
    elif config.source_type == "http":
        return HttpStreamGrabber(config) # HTTP MJPEG or HLS stream
    else:
        return RtspGrabber(config)       # Existing RTSP grabber
```

---

### Task 6: Camera Registration Sync (Backend ↔ Edge)

Currently cameras are registered in the database but the edge reads a static `cameras.json`. They're disconnected.

**Fix:** Edge node periodically pulls camera config from the backend API:

```python
# edge/camera_sync.py
class CameraSync:
    """
    Syncs camera configuration from backend API to local cameras.json.
    Runs every 5 minutes. If cameras change, signals pipeline to restart.
    """
    
    def sync(self):
        response = requests.get(f"{API_BASE}/api/v1/cameras?nodeId={NODE_ID}")
        remote_cameras = response.json()
        local_cameras = load_local_config()
        
        if remote_cameras != local_cameras:
            save_local_config(remote_cameras)
            signal_pipeline_restart()
```

Also add backend API endpoints:
```
POST   /api/v1/cameras         → Register camera (RTSP, file, or HTTP)
PUT    /api/v1/cameras/{id}    → Update camera config
DELETE /api/v1/cameras/{id}    → Unregister (stops recording, keeps videos)
GET    /api/v1/cameras/{id}/recordings → List video segments
GET    /api/v1/cameras/{id}/recordings/{date} → Segments for a date
```

---

### Task 7: Recording Status in Dashboard

Add to dashboard CamerasPage:
- Recording indicator (red dot) per camera
- Disk usage bar (SSD and archive)
- Link to browse recordings by date/hour
- Video playback of segments (MP4 served via edge Flask)

Add Flask routes on edge:
```
GET /api/recordings/{camera_id}                    → List dates
GET /api/recordings/{camera_id}/{date}             → List segments
GET /api/recordings/{camera_id}/{date}/{filename}  → Serve MP4 file
GET /api/storage/status                            → SSD + archive disk usage
```

---

### Task 8: Alert-Linked Video Clips

When an alert fires, tag which video segment contains it. Also extract a 30-second clip (15s before + 15s after the alert):

```python
# In alert_engine.py, after creating an alert:
def _link_video_clip(self, alert_id, camera_id, timestamp):
    """Find the segment containing this alert and extract a 30s clip."""
    segment = find_segment(camera_id, timestamp)  # e.g., cam-01_02-10.mp4
    clip_path = f"/data/video/_clips/{alert_id}.mp4"
    
    # Extract 30s clip centered on alert time
    offset = timestamp - segment_start_time - 15  # 15s before
    ffmpeg_cmd = f"ffmpeg -ss {offset} -i {segment} -t 30 -c copy {clip_path}"
```

This means when you tap an alert in the dashboard or Android app, you can watch what happened — not just see a single frame.

---

### Task 9: Training Data Export Tool

**New file:** `edge/scripts/export_training_data.py`

Extracts frames from recorded video for YOLO training:

```bash
# Export every 30th frame from cam-01 recordings on March 12
python export_training_data.py \
    --camera cam-01 \
    --date 2026-03-12 \
    --every-nth-frame 30 \
    --output /data/training/export_20260312/

# Export only frames that had detections (for hard-negative mining)
python export_training_data.py \
    --camera cam-01 \
    --date 2026-03-12 \
    --alerts-only \
    --output /data/training/alerts_20260312/
```

Output structure (YOLO-ready):
```
/data/training/export_20260312/
├── images/
│   ├── cam-01_20260312_020530.jpg
│   ├── cam-01_20260312_020600.jpg
│   └── ...
└── labels/
    ├── cam-01_20260312_020530.txt    ← Empty (no detection) = negative example
    ├── cam-01_20260312_020600.txt    ← YOLO format labels if detection existed
    └── ...
```

---

### Task 10: SSD and External Drive Configuration

**New file:** `edge/config/storage.json`

```json
{
    "ssd": {
        "path": "/data/video",
        "retention_days": 10,
        "high_watermark_percent": 85,
        "low_watermark_percent": 70,
        "segment_minutes": 10
    },
    "archive": {
        "enabled": true,
        "path": "/archive/video",
        "auto_archive_after_days": 10,
        "check_interval_hours": 1
    },
    "snapshots": {
        "path": "/data/snapshots",
        "retention_hours": 48
    },
    "clips": {
        "path": "/data/video/_clips",
        "pre_alert_seconds": 15,
        "post_alert_seconds": 15,
        "retention_days": 30
    }
}
```

Docker volume mounts:
```yaml
# edge/docker-compose.yml
volumes:
    - /mnt/ssd1:/data/video        # Primary SSD
    - /mnt/ssd2:/data/video2       # Second SSD (for more cameras)
    - /mnt/ext-hdd:/archive/video  # External USB hard drive
    - /data/snapshots:/data/snapshots
```

---

### Task 11: Multi-SSD Support

For 8 cameras recording 24/7, one SSD fills up fast. Support striping across multiple SSDs:

```json
{
    "ssd_pools": [
        {
            "path": "/data/video",
            "cameras": ["cam-01", "cam-02", "cam-03", "cam-04"]
        },
        {
            "path": "/data/video2",
            "cameras": ["cam-05", "cam-06", "cam-07", "cam-08"]
        }
    ]
}
```

**Storage estimate:**
```
Per camera at 640x480 15FPS H.264:
  ~150 MB/hour → 3.6 GB/day → 36 GB for 10 days

8 cameras × 36 GB = 288 GB for 10 days retention
  → 500 GB SSD handles 4 cameras
  → 2× 500 GB SSDs handle all 8 cameras

Full quality (1080p 25FPS):
  ~1.5 GB/hour → 36 GB/day → 360 GB for 10 days per camera
  → 2× 2TB SSDs for 8 cameras
```

---

### Task 12: Database Schema for Recordings

**Add to:** `cloud/db/init.sql`

```sql
CREATE TABLE video_recordings (
    id BIGSERIAL PRIMARY KEY,
    camera_id VARCHAR(50) REFERENCES cameras(id),
    node_id VARCHAR(50) REFERENCES edge_nodes(id),
    segment_start TIMESTAMPTZ NOT NULL,
    segment_end TIMESTAMPTZ NOT NULL,
    file_path TEXT NOT NULL,                      -- Relative: cam-01/2026-03-22/08/cam-01_08-00.mp4
    file_size_bytes BIGINT,
    duration_seconds REAL,
    resolution VARCHAR(20),
    fps REAL,
    storage_location VARCHAR(20) DEFAULT 'ssd',   -- 'ssd' | 'archive' | 'deleted'
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_recordings_camera_time ON video_recordings(camera_id, segment_start DESC);
CREATE INDEX idx_recordings_storage ON video_recordings(storage_location);

CREATE TABLE video_alerts_link (
    id BIGSERIAL PRIMARY KEY,
    alert_id UUID REFERENCES alerts(id),
    recording_id BIGINT REFERENCES video_recordings(id),
    clip_path TEXT,                                -- _clips/alert-id.mp4
    offset_seconds REAL,                           -- Offset within segment
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## Storage Lifecycle Summary

```
Hour 0:   Camera starts recording → segments to SSD
Hour 1:   6 segments × 8 cameras = 48 MP4 files on SSD
Day 1:    144 segments per camera, ~3.6 GB per camera
Day 10:   SSD at ~288 GB (8 cameras × 36 GB)
Day 10+:  Archiver moves Day 1 → external HDD (same structure + metadata)
          SSD now has Day 2-11
          If SSD > 85%: circular delete kicks in, removes oldest first
Day 30:   External HDD has 20 days of archived video
          SSD always has most recent 10 days
```

---

## File Changes Summary

### New Files

| File | Purpose |
|:-----|:--------|
| `edge/video_recorder.py` | ffmpeg-based continuous recording in segments |
| `edge/storage_manager.py` | Disk monitoring, circular overwrite, retention |
| `edge/archiver.py` | Move old videos to external drive + metadata |
| `edge/camera_sync.py` | Backend API ↔ edge config sync |
| `edge/grabbers/file_grabber.py` | MP4/AVI file input adapter |
| `edge/grabbers/http_grabber.py` | HTTP MJPEG/HLS stream adapter |
| `edge/scripts/export_training_data.py` | Extract frames for AI training |
| `edge/config/storage.json` | Storage configuration |
| `cloud/db/init.sql` additions | video_recordings + video_alerts_link tables |

### Modified Files

| File | Changes |
|:-----|:--------|
| `edge/pipeline.py` | Grabber factory for RTSP/file/HTTP sources |
| `edge/farm_edge_node.py` | Start video recorder + storage manager threads |
| `edge/alert_engine.py` | Link alerts to video segments + extract clips |
| `edge/edge_gui.py` | Recording browser API + video serve routes |
| `edge/config/cameras.json` | Add source_type field |
| `edge/docker-compose.yml` | SSD + archive volume mounts |

---

## Priority Order

**Phase 1 — Record video (2 days):**
Task 1 (video recorder), Task 2 (directory structure), Task 10 (storage config)

**Phase 2 — Storage management (1 day):**
Task 3 (storage manager), Task 4 (archiver), Task 11 (multi-SSD)

**Phase 3 — Video file input (1 day):**
Task 5 (file/HTTP grabbers), Task 6 (camera sync)

**Phase 4 — Integration (2 days):**
Task 7 (dashboard), Task 8 (alert clips), Task 9 (training export), Task 12 (DB schema)
