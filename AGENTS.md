# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

SudarshanChakra is an enterprise smart farm hazard detection & security system (IoT + Edge AI + Cloud). It is a monorepo with these components:

| Component | Stack | Status |
|-----------|-------|--------|
| `backend/` | Java 21, Spring Boot 3.2, Gradle (Kotlin DSL) | `alert-service` (8081), `siren-service` (8084), `auth-service` (8083), `device-service` (8082), `api-gateway` (8080) implemented |
| `dashboard/` | React 18, Vite, TypeScript, Tailwind CSS | Scaffolded (`package.json` only; source files need implementation) |
| `edge/` | Python 3.12, YOLO, Flask, OpenCV | Fully implemented (7 `.py` files) |
| `cloud/` | Docker Compose, PostgreSQL 16, RabbitMQ 3 | Fully implemented (infrastructure configs) |
| `firmware/` | C++ / Arduino (ESP32) | Fully implemented |
| `android/` | Kotlin 1.9, Jetpack Compose, Hilt, Retrofit, Room, MQTT | Fully implemented (Gradle 8.5, AGP 8.2.2) |
| `AlertManagement/` | Python (Raspberry Pi PA system) | Fully implemented |

See `AGENT_INSTRUCTIONS.md` for the full implementation plan and phased build order.

### Full monorepo build & test (canonical path)

From the repo root, [setup_and_build_all.sh](setup_and_build_all.sh) orchestrates **all components** in dependency order:

| Command | What it runs |
|---------|----------------|
| `./setup_and_build_all.sh install-deps` | One-time Ubuntu/Debian packages (Java 21, Node, Python tooling, optional Docker, `arduino-cli`, Android cmdline-tools) |
| `./setup_and_build_all.sh build-all` | **build-cloud** → **build-backend** (Gradle `clean build -x test`) → **build-dashboard** → **build-edge** → **build-android** → **build-alertmgmt** → **build-firmware** |
| `./setup_and_build_all.sh test-all` | **test-backend** (`./gradlew test`) → **test-dashboard** (Vitest) → **test-edge** (pytest) → **test-android** (when `ANDROID_HOME` is set) |

**Environment skips** (optional):

- `SKIP_DASHBOARD=1` — skip dashboard npm build in `build-all`
- `SKIP_ANDROID=1` — skip Android `assembleDebug` in `build-all`
- `SKIP_FIRMWARE=1` — skip ESP32 `arduino-cli compile` in `build-all`

**Note:** `build-backend` packages JARs **without** running unit tests; use **`test-backend`** or **`test-all`** for JUnit. For a full verification pipeline locally: `build-all` then `test-all`.

### Infrastructure services

Start PostgreSQL and RabbitMQ via Docker (without the `deploy.resources` limits that fail in nested containers):

```bash
sudo docker network create sc-net 2>/dev/null
sudo docker run -d --name postgres --network sc-net \
  -e POSTGRES_DB=sudarshanchakra -e POSTGRES_USER=scadmin -e POSTGRES_PASSWORD=devpassword123 \
  -p 127.0.0.1:5432:5432 \
  -v /workspace/cloud/db/init.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro \
  postgres:16-alpine
sudo docker run -d --name rabbitmq --network sc-net --hostname farm-broker \
  -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=devpassword123 \
  -p 5672:5672 -p 1883:1883 -p 15672:15672 \
  -v /workspace/cloud/rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro \
  rabbitmq:3-management
```

**Gotchas:**
- The `cloud/docker-compose.yml` includes `deploy.resources.limits.memory` on the `postgres` service, which fails in nested Docker (cgroup v2 threaded mode). Use `docker run` directly without resource limits instead.
- The `cloud/rabbitmq/rabbitmq.conf` references TLS certificate paths (`/etc/rabbitmq/certs/`) that don't exist locally. Start RabbitMQ **without** mounting that config file to avoid prelaunch failures. Only mount `enabled_plugins`.
- RabbitMQ Management UI: http://localhost:15672 (admin / devpassword123)
- PostgreSQL: `localhost:5432`, database `sudarshanchakra`, user `scadmin`, password `devpassword123`
- **Full port + credential matrix:** [docs/PORTS_AND_CREDENTIALS.md](docs/PORTS_AND_CREDENTIALS.md)

### Backend (Java/Spring Boot)

- Java 21 is pre-installed. Gradle wrapper is in `backend/` (`./gradlew`).
- `settings.gradle.kts` references 5 subprojects. `alert-service`, `auth-service`, `device-service`, and `api-gateway` have `build.gradle.kts`. Build individual subprojects: e.g. `./gradlew :auth-service:build`
- **alert-service** (port 8081): Alert CRUD, RabbitMQ consumer, WebSocket broadcast. Run: `./gradlew :alert-service:bootRun`
- **siren-service** (port 8084): Siren trigger/stop via RabbitMQ, audit log. Run: `./gradlew :siren-service:bootRun`
- **auth-service** (port 8083): JWT authentication, user registration/login. Run: `./gradlew :auth-service:bootRun`
- **device-service** (port 8082): CRUD for edge nodes, cameras, zones, worker tags. Run: `./gradlew :device-service:bootRun`
- All services use `spring.jpa.hibernate.ddl-auto=validate` — the PostgreSQL schema must already exist (loaded from `cloud/db/init.sql`).
- After starting PostgreSQL and RabbitMQ, run `RABBITMQ_PASS=devpassword123 python3 cloud/scripts/rabbitmq_init.py` to create the messaging topology.
- The `api-gateway` (Spring Cloud Gateway on Netty) excludes `spring-boot-starter-web` and `spring-boot-starter-tomcat` — do NOT add those deps to it. Run: `./gradlew :api-gateway:bootRun` (port 8080).
- CI/CD: `.github/workflows/backend.yml` runs matrix builds for all 5 services.

