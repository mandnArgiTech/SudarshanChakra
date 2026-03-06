# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

SudarshanChakra is an enterprise smart farm hazard detection & security system (IoT + Edge AI + Cloud). It is a monorepo with these components:

| Component | Stack | Status |
|-----------|-------|--------|
| `backend/` | Java 21, Spring Boot 3.2, Gradle (Kotlin DSL) | Scaffolded (only `alert-service` has `build.gradle.kts`; other services need implementation) |
| `dashboard/` | React 18, Vite, TypeScript, Tailwind CSS | Scaffolded (`package.json` only; source files need implementation) |
| `edge/` | Python 3.12, YOLO, Flask, OpenCV | Fully implemented (7 `.py` files) |
| `cloud/` | Docker Compose, PostgreSQL 16, RabbitMQ 3 | Fully implemented (infrastructure configs) |
| `firmware/` | C++ / Arduino (ESP32) | Fully implemented |
| `AlertManagement/` | Python (Raspberry Pi PA system) | Fully implemented |

See `AGENT_INSTRUCTIONS.md` for the full implementation plan and phased build order.

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

### Backend (Java/Spring Boot)

- Java 21 is pre-installed. Gradle wrapper is in `backend/` (`./gradlew`).
- `settings.gradle.kts` references 5 subprojects but only `alert-service` has files. Building the root project will fail; build individual subprojects: `./gradlew :alert-service:build`
- No Java source files exist yet — the services need to be implemented per `AGENT_INSTRUCTIONS.md` Phase 1.

### Dashboard (React)

- Node.js 22+ and npm are pre-installed. Run `npm install` in `dashboard/`.
- Only `package.json` exists — no `vite.config.ts`, `tsconfig.json`, or source files. These need to be created per `AGENT_INSTRUCTIONS.md` Phase 2.
- Lint: `npm run lint` (ESLint 8)
- Dev server: `npm run dev` (once source files are created)

### Edge AI (Python)

- Python 3.12 is pre-installed. Install deps: `pip install -r edge/requirements.txt`
- Syntax-check all files: `python3 -m py_compile edge/*.py`
- Lint: `python3 -m flake8 --max-line-length=120 edge/*.py`
- The edge services require NVIDIA GPU + RTSP cameras to run the inference pipeline. The Flask GUI (`edge_gui.py`) also depends on OpenCV for generating placeholder images.

### Dev credentials (local only)

See `cloud/.env` (generated from `.env.example`):
- `DB_PASS=devpassword123`
- `RABBITMQ_PASS=devpassword123`
- `JWT_SECRET=devsecret1234567890abcdef...`
