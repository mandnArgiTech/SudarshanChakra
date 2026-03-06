# AGENT_INSTRUCTIONS.md — SudarshanChakra Implementation Plan

## For Cloud Coding Agents (Claude Code, Cursor, Devin, Windsurf, etc.)

---

## MISSION

Implement the **SudarshanChakra Smart Farm Hazard Detection & Security System** — an enterprise-grade IoT + Edge AI + Cloud platform that detects snakes, scorpions, fire/smoke, intruders, and monitors livestock containment and child safety around a pond on a 3000 sq ft agricultural farm in Sanga Reddy, India.

**Read these reference documents FIRST before writing any code:**
- `docs/BLUEPRINT.md` — Full system architecture, Agile Epics, User Stories, Acceptance Criteria
- `docs/AI_DETECTION_ARCHITECTURE.md` — Deep dive into every AI detection subsystem

---

## REPOSITORY STRUCTURE

```
SudarshanChakra/
├── AGENT_INSTRUCTIONS.md          ★ YOU ARE HERE — master implementation plan
│
├── edge/                          # Edge AI Node (Python, Docker, GPU)
│   ├── Dockerfile                 ✅ DONE — provided foundation
│   ├── requirements.txt           ✅ DONE
│   ├── docker-compose.yml         ✅ DONE
│   ├── farm_edge_node.py          ✅ DONE — main entrypoint
│   ├── pipeline.py                ✅ DONE — RTSP + YOLO inference
│   ├── zone_engine.py             ✅ DONE — virtual fence polygons
│   ├── lora_receiver.py           ✅ DONE — ESP32 LoRa receiver
│   ├── alert_engine.py            ✅ DONE — decision engine
│   ├── detection_filters.py       ✅ DONE — post-processing filters
│   ├── edge_gui.py                ✅ DONE — Flask polygon drawing GUI
│   └── config/
│       ├── cameras.json           ✅ DONE — 8 TP-Link camera configs
│       ├── zones.json             ✅ DONE — 6 zone definitions
│       └── authorized_tags.json   ✅ DONE — worker/child tag registry
│
├── backend/                       # Java Spring Boot Microservices
│   ├── alert-service/             🔨 BUILD — consumes alerts, stores in PG, pushes FCM
│   ├── device-service/            🔨 BUILD — CRUD for nodes, cameras, zones, tags
│   ├── auth-service/              🔨 BUILD — JWT auth, user management
│   ├── siren-service/             🔨 BUILD — siren commands → RabbitMQ
│   └── api-gateway/               🔨 BUILD — Spring Cloud Gateway routing
│
├── dashboard/                     # React.js Admin Dashboard
│   ├── src/                       🔨 BUILD — based on docs/dashboard-mockup.jsx
│   ├── package.json               🔨 BUILD
│   ├── Dockerfile                 🔨 BUILD
│   └── nginx.conf                 🔨 BUILD (static file serving)
│
├── android/                       # Android App (Kotlin + Jetpack Compose)
│   ├── app/src/                   🔨 BUILD — based on docs/android-mockup.jsx
│   ├── build.gradle               🔨 BUILD
│   └── ...                        🔨 BUILD
│
├── cloud/                         # Cloud VPS Infrastructure
│   ├── docker-compose.yml         ✅ DONE
│   ├── .env.example               ✅ DONE
│   ├── db/init.sql                ✅ DONE — PostgreSQL schema
│   ├── rabbitmq/                  ✅ DONE — broker config
│   ├── nginx/nginx.conf           ✅ DONE — reverse proxy
│   └── scripts/                   ✅ DONE — health monitor, RabbitMQ init
│
├── firmware/                      # ESP32 Arduino Firmware
│   ├── worker_beacon/             ✅ DONE — worker tag + child fall detector
│   └── lora_bridge/               ✅ DONE — USB LoRa receiver bridge
│
├── AlertManagement/               ✅ DONE — Raspberry Pi PA siren system
│
├── docs/                          ✅ DONE — Architecture documents & UI mockups
│   ├── BLUEPRINT.md
│   ├── AI_DETECTION_ARCHITECTURE.md
│   ├── dashboard-mockup.jsx       (interactive React wireframe)
│   └── android-mockup.jsx         (interactive Android wireframe)
│
└── .github/workflows/             🔨 BUILD — CI/CD pipelines
```

