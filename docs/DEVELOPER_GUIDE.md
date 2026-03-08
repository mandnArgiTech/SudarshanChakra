# SudarshanChakra — Developer Guide

## Setup, Code Structure, Testing, and Contribution Guidelines

---

## 1. Development Environment Setup

### 1.1 Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21 (Temurin) | Backend Spring Boot microservices |
| Node.js | 20+ | React dashboard |
| Python | 3.12+ | Edge AI pipeline, scripts |
| Docker | 24+ | Infrastructure services, edge containers |
| Gradle | 8.7 (via wrapper) | Java build tool |
| npm | 10+ | Node package manager |

### 1.2 Single-Command Setup + Build (Recommended)

```bash
git clone https://github.com/mandnArgiTech/SudarshanChakra.git
cd SudarshanChakra
chmod +x setup_and_build_all.sh
./setup_and_build_all.sh
```

This script performs end-to-end local setup and build for the full monorepo:
- Checks toolchain + GPU visibility (`nvidia-smi`) + Docker daemon
- Creates `.venv` and installs Python deps (`edge/requirements.txt`, `pytest`, `flake8`, `pika`)
- Installs dashboard dependencies (`npm install`)
- Starts PostgreSQL + RabbitMQ containers and initializes RabbitMQ topology
- Builds backend microservices (`./gradlew clean build`)
- Builds/tests dashboard (`lint`, `build`, `vitest --run`)
- Validates Edge AI code (`py_compile`) and reports `flake8`/`pytest` results as warnings when baseline issues exist
- Builds Android app (`assembleDebug`, `testDebugUnitTest`)
- Syntax-checks `AlertManagement` scripts
- Compiles firmware sketches if `arduino-cli` is installed

Optional flags:

```bash
# Skip Android build when SDK is not installed yet
SKIP_ANDROID=1 ./setup_and_build_all.sh

# Skip firmware compile when arduino-cli/toolchain is not present
SKIP_FIRMWARE=1 ./setup_and_build_all.sh

# Use custom Android SDK location
ANDROID_HOME=/path/to/android-sdk ./setup_and_build_all.sh
```

### 1.3 Repository Clone & Manual First Setup

```bash
# Backend: download Gradle dependencies
cd backend && ./gradlew :alert-service:dependencies && cd ..

# Dashboard: install npm packages
cd dashboard && npm install && cd ..

# Edge: install Python packages
pip install -r edge/requirements.txt
pip install flake8  # for linting
```

### 1.4 Infrastructure Services (Docker)

For **local development**, start PostgreSQL and RabbitMQ (or use `./setup_and_build_all.sh`, which does this and sets `RABBITMQ_ERLANG_COOKIE` and a named volume):

```bash
# Start PostgreSQL and RabbitMQ for local development
docker network create sc-net
docker run -d --name postgres --network sc-net \
  -e POSTGRES_DB=sudarshanchakra -e POSTGRES_USER=scadmin -e POSTGRES_PASSWORD=devpassword123 \
  -p 127.0.0.1:5432:5432 \
  -v $(pwd)/cloud/db/init.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro \
  postgres:16-alpine

docker run -d --name rabbitmq --network sc-net --hostname farm-broker \
  -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=devpassword123 \
  -e RABBITMQ_ERLANG_COOKIE=sudarshanchakra-dev-cookie \
  -p 5672:5672 -p 1883:1883 -p 15672:15672 \
  -v rabbitmq_data:/var/lib/rabbitmq \
  -v $(pwd)/cloud/rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro \
  rabbitmq:3-management

# Initialize RabbitMQ topology
pip install pika
RABBITMQ_PASS=devpassword123 python cloud/scripts/rabbitmq_init.py
```

For **VPS deployment** (full stack in Docker), use the deploy script from repo root: `./cloud/deploy.sh`. It builds backend and dashboard images and runs `cloud/docker-compose.vps.yml` (HTTP-only; dashboard on port 9080 by default). See [DEPLOY_AFTER_BUILD.md](DEPLOY_AFTER_BUILD.md).

**Dev credentials:**
- PostgreSQL: `scadmin / devpassword123` on `localhost:5432`
- RabbitMQ: `admin / devpassword123` on `localhost:5672` (management UI: `localhost:15672`)

---

## 2. Project Structure

