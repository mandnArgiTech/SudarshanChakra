# CODE_REVIEW_V1 — Camera, Video & Remote Control Implementation

**Date:** March 22, 2026
**Scope:** `CAMERA_VIDEO_AND_REMOTE_CONTROL_PLAN.md` (20 tasks, 5 phases)
**Commits reviewed:** 7b28512..7ae724e (2 commits, 38 files, +4,033 lines)
**Repo:** 561 files, 87 commits

---

## Task-by-Task Scorecard

### Phase 1: Video Recording + Storage (4 tasks)

| Task | File(s) | Lines | Status | Notes |
|:-----|:--------|:------|:-------|:------|
| T1: Video recorder | `edge/video_recorder.py` | 222 | ✅ DONE | ffmpeg subprocess with `-c:v copy` (zero CPU), segment muxer, `strftime` date/hour directory structure, `RecordingConfig` dataclass, per-camera `CameraRecorder` threads |
| T2: Directory structure | — | — | ✅ DONE | `cam/{YYYY-MM-DD}/{HH}/cam_{HH-MM}.mp4` format via ffmpeg `-strftime 1` |
| T3: Storage manager | `edge/storage_manager.py` | 198 | ✅ DONE | `shutil.disk_usage()` check, high watermark (85%), deletes oldest day folders, calls archiver before delete, periodic `_loop` thread |
| T4: Storage config | `edge/config/storage.json` | 21 | ✅ DONE | `ssd_pools`, `retention_days`, `segment_seconds`, `archive` config. Has a test camera pre-configured |

**Phase 1 verdict: 4/4 complete.** Video recording pipeline is solid — ffmpeg with `-c:v copy` is the right choice for zero-CPU recording. Storage manager has proper disk monitoring and circular overwrite logic.

---

### Phase 2: Video Serving + Playback (6 tasks)

| Task | File(s) | Lines | Status | Notes |
|:-----|:--------|:------|:-------|:------|
| T5: Edge Flask serve MP4 | `edge/edge_gui.py` (modified) | +70 | ✅ DONE | `/api/video/{cam}/{path}` with `send_file(conditional=True)` for Range support. `/api/recordings/{cam}`, `/api/clips/{id}.mp4`, `/api/storage/status` all present |
| T6: MJPEG live stream | `edge/edge_gui.py` (modified) | +25 | ✅ DONE | `/api/live/{cam}` streams from `_snapshot_cache` with `multipart/x-mixed-replace; boundary=frame`. Includes `Content-Length` header. 0.33s sleep (~3 FPS) |
| T7: Dashboard video player | `dashboard/src/pages/VideoPlayerPage.tsx` | 205 | ✅ DONE | HTML5 `<video>` with segment list, date navigation |
| T8: Dashboard live camera feed | `dashboard/src/components/LiveCameraFeed.tsx` | 40 | ✅ DONE | MJPEG `<img>` component with snapshot fallback |
| T9: Android ExoPlayer | `android/.../VideoPlayerScreen.kt` | 359 | ⚠️ PARTIAL | File exists (359 lines) but **media3-exoplayer dependency missing from build.gradle.kts** — will not compile |
| T10: Alert clip extraction | `edge/alert_engine.py` (modified) | +40 | ✅ DONE | `_extract_clip()` method extracts 30s clip (15s before + 15s after) via ffmpeg. Clips saved to `/data/video/clips/{alert_id}.mp4`. Clip path included in alert payload |

**Phase 2 verdict: 5/6 complete, 1 partial.**

---

### Phase 3: ONVIF PTZ Camera Control (4 tasks)

| Task | File(s) | Lines | Status | Notes |
|:-----|:--------|:------|:-------|:------|
| T11: ONVIF controller | `edge/onvif_controller.py` | 231 | ✅ DONE | `OnvifController` class with `get_capabilities()`, `continuous_move()`, `absolute_move()`, `stop()`, `get_presets()`, `goto_preset()`, `set_preset()`. `OnvifManager` registry for multi-camera. `onvif-zeep` in requirements.txt |
| T12: Edge Flask PTZ API | `edge/edge_gui.py` (modified) | +60 | ✅ DONE | 7 routes: `/api/ptz/{cam}/capabilities`, `/move`, `/stop`, `/absolute`, `/presets`, `/preset/goto`, `/preset/save` |
| T13: Dashboard PTZ joystick | `dashboard/src/components/PtzJoystick.tsx` + `PtzControlPage.tsx` | 278 | ✅ DONE | D-pad buttons, zoom slider, preset list, save position. PtzControlPage wraps it with live feed |
| T14: Android PTZ control | `android/.../PtzControlScreen.kt` | 368 | ✅ DONE | Joystick control, zoom slider, presets. 12 references to pan/tilt/zoom/preset |

**Phase 3 verdict: 4/4 complete.** ONVIF implementation is comprehensive — covers all PTZ operations including preset save/recall.

---

### Phase 4: Remote Zone Drawing (3 tasks)