**Legend:** ✅ DONE = File exists, ready to use | 🔨 BUILD = Agent must implement this

---

## IMPLEMENTATION ORDER (8 PHASES)

Execute phases in this exact order. Each phase builds on the previous.

---

### PHASE 1: Backend — Java Spring Boot Microservices

**Priority: HIGHEST — everything depends on the API**

**Technology:**
- Java 21 + Spring Boot 3.2+
- Gradle (Kotlin DSL)
- Spring Data JPA + PostgreSQL
- Spring AMQP (RabbitMQ consumer)
- Spring Security + JWT
- springdoc-openapi (Swagger UI)
- Docker multi-stage builds

#### Task 1.1: Create `backend/alert-service`

This service consumes alerts from RabbitMQ, stores in PostgreSQL, pushes FCM notifications.

**Files to create:**

```
backend/alert-service/
├── build.gradle.kts
├── Dockerfile
├── src/main/java/com/sudarshanchakra/alert/
│   ├── AlertServiceApplication.java
│   ├── config/
│   │   ├── RabbitMQConfig.java          # Queue bindings, exchange declarations
│   │   └── SecurityConfig.java          # JWT filter
│   ├── model/
│   │   ├── Alert.java                   # JPA entity mapping to alerts table
│   │   └── AlertStatus.java            # Enum: NEW, ACKNOWLEDGED, RESOLVED, FALSE_POSITIVE
│   ├── dto/
│   │   ├── AlertPayload.java           # Incoming MQTT/AMQP payload from edge
│   │   ├── AlertResponse.java          # API response DTO
│   │   └── AlertUpdateRequest.java     # For acknowledging/resolving
│   ├── repository/
│   │   └── AlertRepository.java         # JPA repository with custom queries
│   ├── service/
│   │   ├── AlertConsumerService.java    # @RabbitListener — consumes from queues
│   │   ├── AlertService.java           # Business logic
│   │   ├── FCMService.java             # Firebase Cloud Messaging push
│   │   └── WebSocketService.java       # Real-time alert feed to React dashboard
│   └── controller/
│       └── AlertController.java         # REST API: GET /api/v1/alerts, PATCH status
├── src/main/resources/
│   ├── application.yml
│   └── application-prod.yml
└── src/test/java/...
```

**Key implementation details:**

```java
// AlertConsumerService.java
@RabbitListener(queues = "alert.critical", priority = "10")
public void handleCriticalAlert(String message) {
    AlertPayload payload = objectMapper.readValue(message, AlertPayload.class);
    Alert alert = alertRepository.save(mapToEntity(payload));
    fcmService.sendNotification(alert);  // Push to Android
    webSocketService.broadcast(alert);   // Push to React dashboard
}

// AlertController.java
@GetMapping("/api/v1/alerts")
public Page<AlertResponse> getAlerts(
    @RequestParam(required = false) String priority,
    @RequestParam(required = false) String status,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "createdAt,desc") String sort
) { ... }

@PatchMapping("/api/v1/alerts/{id}/acknowledge")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public AlertResponse acknowledge(@PathVariable UUID id) { ... }
```

**Database table reference:** See `cloud/db/init.sql` — the `alerts` table schema.

**RabbitMQ queues to consume:** `alert.critical`, `alert.high`, `alert.warning`
(See `cloud/scripts/rabbitmq_init.py` for full topology)

---

#### Task 1.2: Create `backend/device-service`

CRUD for edge nodes, cameras, zones, worker tags.

