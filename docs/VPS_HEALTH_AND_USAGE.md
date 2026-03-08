# SudarshanChakra on VPS — Health Check & How to Use

**Your main site** [https://vivasvan-tech.in](https://vivasvan-tech.in) runs another application (e.g. Portainer). **SudarshanChakra** runs on a **separate port** so both can coexist.

---

## 1. SudarshanChakra URLs (this VPS)

| What | URL |
|------|-----|
| **Dashboard (web UI)** | **http://vivasvan-tech.in:9080** or http://localhost:9080 |
| **API base** | http://vivasvan-tech.in:9080/api/v1/ |
| **Health check** | http://vivasvan-tech.in:9080/health |
| **RabbitMQ Management** | http://vivasvan-tech.in:15672 (user/pass from `cloud/.env`) |

Replace `vivasvan-tech.in` with your VPS IP if you use IP instead of domain.

---

## 2. Dashboard username and password

**There is no default username or password.** You must create the first user via the API, then use those credentials to log in.

### Create the first user (run once)

From your machine (replace `vivasvan-tech.in:9080` with your server if different):

```bash
curl -X POST http://vivasvan-tech.in:9080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YourSecurePass123","email":"admin@example.com","role":"admin"}'
```

Or from the VPS:

```bash
curl -X POST http://localhost:9080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YourSecurePass123","email":"admin@example.com","role":"admin"}'
```

- **Username:** `admin` (or whatever you put in `"username"`)
- **Password:** `YourSecurePass123` (or whatever you put in `"password"` — use a strong password in production)
- **Roles:** `admin` | `manager` | `viewer`

Then open **http://vivasvan-tech.in:9080**, enter that username and password, and click **Sign In**. Additional users can be created the same way (or by an admin via the API later).

---

## 3. How to Check If All Services Are Healthy

Run from the **repo root** or from **`cloud/`**:

### Quick one-liner

```bash
cd /path/to/SudarshanChakra/cloud
docker compose -f docker-compose.vps.yml ps -a
```

All services should show **Status** `Up`; `postgres` and `rabbitmq` should show **(healthy)**.

### HTTP checks

```bash
# Dashboard (should return 200)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9080/

# API health (should return {"status":"UP"})
curl -s http://localhost:9080/health
```

### Per-service logs (if something is failing)

```bash
cd cloud
docker compose -f docker-compose.vps.yml logs postgres    # database
docker compose -f docker-compose.vps.yml logs rabbitmq   # message broker
docker compose -f docker-compose.vps.yml logs api-gateway
docker compose -f docker-compose.vps.yml logs alert-service
docker compose -f docker-compose.vps.yml logs nginx      # reverse proxy
# Or follow all logs:
docker compose -f docker-compose.vps.yml logs -f
```

### Restart the stack

```bash
cd /path/to/SudarshanChakra/cloud
docker compose -f docker-compose.vps.yml restart
# Or full down/up:
docker compose -f docker-compose.vps.yml down
docker compose -f docker-compose.vps.yml up -d
```

---

## 4. How to Use the Dashboard

1. **Create a user** (once): see [§2 Dashboard username and password](#2-dashboard-username-and-password) above.
2. Open **http://vivasvan-tech.in:9080** and **log in** with that username and password.
3. Use the dashboard for **alerts**, **cameras**, **zones**, **siren control**, etc. See [USER_GUIDE.md](USER_GUIDE.md) for details.

---

## 5. Change the Port (if 9080 is in use)

Edit **`cloud/docker-compose.vps.yml`**, find the `nginx` service, and change the port mapping:

```yaml
ports:
  - "9090:80"   # use 9090 (or any free port) instead of 9080
```

Then recreate nginx:

```bash
cd cloud
docker compose -f docker-compose.vps.yml up -d nginx --force-recreate
```

Use **http://vivasvan-tech.in:9090** (or your chosen port) for the dashboard.

---

## 6. Summary

- **vivasvan-tech.in** (port 80/443) = your other app (Portainer, etc.).
- **vivasvan-tech.in:9080** = SudarshanChakra dashboard and API.
- Check health: `docker compose -f docker-compose.vps.yml ps` and `curl http://localhost:9080/health`.

For deployment steps, see [DEPLOY_AFTER_BUILD.md](DEPLOY_AFTER_BUILD.md). For end-user features, see [USER_GUIDE.md](USER_GUIDE.md).
