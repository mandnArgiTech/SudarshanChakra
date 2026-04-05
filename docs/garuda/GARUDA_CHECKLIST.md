# Release GARUDA — Master Checklist

> Every testable feature. Check = verified working. Update after each story completion.

---

## BACKEND SERVICES

### Auth Service
- [x] User login → JWT with role + farm_id + modules
- [x] User registration with role assignment
- [x] 5 roles: super_admin, admin, manager, operator, viewer
- [x] Farm CRUD (super_admin only)
- [x] User CRUD (admin for own farm)
- [x] /me endpoint returns modules + permissions
- [x] JWT modules claim from farm.modules_enabled
- [ ] Hibernate tenant filter on User entity (G-01)
- [ ] Per-endpoint @PreAuthorize on User/Farm controllers (G-02)
- [x] AOP audit aspect auto-logs @Auditable methods (G-04)
- [x] FarmService unit tests (G-05)
- [x] PermissionService unit tests (G-05)
- [x] ModuleResolutionService unit tests (G-05)

### Alert Service
- [x] Alert creation via REST API
- [x] Alert storage in PostgreSQL
- [x] WebSocket broadcast (/ws/alerts)
- [x] Alert acknowledgment
- [x] Alert priority filtering (critical/high/warning)
- [ ] Tenant filter on Alert entity (G-01)

### Device Service
- [x] Camera CRUD
- [x] Zone CRUD with polygon data
- [x] Edge node management
- [x] Worker tag management
- [x] Water tank CRUD + readings
- [x] Motor/pump control commands
- [x] Camera source_type (rtsp/file/http)
- [x] Camera has_ptz, ptz_presets, recording_enabled fields
- [x] Zone publish MQTT reload on create/delete (G-07)
- [ ] Tenant filter on Camera/Zone/Node entities (G-01)
- [ ] Per-endpoint @PreAuthorize (G-02)

### Siren Service
- [x] Siren trigger command via RabbitMQ
- [x] Siren stop command
- [x] Siren history
- [ ] Tenant filter (G-01)
- [ ] @PreAuthorize on trigger (G-02)

