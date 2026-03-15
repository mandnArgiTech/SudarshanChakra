# SudarshanChakra — User Guide

## Smart Farm Hazard Detection & Security System

---

## 0. For administrators — setup and deployment

This section is for anyone who installs or deploys SudarshanChakra (farm IT, DevOps). End users can skip to [Section 1](#1-introduction).

### Unified setup and build script

From the repository root, use the main script for all setup, build, and deploy tasks:

```bash
./setup_and_build_all.sh --help
```

**Common commands:**

| Command | Purpose |
|--------|--------|
| `install-deps` | Install system packages (Java 21, Node.js 22+, Docker, Python venv, etc.) on Ubuntu 22.04/24.04 |
| `build-all` | Build backend, dashboard, edge, Android, firmware, alertmgmt, and validate cloud configs |
| `build-cloud` | Validate cloud Python scripts and nginx configs, then start PostgreSQL + RabbitMQ containers and init RabbitMQ topology |
| `deploy-all` | Full local dev stack: infra + backend (5 services) + dashboard + edge (background processes) |
| `deploy-docker` | Build Docker images and start the full stack with `docker-compose.vps.yml` (production-style) |

**Options:** `--menu` for an interactive TUI checklist (whiptail/dialog), `--verbose`, `--no-color`.

**Docker deployment (`deploy-docker`):**

- Run from **repo root**: `./setup_and_build_all.sh deploy-docker`.
- If `cloud/.env` is missing, it is **created automatically** from `cloud/.env.example` with dev defaults (`DB_PASS`, `RABBITMQ_PASS`, `JWT_SECRET`). No manual copy needed for first-time local or dev runs.
- For production, create or edit `cloud/.env` with strong passwords and a secure `JWT_SECRET` (e.g. `openssl rand -hex 32`).
- Compose runs from `cloud/` so `cloud/.env` is always used. Dashboard and API are behind Nginx on **port 9080** by default (configurable in `cloud/docker-compose.vps.yml`).

**Ports (reference):**

| Service | Port | Notes |
|--------|------|--------|
| Dashboard (Vite dev) | 3000 | Local dev only |
| API Gateway | 8080 | Local dev; in Docker, reached via Nginx |
| Nginx (Docker stack) | 9080 | Dashboard + API at `http://host:9080` and `http://host:9080/api/` |
| RabbitMQ Management | 15672 | Admin UI |
| PostgreSQL | 5432 | Internal / localhost |

For detailed steps and alternatives, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) and [DEPLOY_AFTER_BUILD.md](DEPLOY_AFTER_BUILD.md).

---

## 1. Introduction

SudarshanChakra is an AI-powered security system for your farm. It uses cameras and wearable tags to detect hazards (snakes, scorpions, fire) and intruders, monitor livestock, and protect children near the pond — all in real time.

### What It Detects

| Hazard | How | Response Time |
|--------|-----|---------------|
| **Intruders** | Cameras + AI person detection | < 3 seconds |
| **Snakes** | Low-mounted cameras + AI | < 5 seconds |
| **Scorpions** | Low-mounted cameras + AI | < 5 seconds |
| **Fire / Smoke** | Cameras + color/temporal analysis | < 8 seconds (3-frame confirmation) |
| **Child near pond** | Camera + wearable fall detector | < 3 seconds |
| **Cow escape** | Camera + containment zone check | < 5 seconds |
| **Worker fall** | Wearable accelerometer | < 3 seconds |

### How Alerts Reach You

1. Camera detects hazard → Edge AI processes → Alert sent to cloud
2. Cloud stores alert → Push notification to your Android phone
3. Dashboard shows alert with camera snapshot
4. Siren can sound automatically or be triggered manually

---

## 2. Dashboard Guide (Web)

### 2.1 Accessing the Dashboard

- **Production (TLS):** **`https://vivasvan-tech.in`** when the full TLS stack is in use.
- **Docker / VPS (HTTP):** If the stack was started with **`./setup_and_build_all.sh deploy-docker`** or **`./cloud/deploy.sh`**, use **`http://<server-ip>:9080`** or **`http://localhost:9080`** when on the server. Port **9080** is the default so SudarshanChakra can coexist with other apps (e.g. Portainer on 80/443). To change it, edit the nginx port in `cloud/docker-compose.vps.yml` (e.g. `"9080:80"`).
- **Local development:** If you ran **`./setup_and_build_all.sh deploy-all`**, the dashboard is at **`http://localhost:3000`** (Vite dev server; API is proxied to 8080).