### Dashboard (React)

- Node.js 22+ and npm are pre-installed. Run `npm install` in `dashboard/`.
- Fully implemented with Vite 5, React 18, TypeScript, Tailwind CSS 3.
- Build: `npm run build` (runs `tsc && vite build`)
- Lint: `npm run lint` (ESLint 8 with `@typescript-eslint`, `react-hooks`, `react-refresh` plugins)
- Dev server: `npm run dev` (Vite on port 3000, proxies `/api` to `localhost:8080` and `/ws` to `localhost:8081`). If you see **`read EIO`** from readline (SSH/tmux), use **`npm run dev:bg`** (`CI=true vite`). `setup_and_build_all.sh deploy-dashboard` uses `dev:bg` for background starts.
- `sockjs-client` requires `global: 'globalThis'` in `vite.config.ts` `define` — without this the app crashes with "global is not defined".
- The dashboard uses fallback mock data when backend APIs are unavailable, so it renders fully without running backend services.
- Auth uses JWT stored in `localStorage` (`sc_token`, `sc_user`). Unauthenticated users are redirected to `/login`.
- **Cameras page:** Cards are clickable for details. Browsers cannot play RTSP directly. For live JPEG thumbnails (refreshed ~every 3s), set `VITE_EDGE_SNAPSHOT_BASE` to the edge Flask GUI origin (e.g. `http://192.168.68.50:5000`); the dashboard loads `/api/snapshot/<camera_id>` — camera IDs must match device-service and the running edge pipeline. See `dashboard/.env.example`.

### Edge AI (Python)

- Python 3.12 is pre-installed. Install deps: `pip install -r edge/requirements.txt`
- Syntax-check all files: `python3 -m py_compile edge/*.py`
- Lint: `python3 -m flake8 --max-line-length=120 edge/*.py`
- The edge services require NVIDIA GPU + RTSP cameras to run the inference pipeline. The Flask GUI (`edge_gui.py`) also depends on OpenCV for generating placeholder images.
- **Storage config (`storage.json`):** The repo uses placeholders only — never commit real RTSP credentials. Copy `edge/config/storage.json.example` to `storage.json` (or set **`VIDEO_STORAGE_CONFIG`** to an untracked JSON path). Optional overrides without editing the file: **`TEST_CAMERA_RTSP`**, **`TEST_CAMERA_STREAM1`** (merged into the `test_camera` block). Default path is `CONFIG_DIR/storage.json` (`/app/config` in containers).
- **Edge tests:** `pytest edge/tests/ -q` (install: `pip install -r edge/requirements-dev.txt` or `pip install pytest`).
- **Camera sync (optional):** `edge/camera_sync.py` writes `CONFIG_DIR/cameras.json` from device-service. Set `DEVICE_SERVICE_URL` (e.g. `http://localhost:8080/api/v1`), `EDGE_NODE_ID` / `NODE_ID`, `CAMERA_SYNC_TOKEN` (JWT), and run `python3 -m camera_sync` from `edge/`, or enable `CAMERA_SYNC_ENABLED=true` on the edge node for a background thread (`CAMERA_SYNC_INTERVAL_SEC`, default 900). **Restart the edge process** to reload the inference pipeline after cameras change.

### Android (Kotlin/Compose)

- `ANDROID_HOME` must be set to `/opt/android-sdk`. The SDK has `platforms;android-34` and `build-tools;34.0.0`.
- Java 21 is pre-installed; the app targets `jvmTarget = "17"`.
- Gradle wrapper (8.5) is in `android/`. Build: `cd android && ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug --no-daemon`
- Unit tests: `cd android && ANDROID_HOME=/opt/android-sdk ./gradlew testDebugUnitTest --no-daemon`
- The HiveMQ MQTT client pulls in Netty jars whose `META-INF/INDEX.LIST` conflicts — the `packaging.resources.excludes` block in `app/build.gradle.kts` handles this.
- `Tag` was renamed to `WorkerTag` to avoid collision with `retrofit2.http.Tag`.
- No Android emulator is available in this environment; builds produce an APK at `app/build/outputs/apk/debug/app-debug.apk` but cannot be run on-device.

### Startup order for services

1. Start Docker daemon (see Docker gotcha below)
2. Start or create PostgreSQL and RabbitMQ containers (`sudo docker start postgres rabbitmq` if they already exist)
3. Wait for both to be ready, then run `RABBITMQ_PASS=devpassword123 python3 cloud/scripts/rabbitmq_init.py`
4. Start backend services: `auth-service`, `device-service`, `alert-service`, `siren-service`, then `api-gateway`
5. Start dashboard: `cd dashboard && npm run dev`

**Docker in nested container gotcha:** The Docker daemon needs to be started manually with `sudo dockerd &`. The daemon config at `/etc/docker/daemon.json` must use `fuse-overlayfs` storage driver, and `iptables-legacy` must be selected via `update-alternatives`. These are pre-configured in the VM snapshot.

**Auth API path convention:** REST uses the `/api/v1/` prefix (e.g. `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/alerts`). Device inventory is under `/api/v1/nodes`, `/api/v1/cameras`, `/api/v1/zones`, `/api/v1/tags` (not `/devices/...`). Spring Security permits `/api/v1/auth/**` unauthenticated; other paths typically require a `Bearer` JWT (device-service may allow local unauthenticated POST in dev — see service config).

### Dev credentials (local only)

See `cloud/.env` (generated from `.env.example`):
- `DB_PASS=devpassword123`
- `RABBITMQ_PASS=devpassword123`
- `JWT_SECRET=devsecret1234567890abcdef...`
