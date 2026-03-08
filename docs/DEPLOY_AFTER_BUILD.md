# Deploy After Successful Build

After `./setup_and_build_all.sh` (or equivalent) succeeds, use these steps to run or deploy each part.

---

## 1. Local / development (same machine)

You already have **PostgreSQL** and **RabbitMQ** running (started by the script). Optional: init RabbitMQ topology if not done:

```bash
cd /path/to/SudarshanChakra
source .venv/bin/activate   # or use system Python
RABBITMQ_PASS=devpassword123 python3 cloud/scripts/rabbitmq_init.py
```

**Backend (one terminal per service, or run in background):**

```bash
cd backend
./gradlew :auth-service:bootRun        # port 8083
./gradlew :device-service:bootRun      # port 8082
./gradlew :alert-service:bootRun       # port 8081
./gradlew :siren-service:bootRun       # port 8084
./gradlew :api-gateway:bootRun         # port 8080
```

**Dashboard:**

```bash
cd dashboard
npm run dev   # Vite dev server, e.g. port 3000; proxies /api to 8080, /ws to 8081
```

**Android:** Install the built APK on a device or emulator:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

**Edge (optional, needs GPU + cameras):** Run Python directly or use Docker; see Edge deployment below.

---

## 2. Cloud VPS deployment

Target: run the full stack (PostgreSQL, RabbitMQ, backend services, dashboard, Nginx) on a server (e.g. Ubuntu 22.04/24.04).

### 2.1 One-time setup on the VPS

```bash
# Clone repo
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra/cloud

# Environment
cp .env.example .env
# Edit .env: set DB_PASS, RABBITMQ_PASS, JWT_SECRET (e.g. openssl rand -hex 32)
```

### 2.2 Deploy with script (recommended on VPS with Docker)

From **repo root**, run the deployment script. It builds all backend and dashboard images, starts the stack with `docker-compose.vps.yml`, and initializes RabbitMQ (HTTP-only; no TLS).

```bash
cd /path/to/SudarshanChakra
./cloud/deploy.sh
```

- **First time:** Copy `cloud/.env.example` to `cloud/.env` and set `DB_PASS`, `RABBITMQ_PASS`, `JWT_SECRET` (e.g. `openssl rand -hex 32`). The script creates `cloud/.env` from the example if missing.
- **Options:** `./cloud/deploy.sh --no-build` to skip building images; `./cloud/deploy.sh --build-only` to only build images.
- **Ports:** Dashboard and API are behind Nginx. If host port 80 is free, edit `cloud/docker-compose.vps.yml` and set nginx ports to `"80:80"`. By default the script uses **9080** so SudarshanChakra does not conflict with other apps on the VPS (e.g. Portainer on vivasvan-tech.in).
- **Access:** Dashboard: `http://<vps-ip>:9080`, API: `http://<vps-ip>:9080/api/v1/`. See [VPS_HEALTH_AND_USAGE.md](VPS_HEALTH_AND_USAGE.md) for health checks and usage.

### 2.3 Build images manually and run (option A — no script)

If your cloud compose supports building from source (e.g. `build: .` instead of `image: ghcr.io/...`), from repo root:

```bash
# Build backend images (from repo root)
cd backend
./gradlew :alert-service:bootJar :device-service:bootJar :auth-service:bootJar :siren-service:bootJar :api-gateway:bootJar

# Build dashboard (from repo root)
cd ../dashboard
npm run build

# Then use a docker-compose that builds from Dockerfiles, or build each image:
# docker build -t sudarshanchakra/alert-service:latest -f backend/alert-service/Dockerfile ...
# (Dockerfiles may expect to be run from backend/ with context; adjust paths as needed.)
```

The repo’s `cloud/docker-compose.yml` references **pre-built images** at `ghcr.io/mandnargitech/sudarshanchakra/*`. To run without a registry you have two options:

- Use **option B** (push to a registry after building locally), or  
- Add a **compose override** that uses `build:` and local Dockerfiles (build context must be set so each service’s Dockerfile can see the root Gradle files).

### 2.4 Pull pre-built images and run (option B — CI/CD or manual push)

If images are built and pushed to a registry (e.g. GitHub Actions → GHCR):

```bash
cd SudarshanChakra/cloud
docker compose pull
docker compose up -d

# Initialize RabbitMQ topology (once). From repo root, with pika installed:
# RABBITMQ_PASS=<from .env> python3 cloud/scripts/rabbitmq_init.py
# Or without host pika (uses same network as compose):
# docker run --rm --network cloud_default -e RABBITMQ_HOST=rabbitmq -e RABBITMQ_USER=admin -e RABBITMQ_PASS=$RABBITMQ_PASS -v $(pwd)/scripts/rabbitmq_init.py:/script.py:ro python:3.12-slim bash -c "pip install -q pika && python /script.py"
```

### 2.5 TLS and Nginx

- Put TLS certs in `cloud/certs/` (e.g. from Let’s Encrypt).  
- Nginx config is in `cloud/nginx/nginx.conf`.  
- Open firewall: 80, 443, and optionally 1194/udp (OpenVPN), 8883 (MQTTS).

---

## 3. Edge node deployment (farm site)

Runs on a machine with **NVIDIA GPU** and **cameras** (and optionally LoRa USB receiver).

### 3.1 Build the Edge image (on a machine with Docker)

```bash
cd SudarshanChakra/edge
docker compose build
# Or: docker build -t sudarshanchakra/edge-ai:latest .
```

### 3.2 On the edge machine (e.g. Ubuntu 22.04)

- Install NVIDIA driver and `nvidia-container-toolkit`.  
- Copy the repo (or at least `edge/` plus `cloud/db/init.sql` if needed).  
- Copy VPN client config (e.g. `edge-node-a.ovpn`) into `edge/vpn/`.  
- Configure `edge/config/cameras.json` and `edge/config/zones.json` (or draw zones via Flask GUI after first start).

```bash
cd SudarshanChakra/edge
# Create .env with NODE_ID, MQTT_USER, MQTT_EDGE_PASS
docker compose up -d
docker logs -f edge-ai
```

- Open `http://<edge-ip>:5000` to draw zones and verify cameras.

---

## 4. Android APK distribution

- **Debug APK** (after `./setup_and_build_all.sh` or `./gradlew assembleDebug`):  
  `android/app/build/outputs/apk/debug/app-debug.apk`  
  Install via USB: `adb install -r app-debug.apk` or share the file.

- **Release APK** (for production): configure signing in `android/app/build.gradle.kts`, then:

  ```bash
  cd android
  ANDROID_HOME=/path/to/sdk ./gradlew assembleRelease
  ```

  Output: `app/build/outputs/apk/release/app-release.apk`. Distribute via Play Store, sideload, or your channel.

---

## 5. Raspberry Pi PA system (AlertManagement)

On the Pi (e.g. Pi Zero 2 W):

```bash
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra/AlertManagement
sudo bash setup.sh
# Edit config/pa.env (MQTT broker IP)
sudo systemctl start pa-controller
sudo systemctl enable pa-controller
```

---

## Quick reference

| Component      | After build / deploy                                      |
|----------------|------------------------------------------------------------|
| **Local stack**| Postgres + RabbitMQ already up; run backend + dashboard.   |
| **Cloud VPS**  | `./cloud/deploy.sh` (or `cloud/.env` → `docker compose -f docker-compose.vps.yml up -d` with built images). |
| **Edge**       | `edge/docker compose up -d` on GPU machine with VPN + config. |
| **Android**    | Install `app-debug.apk` or `app-release.apk`.             |
| **PA system**  | `AlertManagement/setup.sh` then `systemctl start pa-controller`. |

For full server hardening, VPN, and TLS, see **DEPLOYMENT_GUIDE.md**.