```
backend/device-service/
├── build.gradle.kts
├── Dockerfile
├── src/main/java/com/sudarshanchakra/device/
│   ├── DeviceServiceApplication.java
│   ├── model/
│   │   ├── EdgeNode.java
│   │   ├── Camera.java
│   │   ├── Zone.java
│   │   └── WorkerTag.java
│   ├── repository/
│   │   ├── EdgeNodeRepository.java
│   │   ├── CameraRepository.java
│   │   ├── ZoneRepository.java
│   │   └── WorkerTagRepository.java
│   ├── service/
│   │   └── DeviceService.java
│   └── controller/
│       ├── EdgeNodeController.java      # GET/POST /api/v1/nodes
│       ├── CameraController.java        # GET/POST /api/v1/cameras
│       ├── ZoneController.java          # GET/POST/DELETE /api/v1/zones
│       └── WorkerTagController.java     # GET/POST /api/v1/tags
├── src/main/resources/
│   └── application.yml
```

**Database tables:** `edge_nodes`, `cameras`, `zones`, `worker_tags` (see `cloud/db/init.sql`)

---

#### Task 1.3: Create `backend/auth-service`

JWT authentication, user registration, role-based access.

```
backend/auth-service/
├── build.gradle.kts
├── Dockerfile
├── src/main/java/com/sudarshanchakra/auth/
│   ├── AuthServiceApplication.java
│   ├── config/
│   │   └── SecurityConfig.java
│   ├── model/
│   │   ├── User.java
│   │   └── Role.java                   # ADMIN, MANAGER, VIEWER
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── AuthResponse.java           # { token, refreshToken, user }
│   │   └── UserResponse.java
│   ├── repository/
│   │   └── UserRepository.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── JwtService.java             # Token generation/validation
│   │   └── UserService.java
│   └── controller/
│       ├── AuthController.java          # POST /api/v1/auth/login, /register
│       └── UserController.java          # GET /api/v1/users (admin only)
├── src/main/resources/
│   └── application.yml
```

**Key:** JWT secret from environment variable `JWT_SECRET`. Token expiry: 24 hours. Refresh token: 7 days.

---

#### Task 1.4: Create `backend/siren-service`

Receives siren trigger/stop commands from dashboard/Android, publishes to RabbitMQ.

```
backend/siren-service/
├── build.gradle.kts
├── Dockerfile
├── src/main/java/com/sudarshanchakra/siren/
│   ├── SirenServiceApplication.java
│   ├── model/
│   │   └── SirenAction.java            # Audit trail entity
│   ├── dto/
│   │   ├── SirenRequest.java           # { nodeId, sirenUrl, alertId }
│   │   └── SirenResponse.java
│   ├── repository/
│   │   └── SirenActionRepository.java
│   ├── service/
│   │   └── SirenCommandService.java    # Publishes to RabbitMQ
│   └── controller/
│       └── SirenController.java         # POST /api/v1/siren/trigger, /stop
├── src/main/resources/
│   └── application.yml
```

**RabbitMQ publish:** Exchange `farm.commands`, routing key `farm.siren.trigger` or `farm.siren.stop`.

**Audit:** Every siren action logged to `siren_actions` table with `triggered_by` user ID.

---

#### Task 1.5: Create `backend/api-gateway`

Spring Cloud Gateway — routes all external requests to internal services.

```
backend/api-gateway/
├── build.gradle.kts
├── Dockerfile
├── src/main/java/com/sudarshanchakra/gateway/
│   ├── ApiGatewayApplication.java
│   └── config/
│       ├── GatewayConfig.java           # Route definitions
│       └── CorsConfig.java
├── src/main/resources/
│   └── application.yml
```