Log in with your username and password provided by the farm administrator.

### 2.2 Dashboard Home Page

The home page shows at a glance:
- **Active Alerts** — Number of unresolved alerts, with critical count
- **Cameras Online** — How many cameras are working
- **Detections Today** — Total AI detections and escalated alerts
- **Workers On-Site** — Number of active worker tags

Below the stats:
- **Live Alert Feed** — Real-time alerts as they happen, with priority indicators
- **Node Status** — Edge Node A and B health (online/offline, GPU usage)
- **Quick Siren Control** — Emergency siren buttons

### 2.3 Managing Alerts

Navigate to **Alerts** in the sidebar.

**Filter alerts by:**
- Priority: Critical (red), High (orange), Warning (yellow)
- Status: New, Acknowledged, Resolved, False Positive

**Actions on each alert:**
- **ACK** — Acknowledge that you've seen it (requires Manager or Admin role)
- **View** — See full details including camera snapshot
- **Resolve** — Mark the situation as handled
- **False Positive** — Mark as incorrect detection (helps improve AI)

### 2.4 Camera Grid

Navigate to **Cameras** to see all 8 cameras in a 4×2 grid:
- Green dot = camera is active and streaming
- Red "ALERT" badge = active alert on this camera
- Gray dot = camera is offline
- FPS indicator shows current processing speed

### 2.5 Siren Control

Navigate to **Siren** for emergency siren management:
- **TRIGGER ALL** — Sound siren on all farm nodes simultaneously
- **Node A Only / Node B Only** — Target specific area
- **STOP SIREN** — Silence all sirens
- **Activation History** — Who triggered the siren and why

### 2.6 Zone Management

Navigate to **Zones** to manage virtual fence areas:
- View all defined zones per camera
- Each zone shows: name, type, priority, target classes, point count
- Zones can be edited via the Edge Node GUI (see Section 4)

---

## 3. Android App Guide

### 3.1 Installation

Download the APK from the provided distribution link or install from the release page.

### 3.2 Login

Enter your username and password. The app will register for push notifications automatically.

### 3.3 Alert Feed

The home screen shows a scrollable list of alerts:
- **Red circle** = Critical (child near pond, fire)
- **Orange diamond** = High (intruder, snake)
- **Yellow diamond** = Warning (cow escaped)
- Tap any alert to see details + camera snapshot

### 3.4 Alert Detail Screen

Shows:
- Camera snapshot at the moment of detection
- AI confidence percentage
- Zone name and camera ID
- **Acknowledge Alert** button
- **Trigger Siren** button — sounds the farm siren
- **Mark as False Positive** button

### 3.5 Siren Control

Big red button to trigger the emergency siren:
- **TRIGGER ALL** — All nodes sound the siren
- Select specific node (A or B) for targeted response
- **STOP** — Silences all sirens
- Recent activation history shown below

### 3.6 Push Notifications

Critical alerts will wake your phone even in Do Not Disturb mode. You'll see:
- Alert type (e.g., "CHILD near Pond")
- Zone name
- Camera ID
- Tap to open the alert detail

---

## 4. Edge Zone Editor Guide

### 4.1 Accessing the Editor

From a computer on the same network as the Edge Node:
```
http://<edge-node-ip>:5000
```
(e.g., `http://192.168.1.100:5000` or `http://10.8.0.10:5000` via VPN)

### 4.2 Drawing a Zone

1. **Select a camera** from the left sidebar
2. The camera's live snapshot appears in the main area (refreshes every 2 seconds)
3. **Click points** on the image to define the polygon vertices
4. You need at least 3 points to create a valid zone
5. Fill in the zone details:
   - **Zone Name**: e.g., "Pond Danger Zone"
   - **Zone Type**: 
     - *Intrusion Detection* — Alert when person enters
     - *Zero Tolerance* — Alert always, never suppressed by worker tags (use for pond)
     - *Livestock Containment* — Alert when cow LEAVES the area
     - *Hazard Zone* — For snake/scorpion/fire areas
   - **Priority**: Critical, High, or Warning
   - **Target Classes**: Comma-separated (e.g., `person, child, snake`)
6. Click **Save Zone**
7. The zone is immediately active — no restart needed