```
SudarshanChakra/
├── AGENT_INSTRUCTIONS.md      # Implementation plan (8 phases)
├── AGENTS.md                  # Cursor Cloud agent instructions
│
├── edge/                      # TIER 2: Edge AI (Python, Docker)
│   ├── farm_edge_node.py      # Main orchestrator
│   ├── pipeline.py            # RTSP + YOLO inference
│   ├── zone_engine.py         # Point-in-polygon (Shapely)
│   ├── lora_receiver.py       # ESP32 LoRa USB-Serial
│   ├── alert_engine.py        # Decision engine (fusion + dedup)
│   ├── detection_filters.py   # 4-layer false positive reduction
│   ├── edge_gui.py            # Flask polygon editor (:5000)
│   ├── config/                # cameras.json, zones.json, authorized_tags.json
│   ├── Dockerfile
│   └── docker-compose.yml
│
├── backend/                   # TIER 3: Spring Boot Microservices
│   ├── build.gradle.kts       # Root Gradle config
│   ├── settings.gradle.kts    # Subproject declarations
│   ├── gradlew                # Gradle wrapper
│   └── alert-service/         # Only service with build config
│       ├── build.gradle.kts
│       ├── Dockerfile
│       └── src/main/resources/application.yml
│
├── dashboard/                 # TIER 4: React Dashboard
│   └── package.json           # Dependencies defined
│
├── cloud/                     # Cloud infrastructure
│   ├── docker-compose.yml     # Full VPS (GHCR images, OpenVPN, TLS)
│   ├── docker-compose.vps.yml # VPS HTTP-only (local images, use with deploy.sh)
│   ├── deploy.sh             # Build images + start stack + RabbitMQ init
│   ├── .env.example
│   ├── db/init.sql            # PostgreSQL schema (10 tables)
│   ├── rabbitmq/              # Broker config (enabled_plugins)
│   ├── nginx/                 # nginx.conf (TLS), nginx-vps.conf (HTTP-only)
│   └── scripts/               # Health monitor, RabbitMQ init
│
├── firmware/                  # ESP32 Arduino code
│   ├── worker_beacon/         # Worker + child safety tag
│   └── lora_bridge/           # LoRa-to-Serial bridge
│
├── AlertManagement/           # Raspberry Pi PA system
│   ├── scripts/               # pa_controller.py, test_pa.py
│   ├── config/                # asound.conf, pa.env
│   └── setup.sh
│
└── docs/                      # Architecture, mockups, reference code
    ├── BLUEPRINT.md            # Full system spec (~1,700 lines)
    ├── AI_DETECTION_ARCHITECTURE.md  # AI deep dive (~1,530 lines)
    ├── ARCHITECTURE.md         # System architecture document
    ├── API_REFERENCE.md        # REST + MQTT API docs
    ├── DEPLOYMENT_GUIDE.md     # Step-by-step deployment
    ├── USER_GUIDE.md           # End-user documentation
    ├── TROUBLESHOOTING.md      # Diagnosis & resolution
    ├── AUDIT_REPORT.md         # Code quality audit
    ├── dashboard-mockup.jsx    # React wireframe
    └── android-mockup.jsx      # Android wireframe
```

---

## 3. Implementation Order

Follow the 8 phases defined in `AGENT_INSTRUCTIONS.md`:

| Phase | Component | Status | Dependencies |
|-------|-----------|--------|-------------|
| 1 | Backend microservices | 🔨 Not started | PostgreSQL, RabbitMQ |
| 2 | React dashboard | 🔨 Not started | Backend API |
| 3 | Android app | 🔨 Not started | Backend API |
| 4 | CI/CD pipelines | 🔨 Not started | All services |
| 5 | Cloud deployment | ✅ Config done | Backend images |
| 6 | Edge deployment | ✅ Done | Custom YOLO model |
| 7 | ESP32 firmware | ✅ Done | Physical hardware |
| 8 | AI model training | 🔨 Not started | Dataset + GPU |

---

## 4. Build & Run Commands

### One-command full setup + build

```bash
./setup_and_build_all.sh
```

### Backend (Java)

```bash
cd backend

# Build alert-service
./gradlew :alert-service:build

# Run alert-service locally (needs PostgreSQL + RabbitMQ)
./gradlew :alert-service:bootRun

# Run tests
./gradlew :alert-service:test

# Build all services (once implemented)
./gradlew build
```

### Dashboard (React)

```bash
cd dashboard

# Install dependencies
npm install

# Development server (once source files exist)
npm run dev       # Vite dev server on :5173

# Build for production
npm run build

# Lint
npm run lint

# Test
npm run test
```

### Edge AI (Python)