**Route mapping:**

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: alert-service
          uri: http://alert-service:8081
          predicates:
            - Path=/api/v1/alerts/**
        - id: device-service
          uri: http://device-service:8082
          predicates:
            - Path=/api/v1/nodes/**,/api/v1/cameras/**,/api/v1/zones/**,/api/v1/tags/**
        - id: auth-service
          uri: http://auth-service:8083
          predicates:
            - Path=/api/v1/auth/**,/api/v1/users/**
        - id: siren-service
          uri: http://siren-service:8084
          predicates:
            - Path=/api/v1/siren/**
```

---

### PHASE 2: React Admin Dashboard

**Build the production dashboard based on `docs/dashboard-mockup.jsx`**

**Technology:**
- React 18 + TypeScript
- Vite build tool
- Tailwind CSS
- Recharts (analytics)
- React Query (API state)
- SockJS + STOMP (WebSocket for live alerts)
- React Router v6
- Nginx (static serving in Docker)

**Pages to implement (see mockup for exact layout):**

```
dashboard/
├── src/
│   ├── App.tsx
│   ├── main.tsx
│   ├── api/
│   │   ├── client.ts              # Axios instance with JWT interceptor
│   │   ├── alerts.ts              # Alert API hooks (React Query)
│   │   ├── devices.ts             # Device/node API hooks
│   │   ├── siren.ts               # Siren API hooks
│   │   └── auth.ts                # Auth API hooks
│   ├── components/
│   │   ├── Layout/
│   │   │   ├── Sidebar.tsx
│   │   │   ├── Header.tsx
│   │   │   └── Layout.tsx
│   │   ├── AlertCard.tsx
│   │   ├── AlertTable.tsx
│   │   ├── CameraGrid.tsx
│   │   ├── NodeStatusCard.tsx
│   │   └── SirenButton.tsx
│   ├── pages/
│   │   ├── DashboardPage.tsx       # Stats cards + live alert feed + node status
│   │   ├── AlertsPage.tsx          # Filterable alert table with ACK/resolve actions
│   │   ├── CamerasPage.tsx         # 4×2 camera grid with status indicators
│   │   ├── SirenPage.tsx           # Big siren trigger button + per-node controls + history
│   │   ├── ZonesPage.tsx           # Zone management (future: inline polygon editor)
│   │   ├── DevicesPage.tsx         # Edge node status, GPU metrics
│   │   ├── WorkersPage.tsx         # Worker tag management
│   │   ├── AnalyticsPage.tsx       # Recharts: alerts over time, by zone, by class
│   │   ├── LoginPage.tsx
│   │   └── SettingsPage.tsx
│   ├── hooks/
│   │   ├── useAlertWebSocket.ts    # WebSocket subscription for live alerts
│   │   └── useAuth.ts             # JWT auth context
│   ├── types/
│   │   └── index.ts               # TypeScript interfaces for all entities
│   └── utils/
│       └── priorities.ts           # Color mapping, priority helpers
├── public/
│   └── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
├── tailwind.config.js
├── Dockerfile                      # Multi-stage: node build → nginx serve
└── nginx.conf                      # Static file serving config
```

**Design:** Use the exact visual style from `docs/dashboard-mockup.jsx` — dark theme (#0a0e17 background), amber accent (#f59e0b), JetBrains Mono for data, red alert indicators.

**WebSocket endpoint:** `wss://vivasvan-tech.in/ws/alerts` — the alert-service broadcasts new alerts in real-time.

---

### PHASE 3: Android App (Kotlin + Jetpack Compose)

**Build the production app based on `docs/android-mockup.jsx`**

**Technology:**
- Kotlin
- Jetpack Compose (UI)
- MVVM + Hilt (DI)
- Retrofit (REST API)
- HiveMQ MQTT client (real-time subscriptions)
- Firebase Cloud Messaging (push notifications)
- Room (offline alert cache)
- Navigation Compose

**Screens to implement (see mockup for exact layout):**

```
android/app/src/main/java/com/sudarshanchakra/
├── SudarshanChakraApp.kt           # Application class, Hilt
├── di/
│   ├── AppModule.kt                # Hilt module: Retrofit, MQTT, Room
│   └── RepositoryModule.kt
├── data/
│   ├── api/
│   │   ├── ApiService.kt           # Retrofit interface
│   │   └── AuthInterceptor.kt      # JWT token interceptor
│   ├── db/
│   │   ├── AppDatabase.kt          # Room database
│   │   └── AlertDao.kt             # Alert DAO for offline cache
│   ├── mqtt/
│   │   └── MqttManager.kt          # HiveMQ MQTT client (MQTTS on 8883)
│   └── repository/
│       ├── AlertRepository.kt
│       ├── AuthRepository.kt
│       └── SirenRepository.kt
├── domain/
│   └── model/
│       ├── Alert.kt
│       ├── User.kt
│       └── SirenAction.kt
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                # Warm earthy tones (from mockup)
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   ├── screens/
│   │   ├── login/
│   │   │   ├── LoginScreen.kt
│   │   │   └── LoginViewModel.kt
│   │   ├── alerts/
│   │   │   ├── AlertFeedScreen.kt       # Main alert list with priority filters
│   │   │   ├── AlertDetailScreen.kt     # Full alert with snapshot + actions
│   │   │   └── AlertViewModel.kt
│   │   ├── siren/
│   │   │   ├── SirenControlScreen.kt    # Big trigger button + per-node + history
│   │   │   └── SirenViewModel.kt
│   │   ├── cameras/
│   │   │   └── CameraGridScreen.kt
│   │   └── devices/
│   │       └── DeviceStatusScreen.kt
│   └── components/
│       ├── AlertCard.kt
│       ├── PriorityBadge.kt
│       ├── SirenButton.kt
│       └── BottomNavBar.kt
├── service/
│   └── FCMService.kt               # Firebase messaging service
└── util/
    └── Constants.kt                 # API base URL, MQTT broker URL
```

**Design:** Use the exact visual style from `docs/android-mockup.jsx` — warm cream background (#f8f7f4), terracotta accent (#c8553d), Georgia serif headings.

**MQTT connection:** `mqtts://vivasvan-tech.in:8883` — subscribe to `farm/alerts/#` for real-time alerts. Publish to `farm/siren/trigger` for siren commands.

**FCM:** alert-service sends push notifications. App must register FCM token on login via `POST /api/v1/users/me/fcm-token`.

---

### PHASE 4: CI/CD Pipelines (GitHub Actions)

Create `.github/workflows/`:

```yaml
# .github/workflows/backend.yml
name: Build & Deploy Backend
on:
  push:
    branches: [main]
    paths: ['backend/**']
jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [alert-service, device-service, auth-service, siren-service, api-gateway]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: cd backend/${{ matrix.service }} && ./gradlew build test
      - run: |
          echo "${{ secrets.GHCR_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
          docker build -t ghcr.io/mandnargitech/sudarshanchakra/${{ matrix.service }}:${{ github.sha }} backend/${{ matrix.service }}
          docker push ghcr.io/mandnargitech/sudarshanchakra/${{ matrix.service }}:${{ github.sha }}
```

```yaml
# .github/workflows/dashboard.yml
name: Build & Deploy Dashboard
on:
  push:
    branches: [main]
    paths: ['dashboard/**']
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd dashboard && npm ci && npm run build && npm test
      - run: |
          docker build -t ghcr.io/mandnargitech/sudarshanchakra/dashboard:${{ github.sha }} dashboard/
          docker push ghcr.io/mandnargitech/sudarshanchakra/dashboard:${{ github.sha }}
```

```yaml
# .github/workflows/edge.yml
name: Build Edge AI Container
on:
  push:
    branches: [main]
    paths: ['edge/**']
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: cd edge && python -m py_compile farm_edge_node.py pipeline.py zone_engine.py alert_engine.py
      - run: |
          docker build -t ghcr.io/mandnargitech/sudarshanchakra/edge-ai:${{ github.sha }} edge/
          docker push ghcr.io/mandnargitech/sudarshanchakra/edge-ai:${{ github.sha }}
```

---

### PHASE 5: VPN & Cloud Infrastructure Deployment

Run on VPS (vivasvan-tech.in):

```bash
# 1. Clone repo
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra/cloud

# 2. Configure secrets
cp .env.example .env
# Edit .env with real passwords

# 3. Initialize OpenVPN (one-time)
docker run -v /opt/openvpn:/etc/openvpn --rm kylemanna/openvpn ovpn_genconfig -u udp://vivasvan-tech.in
docker run -v /opt/openvpn:/etc/openvpn --rm -it kylemanna/openvpn ovpn_initpki

# 4. Generate client configs for Edge Nodes
docker run -v /opt/openvpn:/etc/openvpn --rm kylemanna/openvpn ovpn_getclient edge-node-a > edge-node-a.ovpn
docker run -v /opt/openvpn:/etc/openvpn --rm kylemanna/openvpn ovpn_getclient edge-node-b > edge-node-b.ovpn

# 5. Assign static VPN IPs
echo "ifconfig-push 10.8.0.10 10.8.0.9" > /opt/openvpn/ccd/edge-node-a
echo "ifconfig-push 10.8.0.11 10.8.0.9" > /opt/openvpn/ccd/edge-node-b

# 6. Get TLS certificates
sudo certbot certonly --standalone -d vivasvan-tech.in
cp /etc/letsencrypt/live/vivasvan-tech.in/fullchain.pem certs/
cp /etc/letsencrypt/live/vivasvan-tech.in/privkey.pem certs/

# 7. Start all services
docker compose up -d

# 8. Initialize RabbitMQ topology
docker exec -it rabbitmq pip install pika
docker cp scripts/rabbitmq_init.py rabbitmq:/tmp/
docker exec -it rabbitmq python /tmp/rabbitmq_init.py

# 9. Verify
docker compose ps
curl https://vivasvan-tech.in/health
```

---

### PHASE 6: Edge Node Deployment

On each Edge Node (Node A and Node B):

```bash
# 1. Clone repo
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra/edge

# 2. Copy VPN config (from VPS)
scp user@vivasvan-tech.in:~/edge-node-a.ovpn vpn/

# 3. Configure cameras
# Edit config/cameras.json with real camera IPs and credentials

# 4. Start
docker compose up -d

# 5. Verify
docker logs -f edge-ai
# Should see: "LoRa receiver started", "Flask Edge GUI started on port 5000",
# "Connected to VPS MQTT broker", "Starting inference pipeline"

# 6. Open polygon editor
xdg-open http://localhost:5000
# Draw virtual fence polygons on camera views
```

---

### PHASE 7: ESP32 Firmware Flashing

**Worker Beacon Tags (3 units):**
```bash
cd firmware/worker_beacon
# Open in Arduino IDE or PlatformIO
# Set TAG_ID = "TAG-W001" (unique per tag)
# Set DEVICE_MODE = "WORKER_BEACON"
# Flash to ESP32
```

**Child Safety Tag (1 unit):**
```bash
cd firmware/worker_beacon
# Set TAG_ID = "TAG-C001"
# Set DEVICE_MODE = "CHILD_SAFETY"
# Flash to ESP32 with MPU6050 connected
```

**LoRa Bridge Receiver (2 units — one per Edge Node):**
```bash
cd firmware/lora_bridge
# Flash to ESP32 (no config changes needed)
# Plug into Edge Node USB port
# Verify: screen /dev/ttyUSB0 115200
# Should see LoRa packets flowing
```

---

### PHASE 8: AI Model Training

```bash
# On Edge Node B (RTX 3060) or cloud GPU

# 1. Prepare dataset
# Follow AI_DETECTION_ARCHITECTURE.md Section 2 for dataset sourcing
# Download from Roboflow, COCO, iNaturalist
# Annotate on-site images with CVAT

# 2. Train
cd edge
yolo detect train \
    model=yolov8n.pt \
    data=/datasets/sudarshanchakra_v1/data.yaml \
    epochs=300 imgsz=640 batch=16 patience=50 device=0

# 3. Export to TensorRT
yolo export model=runs/train/sc_v1/weights/best.pt format=engine half=True

# 4. Deploy
cp runs/train/sc_v1/weights/best.engine models/yolov8n_farm.engine
cp runs/train/sc_v1/weights/best.pt models/yolov8n_farm.pt

# 5. Restart edge container to load new model
docker compose restart edge-ai
```

---

## API REFERENCE (for frontend and mobile)

```
Base URL: https://vivasvan-tech.in/api/v1

AUTH:
  POST /auth/login          { username, password }  → { token, refreshToken }
  POST /auth/register       { username, password, email, role }

ALERTS:
  GET  /alerts              ?priority=critical&status=new&page=0&size=20
  GET  /alerts/:id
  PATCH /alerts/:id/acknowledge
  PATCH /alerts/:id/resolve
  PATCH /alerts/:id/false-positive

DEVICES:
  GET  /nodes               → List edge nodes with status
  GET  /nodes/:id
  GET  /cameras             ?nodeId=edge-node-a
  GET  /zones               ?cameraId=cam-03
  POST /zones               { cameraId, name, type, priority, targetClasses, polygon }
  DELETE /zones/:id
  GET  /tags                → Worker/child tags
  POST /tags                { tagId, workerName, role }

SIREN:
  POST /siren/trigger       { nodeId, sirenUrl?, alertId? }
  POST /siren/stop          { nodeId }
  GET  /siren/history       ?page=0&size=20

USERS:
  GET  /users               (admin only)
  PATCH /users/me/fcm-token { token }

WEBSOCKET:
  ws://vivasvan-tech.in/ws/alerts    (STOMP over SockJS)
  Subscribe: /topic/alerts           (real-time new alerts)
  Subscribe: /topic/nodes            (node status changes)
```

---

## TESTING CHECKLIST

After implementation, verify these end-to-end flows:

```
□ 1. Person walks into perimeter zone → alert appears on dashboard within 3 seconds
□ 2. Worker with tag walks into zone → alarm SUPPRESSED, suppression logged
□ 3. Person in zero-tolerance pond zone → CRITICAL alert, NOT suppressed even with tag
□ 4. Fire detected on camera → 3-frame temporal confirmation → alert with snapshot
□ 5. Cow exits pen polygon → livestock containment warning
□ 6. Manager triggers siren from Android app → PA system sounds on farm
□ 7. Manager triggers siren from React dashboard → same result
□ 8. Edge Node loses power → VPN reconnects within 90 seconds
□ 9. Android app receives FCM push notification for critical alert
□ 10. ESP32 child fall detector → CRITICAL alert on all platforms within 3 seconds
□ 11. Login → JWT token → authorized API access → token refresh works
□ 12. Snake detected at night (IR mode) → alert with correct confidence
```

---

## REFERENCE FILES

All existing implementation code is in these directories. **Read before building:**

| What | Where | Lines |
|:-----|:------|:------|
| System Architecture | `docs/BLUEPRINT.md` | 1,700 |
| AI Detection Deep Dive | `docs/AI_DETECTION_ARCHITECTURE.md` | 1,532 |
| React Dashboard Wireframe | `docs/dashboard-mockup.jsx` | 474 |
| Android App Wireframe | `docs/android-mockup.jsx` | 416 |
| PostgreSQL Schema | `cloud/db/init.sql` | 254 |
| Edge AI Python Code | `edge/*.py` | 1,900 |
| Docker Compose (Cloud) | `cloud/docker-compose.yml` | 180 |
| Docker Compose (Edge) | `edge/docker-compose.yml` | 85 |
| ESP32 Firmware | `firmware/` | 500 |
| PA System | `AlertManagement/` | 1,400 |
