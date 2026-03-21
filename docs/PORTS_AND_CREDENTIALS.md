# SudarshanChakra — ports and credentials reference

Single cheat sheet for **which service listens where** and **default usernames/passwords** used in **local development**.  
**Production:** replace every placeholder and dev default; never commit real secrets.

---

## Security notice

| Risk | Mitigation |
|------|------------|
| Passwords below are **well-known dev defaults** | Change in `cloud/.env` and broker/DB before any real deployment |
| `JWT_SECRET` in dev is fixed length for local only | Use `openssl rand -hex 32` for production |
| Web UIs on `0.0.0.0` / public hosts | Put TLS + firewall + strong auth in front |

---

## Port map (quick)

| Port | Protocol / use | Component | Access notes |
|------|------------------|-----------|----------------|
| **5432** | PostgreSQL | `postgres` (Docker) | DB `sudarshanchakra` |
| **5672** | AMQP | RabbitMQ | Backend Spring apps, `rabbitmq_init.py` |
| **1883** | MQTT (TCP) | RabbitMQ MQTT plugin | Edge, Android (`tcp://host:1883`), devices |
| **15672** | HTTP | RabbitMQ Management UI | Browser admin UI |
| **15675** | WebSocket | RabbitMQ **web_mqtt** | Browser clients (e.g. farm simulator `ws://host:15675/ws`) |
| **8080** | HTTP | **api-gateway** | Main REST + routes to microservices (local `bootRun`) |
| **8081** | HTTP + **STOMP/SockJS** | **alert-service** | Alerts API; STOMP on **`/ws/alerts`** and legacy **`/ws`** (same broker; SockJS `…/info` on each). Dashboard dev proxies **`/ws`** → 8081. |
| **8082** | HTTP | **device-service** | Nodes, cameras, zones, tags, water APIs |
| **8083** | HTTP | **auth-service** | `/api/v1/auth/register`, `/api/v1/auth/login` |
| **8084** | HTTP | **siren-service** | Siren trigger/stop APIs |
| **3000** | HTTP | **dashboard** (Vite dev) | Proxies `/api` → 8080, `/ws` → 8081 |
| **3001** | HTTP | **simulator** (Vite dev) | Farm simulator UI; dev proxies **`/api` → 8080** (empty API base) for inventory + `POST /api/v1/alerts` via gateway |
| **3001** | HTTP | **simulator** (Docker) | Host port when `docker-compose.vps.yml` profile `dev` is active (`3001:80`) |
| **5000** | HTTP | **edge** (`edge_gui.py`) | Flask GUI / health |
| **9080** | HTTP | **nginx** (Docker VPS stack) | Public entry: dashboard + `/api/` → gateway (compose maps `9080:80`) |

**Android emulator:** use **`10.0.2.2`** instead of `localhost` to reach the host machine (e.g. `http://10.0.2.2:8080`).

**Apply SQL to Docker Postgres:** see **`docs/POSTGRES_DOCKER_SQL.md`** (`docker exec -i postgres psql … < cloud/db/…`).

---

## Default usernames and passwords (local dev)

Values align with **`AGENTS.md`**, **`setup_and_build_all.sh`** (first-time `cloud/.env` generation from `.env.example`), and typical `docker run` examples.

### PostgreSQL

| Field | Default (local dev) |
|-------|---------------------|
| Host:port | `localhost:5432` (compose may bind `127.0.0.1:5432`) |
| Database | `sudarshanchakra` |
| User | `scadmin` |
| Password | `devpassword123` |

Set in Docker: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`.  
Spring: `SPRING_DATASOURCE_*` in each service (compose uses `${DB_USER}`, `${DB_PASS}` from `cloud/.env`).

### RabbitMQ (AMQP, management, MQTT, Web MQTT)

| Field | Default (local dev) |
|-------|---------------------|
| AMQP | `localhost:5672` |
| Management UI | `http://localhost:15672` |
| User | `admin` |
| Password | `devpassword123` |

MQTT TCP (**1883**) and **Web MQTT** (**15675**, path often **`/ws`**) use the **same** username/password as the RabbitMQ user.

Docker: `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`.

### JWT (auth-service)

Not a “login” — this is the **signing secret** for issued tokens.

| Variable | Typical local value (after `ensure_cloud_env` / sed from example) |
|----------|---------------------------------------------------------------------|
| `JWT_SECRET` | Long hex string substituted by setup script (see `cloud/.env`); **replace in production** |

There is **no default dashboard “admin” user**: create an account via **`POST /api/v1/auth/register`** then login via **`POST /api/v1/auth/login`**.

### Optional: edge MQTT password (env template)

| Variable | Meaning |
|----------|---------|
| `MQTT_EDGE_PASS` | Password for dedicated edge MQTT user (when you configure one); template uses `CHANGE_ME_*`, setup script may set `devedgepassword123` for first `cloud/.env` |

### Farm simulator (browser)

Pre-filled in the UI for convenience (override if your `.env` differs):

| Field | Default |
|-------|---------|
| MQTT user | `admin` |
| MQTT password | `devpassword123` |
| API base | `http://localhost:8080` (gateway) |

---

## Docker Compose VPS file (`cloud/docker-compose.vps.yml`)

- **Postgres / RabbitMQ** ports are as in the port table above.
- **Backend JAR services** (gateway, alert, device, auth, siren) typically **do not publish 8080–8084 on the host**; traffic goes **nginx → api-gateway** inside the network.
- **Nginx** publishes **`9080:80`** on the host by default.
- **Simulator** (profile **`dev`**): **`3001:80`** on the host.

---

## Where values are defined

| Location | What |
|----------|------|
| [`cloud/.env.example`](cloud/.env.example) | Template: `DB_*`, `RABBITMQ_*`, `JWT_SECRET`, `MQTT_EDGE_PASS` |
| [`cloud/.env`](cloud/.env) | **Local overrides** (gitignored); created with dev substitutions by `setup_and_build_all.sh` if missing |
| [`cloud/docker-compose.vps.yml`](../cloud/docker-compose.vps.yml) | Published ports, service env references |
| [`setup_and_build_all.sh`](../setup_and_build_all.sh) | `ensure_cloud_env` sed → dev passwords; `--help` lists `SERVICE PORTS` |
| [`AGENTS.md`](../AGENTS.md) | Canonical dev commands and infra notes |

---

## Related docs

- [`QUICK_START_LOCAL.md`](QUICK_START_LOCAL.md) — step-by-step local stack
- [`DEPLOY_ALL_COMPONENTS.md`](DEPLOY_ALL_COMPONENTS.md) — broader deploy narrative
- [`simulator/README.md`](../simulator/README.md) — Web MQTT URL and auth note

---

*Last updated to match repo defaults; re-verify after changing compose or `.env.example`.*