```bash
cd edge

# Syntax check all files
python3 -m py_compile farm_edge_node.py pipeline.py zone_engine.py \
  alert_engine.py detection_filters.py lora_receiver.py edge_gui.py

# Lint
flake8 --max-line-length=120 *.py

# Run locally (requires GPU + cameras)
python3 farm_edge_node.py

# Run via Docker
docker compose up -d
```

---

## 5. Key Design Patterns

| Pattern | Location | Usage |
|---------|----------|-------|
| **Observer** | `pipeline.py` → callbacks | Detections published to registered callbacks |
| **State Machine** | `pa_controller.py` | IDLE → PLAYING_BGM → PLAYING_SIREN |
| **Strategy** | `detection_filters.py` | Per-class validator functions |
| **Factory** | `edge_gui.py` | `create_app()` Flask app factory |
| **Null Object** | `farm_edge_node.py` | `DummyLoRa` when hardware absent |
| **Template Method** | `pipeline.py` | `CameraGrabber.run()` frame-grab loop |

---

## 6. Testing Strategy

### Unit Tests (to implement)

```
backend/alert-service/src/test/java/
  AlertServiceTest.java         # Alert CRUD + dedup
  AlertConsumerServiceTest.java # RabbitMQ message processing
  JwtServiceTest.java           # Token generation/validation

edge/tests/
  test_zone_engine.py           # Point-in-polygon logic
  test_detection_filters.py     # Filter pipeline
  test_alert_engine.py          # Decision engine + dedup
```

### Integration Tests

```bash
# Backend: Spring Boot test with embedded PostgreSQL
./gradlew :alert-service:test

# Dashboard: Vitest
cd dashboard && npm run test

# Edge: pytest with mock RTSP streams
cd edge && python -m pytest tests/
```

### End-to-End Test Flows

See `AGENT_INSTRUCTIONS.md` Testing Checklist — 12 E2E scenarios to verify.

---

## 7. Code Style Guidelines

### Python (Edge)
- PEP 8 with max line length 120
- Type hints on function signatures
- Docstrings for all public classes and methods
- Use `logging` module (not `print()`)

### Java (Backend)
- Spring Boot conventions
- Lombok for boilerplate reduction
- OpenAPI annotations for Swagger docs
- `@Slf4j` for logging

### TypeScript (Dashboard)
- React functional components with hooks
- Tailwind CSS for styling
- React Query for server state
- Type interfaces in `types/index.ts`

### Arduino (Firmware)
- `UPPER_CASE` for constants
- Descriptive variable names
- Comment all pin assignments
- Document calibration values

---

## 8. Database Migrations

The PostgreSQL schema is managed via `cloud/db/init.sql` (DDL-first approach).

For schema changes:
1. Add migration SQL to a new file: `cloud/db/migrations/002-add-column.sql`
2. Test locally: `docker exec postgres psql -U scadmin -d sudarshanchakra -f /migration.sql`
3. Update `init.sql` for fresh installations
4. The backend uses `spring.jpa.hibernate.ddl-auto=validate` — it does NOT auto-create tables

---

## 9. Monitoring & Debugging

### Edge Node Debug

```bash
# Set verbose logging
docker exec -e LOG_LEVEL=DEBUG edge-ai python3 farm_edge_node.py

# Check MQTT messages
mosquitto_sub -h 10.8.0.1 -u admin -P password -t "farm/#" -v

# Check pipeline stats
curl http://localhost:5000/api/zones  # Edge GUI API
```

### Cloud Debug

```bash
# When using deploy.sh / docker-compose.vps.yml (from cloud/):
docker compose -f docker-compose.vps.yml logs -f
# Dashboard and API: http://localhost:9080 (or http://<vps-ip>:9080)

# Check all service logs (full compose)
docker compose logs -f

# Query database directly
docker exec postgres psql -U scadmin -d sudarshanchakra -c "SELECT * FROM alerts ORDER BY created_at DESC LIMIT 10;"

# RabbitMQ management
open http://localhost:15672  # admin/devpassword123
```

---

## 10. Contributing

### Branch Strategy

- `main` — Production-ready code
- `feature/<name>` — Feature branches
- `fix/<name>` — Bug fix branches

### Pull Request Checklist

- [ ] Code follows style guidelines
- [ ] All existing tests pass
- [ ] New tests added for new functionality
- [ ] Documentation updated (if API changes)
- [ ] No credentials committed
- [ ] `flake8` / `eslint` / `./gradlew check` passes