### 4.3 Managing Zones

- Existing zones are listed in the sidebar with their details
- Click **Delete** to remove a zone
- To edit a zone: delete it and redraw

### 4.4 Tips

- For the **pond zone**, use "Zero Tolerance" type with "critical" priority and target classes "person, child"
- For **perimeter zones**, use "Intrusion Detection" with "high" priority
- For **cattle pen**, draw the pen boundary as "Livestock Containment" — cows inside are OK, cows outside trigger warning
- For **snake areas**, mount cameras LOW (1-1.5m) and use "Hazard Zone" type

---

## 5. Worker Tags Guide

### 5.1 Purpose

Worker tags are ESP32 wearable devices that broadcast a signal via LoRa radio. When a worker wearing a tag is detected by a camera in a restricted zone, the system recognizes them as authorized and **suppresses the alarm** instead of raising a false alert.

### 5.2 Using a Worker Tag

1. Turn on the tag (switch/button)
2. Green LED blink = tag is transmitting
3. Wear it on your belt, arm, or around your neck
4. Enter restricted zones freely — no false alarms
5. Battery lasts ~24-48 hours; recharge via USB-C

### 5.3 Child Safety Tag

The child safety tag includes a **fall detector**:
- Wear on the child's wrist or ankle
- If the child falls (sudden drop + impact), the tag immediately sends a **CRITICAL alert**
- The siren sounds automatically
- Parents receive push notification within 3 seconds

**Important:** The child tag works independently of cameras. Even if the child is outside camera view, the fall detector still works within LoRa range (~200m on farm).

---

## 6. Understanding Alert Priorities

| Priority | Color | Examples | Auto-Siren? |
|----------|-------|----------|-------------|
| **CRITICAL** | 🔴 Red | Child near pond, fire, fall detected | Yes (configurable) |
| **HIGH** | 🟠 Orange | Intruder, snake, scorpion | No (manual trigger) |
| **WARNING** | 🟡 Yellow | Cow outside pen, smoke detected | No |

### Alert Lifecycle

```
NEW → ACKNOWLEDGED → RESOLVED
         ↓
    FALSE POSITIVE (if AI was wrong)
```

---

## 7. Troubleshooting for Users

| Problem | What to Do |
|---------|-----------|
| No alerts appearing | Check if Edge Nodes are online (Dashboard → Devices) |
| Camera shows "offline" | Check camera power and network cable; restart camera |
| Worker tag not suppressing alarms | Ensure tag is ON (LED blinking); check battery |
| Siren not sounding | Check PA system power; check Raspberry Pi is running |
| App not receiving notifications | Check phone notification settings; ensure MQTT foreground service is running; re-login |
| False alerts for workers | Ensure worker has tag turned ON before entering zone |
| "Node offline" alert | Edge Node may have lost power or internet; check physically |
| Docker deploy fails: "JWT_SECRET is missing" | Ensure `cloud/.env` exists. Run `./setup_and_build_all.sh deploy-docker` from repo root — it creates `cloud/.env` from `.env.example` with dev defaults if missing. For production, edit `cloud/.env` and set strong values. |

---

## 8. Safety Reminders

1. **The pond zone is ZERO TOLERANCE** — Even workers trigger alerts near the pond. This is intentional for child safety.
2. **Keep cameras clean** — Dust, rain, or spider webs on lenses cause false positives.
3. **Charge worker tags daily** — A dead tag means the worker will trigger intruder alerts.
4. **Child safety tag must be worn** — The fall detector only works when the tag is on the child.
5. **Test the siren monthly** — Use the dashboard or app to trigger a short test.
6. **Report false positives** — Marking false alerts helps improve the AI over time.

---

## 9. Related documentation

| Document | Audience | Contents |
|----------|----------|----------|
| [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) | Developers | Prerequisites, repo layout, setup script details, testing, contribution |
| [DEPLOY_AFTER_BUILD.md](DEPLOY_AFTER_BUILD.md) | Admins / DevOps | Step-by-step local and VPS deploy, manual and script-based |
| [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) | Admins | Deployment options, TLS, VPN, production checklist |
| [VPS_HEALTH_AND_USAGE.md](VPS_HEALTH_AND_USAGE.md) | Admins | Health checks, monitoring, usage after deploy |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | All | Deeper troubleshooting for build, run, and network issues |