| Task | File(s) | Lines | Status | Notes |
|:-----|:--------|:------|:-------|:------|
| T15: Dashboard zone drawing | `dashboard/src/components/ZoneDrawer.tsx` + `ZoneDrawPage.tsx` | 322 | ✅ DONE | SVG polygon overlay on live feed, 17 refs to polygon/click/SVG, zone config panel, save API call |
| T16: Android touch zone drawing | `android/.../ZoneDrawerScreen.kt` | 344 | ✅ DONE | Canvas drawing with pointerInput, 13 refs to polygon/tap/drawPath |
| T17: Zone sync backend→edge | `DeviceService.java` (modified) | +15 | ⚠️ PARTIAL | `publishZoneReload()` method exists and is called on create/delete. Edge has `zone_engine.reload()`. But **no MQTT subscriber on edge listening for zone reload events** — sync only works if edge Flask API is called directly |

**Phase 4 verdict: 2/3 complete, 1 partial.**

---

### Phase 5: Video File Input + Camera Lifecycle (3 tasks)

| Task | File(s) | Lines | Status | Notes |
|:-----|:--------|:------|:-------|:------|
| T18: File/HTTP grabbers | `edge/grabbers/file_grabber.py` + `http_grabber.py` | 190 | ✅ DONE | FileGrabber (96 lines) reads MP4/AVI frame-by-frame. HttpGrabber (94 lines) handles MJPEG streams. Pipeline.py has 10 refs to source_type/factory |
| T19: Camera registration UI | `AddCameraPage.tsx` + `AddCameraScreen.kt` | 429 | ✅ DONE | Source type toggle (RTSP/file/HTTP), connection test, camera config form. Both dashboard (213 lines) and Android (216 lines) |
| T20: API gateway edge proxy | `application.yml` (modified) | +8 | ✅ DONE | `/edge/**` → strips prefix, proxies to edge Flask. Single wildcard route covers all edge endpoints |

**Phase 5 verdict: 3/3 complete.**

---

## Summary Scorecard

```
Phase 1: Video Recording      4/4  ████████████ 100%
Phase 2: Video Playback        5/6  ██████████░░  83%
Phase 3: PTZ Control           4/4  ████████████ 100%
Phase 4: Zone Drawing          2/3  ████████░░░░  67%
Phase 5: File Input + Lifecycle 3/3  ████████████ 100%

TOTAL: 18/20 tasks complete    ██████████████████░░ 90%
```

---

## Critical Gaps

### GAP 1: Android ExoPlayer dependency missing (BLOCKER)

**Severity:** BLOCKER — app will not compile

`VideoPlayerScreen.kt` (359 lines) references ExoPlayer/Media3 classes but `android/app/build.gradle.kts` has no media3 dependency.

**What's needed:**
```kotlin
// android/app/build.gradle.kts
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")
```

Without this, the entire video playback feature on Android is dead code.

### GAP 2: Zone sync — edge not subscribing to MQTT reload events (HIGH)

**Severity:** HIGH — zones created remotely won't take effect until edge restart

Backend `DeviceService.publishZoneReload()` publishes a zone reload event. Edge `zone_engine.reload()` exists. But there's **no MQTT subscriber on the edge node** that bridges the two.

**Current flow (broken):**
```
Dashboard saves zone → backend API → publishZoneReload() → MQTT message
Edge: ... nobody listening → zone NOT active until restart
```

**What's needed in `farm_edge_node.py`:**
```python
# Subscribe to farm/admin/zone_updated
def on_zone_update(topic, payload):
    log.info("Zone updated, reloading zone engine")
    zone_engine.reload()

mqtt_client.subscribe("farm/admin/zone_updated")
mqtt_client.message_callback_add("farm/admin/zone_updated", on_zone_update)
```

### GAP 3: camera_sync.py not implemented (MEDIUM)

**Severity:** MEDIUM — cameras.json is still static, out of sync with backend

The plan called for `edge/camera_sync.py` that periodically pulls camera config from the backend API and updates `cameras.json`. This file was never created. Camera registration from the dashboard/Android stores in the database but the edge node doesn't pick it up.

**Workaround:** Manual restart of edge pipeline after adding cameras via UI.

---

## Minor Issues

### M1: Archiver metadata lacks alert cross-references

`archiver.py` (148 lines) creates `metadata.json` per archived day, but it only includes file lists and sizes — **no alert cross-references** as specified in the plan. The plan called for:
```json
"alerts_in_period": [
    {"alert_id": "abc-123", "class": "snake", "segment_file": "02/cam-01_02-10.mp4"}
],
"training_notes": {"has_night_footage": true, "detected_classes": ["snake"]}
```

This matters for training data export — you can't search archives by detection class without it.

### M2: No training data export script

The plan specified `edge/scripts/export_training_data.py` for extracting frames from recordings into YOLO-ready format (images/ + labels/). Not implemented. Low priority for now but needed before model retraining.

### M3: Dashboard alert detail page has no video clip player

`alert_engine.py` correctly extracts 30s clips and includes `clip_path` in the alert payload. But the dashboard alert detail page doesn't render a `<video>` element for the clip. The data flows to the backend but the UI doesn't show it.

### M4: Storage config has a hardcoded test camera IP