### API Gateway
- [x] Route to all 5 services
- [x] Edge proxy (/edge/**)
- [x] Module access filter (403 for disabled modules)
- [x] ModuleAccessGatewayFilter unit tests (G-05)
- [x] CORS configuration
- [ ] MDM service route /api/v1/mdm/** (M-02)
- [ ] ModuleAccessFilter includes "mdm" module (M-02)

### MDM Service (NEW — WS-4)
- [ ] mdm_devices table + entity + CRUD (M-01, M-02)
- [ ] mdm_app_usage table + ingestion (M-01, M-03)
- [ ] mdm_call_logs table + ingestion (M-01, M-03)
- [ ] mdm_screen_time table + ingestion (M-01, M-03)
- [ ] mdm_location_history table + ingestion (M-01, M-03)
- [ ] mdm_commands table + dispatch via MQTT (M-04)
- [ ] mdm_ota_packages CRUD (M-05)
- [ ] Telemetry batch upload endpoint (M-03)
- [ ] Heartbeat endpoint (M-03)
- [ ] Location history endpoint with date range (M-02)
- [ ] Location interval update → MQTT push (M-02)
- [ ] Device decommission (M-02)
- [ ] Command acknowledge from device (M-04)
- [ ] Unit tests: 17+ @Test (M-12)
- [ ] Docker Compose integration (M-12)

---

## DASHBOARD (React)

### Core Pages
- [x] Login page
- [x] Dashboard home with alert summary
- [x] Alerts page with real-time WebSocket
- [x] Cameras page with grid layout
- [x] Zones page
- [x] Devices page
- [x] Siren control page
- [x] Water page with tank gauge
- [x] Pump/motor control page
- [x] Workers page
- [x] Settings page

### SaaS Admin Pages
- [x] Admin farms page (list)
- [x] Admin users page (list)
- [x] Admin audit page (list)
- [x] Dashboard sidebar actually filters by modules (G-03)

### Camera/Video Pages
- [x] Live MJPEG feed component
- [x] PTZ joystick + control page
- [x] Zone drawing page with SVG polygon
- [x] Video player page with segment list
- [x] Add camera page (RTSP/file/HTTP)
- [x] Alert detail shows video clip player (G-09)

### MDM Pages (NEW — WS-4)
- [ ] MDM device list page (M-11)
- [ ] MDM device detail — screen time chart (M-11)
- [ ] MDM device detail — call log table (M-11)
- [ ] MDM device detail — command panel (M-11)
- [ ] MDM device detail — location map trail (M-11)
- [ ] MDM device detail — location interval dropdown (M-11)
- [ ] MDM nav item in sidebar with module filter (M-11)

### Mobile Responsive
- [x] Collapsible sidebar
- [x] Hamburger menu
- [x] Alert card mobile layout
- [x] Camera grid responsive

### Tests
- [x] 92 test functions across 24 files
- [ ] Admin pages tests (G-05)
- [ ] MDM pages tests (M-12)

---

## ANDROID APP

### Core Screens
- [x] Login screen
- [x] Alert feed with real-time MQTT
- [x] Alert detail with snapshot
- [x] Camera grid
- [x] Siren control with confirmation dialog
- [x] Water tanks screen
- [x] Motor control screen
- [x] Device status screen
- [x] Settings screen with logout

### Production Features
- [x] BackHandler → moveTaskToBack (not kill)
- [x] Notification deep link to alert detail
- [x] Critical alert: ALARM sound + vibration + lock screen visibility
- [x] Boot receiver → auto-start MQTT
- [x] Material Icons (no emojis)
- [x] Dark theme
- [x] Haptic feedback on siren/acknowledge
- [x] Connection banner (MQTT/API status)
- [x] Offline banner
- [x] Relative timestamps
- [x] Swipe-to-dismiss on alerts
- [x] Dynamic bottom nav (module filtering)
- [x] Server settings (dynamic URL)
- [x] Encrypted credential storage
- [x] Pull-to-refresh on all list screens (G-10)

### Camera/Video Screens
- [x] PTZ control screen (joystick, zoom, presets)
- [x] Zone drawer screen (touch polygon)
- [x] Add camera screen
- [x] Video player screen (ExoPlayer)
- [x] ExoPlayer media3 dependency in build.gradle (G-06)
- [x] Alert detail video clip (G-11)

### MDM Kiosk (NEW — WS-4)
- [ ] DeviceAdminReceiver (M-09)
- [ ] KioskManager — lock task, status bar, factory reset block (M-09)
- [ ] KioskLauncherActivity — HOME + app grid (M-09)
- [ ] Force location/mobile data/Wi-Fi ON (M-09)
- [ ] Auto-grant permissions (M-09)
- [ ] TelemetryCollector — usage stats, call log, screen time, location (M-07)
- [ ] Room DB v3 — MDM offline cache entities (M-06)
- [ ] TelemetryUploadWorker — batch upload every 30 min (M-08)
- [ ] FusedLocationProvider — configurable interval tracking (M-08)
- [ ] MdmCommandHandler — MQTT command processing (M-10)
- [ ] SilentInstaller — PackageInstaller session API (M-10)
- [ ] Developer escape hatch — 7-tap PIN dialog (M-09)
- [ ] OTA update progress UI (M-10)

### Tests
- [x] ViewModel unit tests (G-12)
- [ ] UI component tests (G-12)

---

## EDGE AI

### Core Pipeline
- [x] Multi-camera RTSP frame grabbing (2-3 FPS)
- [x] YOLOv8n inference (TensorRT FP16)
- [x] Zone engine (Shapely polygon check)
- [x] Alert engine (throttle, dedup, history)
- [x] Detection filters (post-processing)
- [x] MQTT alert publishing
- [x] Flask GUI (:5000) with 15+ routes

### Video
- [x] Video recorder (ffmpeg -c:v copy segments)
- [x] Storage manager (circular overwrite at 85%)
- [x] Archiver (external HDD + metadata.json)
- [x] MJPEG live stream (/api/live/{cam})
- [x] MP4 serve with HTTP Range (/api/video/)
- [x] Alert clip extraction (30s)
- [x] Recordings list API

### PTZ
- [x] ONVIF controller (pan/tilt/zoom/presets)
- [x] PTZ Flask API (7 routes)

### Input Sources
- [x] RTSP grabber
- [x] File grabber (MP4/AVI)
- [x] HTTP stream grabber

### Missing
- [x] Zone reload via MQTT subscriber (G-07)
- [x] camera_sync.py — pull config from backend (G-08)

### Dev Mode
- [x] Mock camera
- [x] Mock LoRa
- [x] Mock siren

### Tests
- [x] 100 pytest functions across 13 files

---

## SIMULATOR
- [x] 23 predefined scenarios
- [x] MQTT.js client
- [x] Device inventory from API
- [x] Sequence playback
- [x] Auto mode / Chaos mode
- [x] Docker + Nginx

---

## PA SYSTEM
- [x] pa_controller.py (541 lines)
- [x] scheduler.py (morning chants)
- [x] self_test.py (daily check)
- [x] pa_dashboard.py (Flask web UI)
- [x] GPIO LED state machine
- [x] Battery monitor (INA219)
- [ ] Field test on actual hardware

---

## WATER / MOTOR
- [x] ESP8266 → MQTT → RabbitMQ → device-service → PostgreSQL
- [x] Water tank CRUD + readings API
- [x] Motor command API
- [x] Dashboard water page + motor page
- [x] Android water + motor screens
- [x] 19 @Test methods (WaterService, MqttConsumer, controllers)
- [x] DB schema (water_tanks, water_level_readings, motors, run_log)

---

## INFRASTRUCTURE

### Cloud VPS
- [x] Docker Compose (11 services)
- [x] deploy.sh
- [x] Nginx (HTTP :9080 + HTTPS :443, LE mount — G-16)
- [x] PostgreSQL schema
- [x] RabbitMQ config
- [x] OpenVPN tunnel (G-15)
- [x] TLS / HTTPS (G-16)

### CI/CD
- [x] backend.yml workflow
- [x] dashboard.yml workflow
- [x] edge.yml workflow
- [x] test.yml workflow
- [x] Docker image push to GHCR on tag (G-17)

### Deployment
- [x] Farm provisioning script + compose integration (G-18) — [`scripts/deploy_saas_farm.sh`](../../scripts/deploy_saas_farm.sh)
- [x] Compose profiles: full, security, monitoring, water_only on `docker-compose.vps.yml` (G-18)

---

## E2E TESTING
- [x] Preflight checker (35+ checks) (G-13)
- [x] E2E test plan (11 suites, 68 tests)
- [x] e2e_config.example.yml
- [x] Playwright browser tests (G-14)
- [x] Maestro Android emulator tests (G-14)
- [ ] Real camera integration (G-14)
- [ ] Real ESP8266 water sensor integration (G-14)
- [ ] Real siren audio test (G-14)

---

## SUMMARY

```
Total checkboxes:  142
Completed [x]:     102
Remaining [ ]:      40

Completion: ~72% (pre-Garuda baseline)
Target:    100% at Garuda release
```