`edge/config/storage.json` contains:
```json
"rtsp_url": "rtsp://administrator:interOP@123@192.168.68.56:554/stream2"
```
This credential should NOT be in a committed config file. Should be in `.env` or environment variable.

### M5: LiveCameraFeed.tsx is very thin (40 lines)

The component exists but may lack error handling, reconnection on MJPEG stream drop, loading state, and offline fallback. Compare to the mockup which showed recording indicator, PTZ badge, and alert count overlay on each feed.

---

## Zero Tests for 3,400+ Lines of New Code

| New Code | Lines | Tests |
|:---------|:------|:------|
| `edge/video_recorder.py` | 222 | **0** |
| `edge/storage_manager.py` | 198 | **0** |
| `edge/archiver.py` | 148 | **0** |
| `edge/onvif_controller.py` | 231 | **0** |
| `edge/grabbers/file_grabber.py` | 96 | **0** |
| `edge/grabbers/http_grabber.py` | 94 | **0** |
| `dashboard/VideoPlayerPage.tsx` | 205 | **0** |
| `dashboard/PtzJoystick.tsx` | 236 | **0** |
| `dashboard/ZoneDrawer.tsx` | 251 | **0** |
| `dashboard/AddCameraPage.tsx` | 213 | **0** |
| `dashboard/LiveCameraFeed.tsx` | 40 | **0** |
| `android/VideoPlayerScreen.kt` | 359 | **0** |
| `android/PtzControlScreen.kt` | 368 | **0** |
| `android/ZoneDrawerScreen.kt` | 344 | **0** |
| `android/AddCameraScreen.kt` | 216 | **0** |
| **Total: 3,421 lines** | | **0 tests** |

This is the same pattern as the SaaS implementation — substantial new code with zero test coverage. The E2E test plan will catch integration issues, but unit/component tests for ONVIF controller, storage manager, video recorder, zone drawer, and PTZ joystick are needed.

---

## What Was Done Well

| Item | Quality |
|:-----|:-------|
| **ffmpeg -c:v copy** | Exactly right — zero CPU, copies H.264 stream directly. Segment muxer with strftime for clean directory structure |
| **ONVIF controller** | 231 lines, clean class hierarchy. Covers all PTZ operations: continuous, absolute, stop, presets (list/goto/save). OnvifManager registry for multi-camera |
| **MJPEG live stream** | Correct `multipart/x-mixed-replace` with Content-Length. Reads from inference cache, not re-opening RTSP — efficient |
| **Alert clip extraction** | 30-second clips with 15s before/after. Uses ffmpeg `-ss` for efficient seeking. Clip path included in alert payload |
| **Zone drawing (both platforms)** | Dashboard SVG polygon (251 lines) + Android Canvas (344 lines) — substantial implementations with proper touch/click handling |
| **PTZ (both platforms)** | Dashboard joystick (236 lines) + Android (368 lines) — D-pad, zoom slider, presets all present |
| **API gateway wildcard proxy** | Clean `/edge/**` → StripPrefix=1 → edge Flask. One route covers all edge endpoints |
| **DB migration** | V3 schema adds video_recordings, alert_video_clips, source_type, has_ptz, ptz_presets, recording_enabled, onvif_info to cameras table |
| **Zone sync trigger** | Backend `publishZoneReload()` on create/delete exists — just needs the edge MQTT subscriber to complete the circuit |

---

## Recommended Fix Priority

| # | Gap | Severity | Effort | Impact |
|:--|:----|:---------|:-------|:-------|
| 1 | media3-exoplayer dependency | **BLOCKER** | 5 minutes | Android won't compile |
| 2 | Edge MQTT zone reload subscriber | **HIGH** | 1 hour | Remote zone drawing doesn't take effect |
| 3 | camera_sync.py | **MEDIUM** | 0.5 day | Camera registration from UI doesn't reach edge |
| 4 | Dashboard alert clip player | **MEDIUM** | 2 hours | Video evidence not shown on alerts |
| 5 | Archiver metadata enrichment | **LOW** | 0.5 day | Training data search not possible |
| 6 | Config credential cleanup | **LOW** | 10 min | Security hygiene |
| 7 | Tests for 3,400 lines | **MEDIUM** | 3 days | Zero coverage on critical new code |

**Total to close all gaps: ~4.5 days of agent work.**

---

## Overall Project Status After Camera/Video Implementation

```
561 files │ 87 commits │ 137+ Java │ 71+ Kotlin │ 40+ Python │ 70+ TSX

Phase completion:
  Backend:           96%  │ Dashboard:    95%  │ Android:       93%
  CI/CD:            100%  │ Cloud VPS:   100%  │ Edge AI:       92%
  Firmware:         100%  │ PA System:    80%  │ Water/Motor:   95%
  Simulator:         90%  │ SaaS:         72%  │ Camera/Video:  90%

New since last review:
  +4,033 lines across 38 files
  +19 new files (edge, dashboard, Android, DB migration)
  +7 new Flask API routes (video, live, PTZ, recordings, clips, storage)
  +7 new dashboard routes
  +4 new Android screens

Tests: 331+ functions (unchanged — zero tests added for 3,400 new lines)
```
