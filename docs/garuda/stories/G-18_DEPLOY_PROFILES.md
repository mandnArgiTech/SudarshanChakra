# G-18: Deployment Script + Compose Profiles — DONE

## What shipped

### Compose profiles ([`cloud/docker-compose.vps.yml`](../../../cloud/docker-compose.vps.yml))

Services declare `profiles:` so you must pass **`--profile`** (see file header). Defaults:

| Profile | Stack |
|---------|--------|
| **full** | All services including **siren-service**, **mdm-service** |
| **security** | No **mdm-service** |
| **monitoring** | No **siren-service**, no **mdm-service** |
| **water_only** | Same containers as **monitoring**; farm **modulesEnabled** differs (script/API) |
| **dev** | Optional; adds **simulator** (use with **full**, e.g. `SC_COMPOSE_EXTRA_PROFILES=dev` or `setup_and_build_all.sh deploy-docker`) |

[`cloud/deploy.sh`](../../../cloud/deploy.sh) defaults to **`--profile full`**. **`api-gateway`** no longer `depends_on` **siren** / **mdm** so omitted services do not break Compose.

### Reference YAMLs (docs only)

[`cloud/docker-compose.profile-security.yml`](../../../cloud/docker-compose.profile-security.yml), [`docker-compose.profile-monitoring.yml`](../../../cloud/docker-compose.profile-monitoring.yml), [`docker-compose.profile-water-only.yml`](../../../cloud/docker-compose.profile-water-only.yml) — comments + `services: {}`; behavior lives in **`docker-compose.vps.yml`**.

### Farm provisioning ([`scripts/deploy_saas_farm.sh`](../../../scripts/deploy_saas_farm.sh))

- **`--plan`** `full` | `security` | `monitoring` | `water_only` → **modulesEnabled** + matching **`docker compose --profile`**
- **`--farm-name`** / **`--name`**, **`--slug`**, **`--admin-user`**, **`--admin-email`**
- **`--admin-password`** or **`INITIAL_ADMIN_PASSWORD`**; if unset, **openssl** generates one and prints it
- **`SUPER_ADMIN_TOKEN`** required; **`API_BASE`** default **`http://localhost:9080/api/v1`** (nginx)
- Single **`POST /api/v1/farms`** with **`initialAdminUsername`**, **`initialAdminPassword`**, **`initialAdminEmail`** ([`FarmRequest`](../../../backend/auth-service/src/main/java/com/sudarshanchakra/auth/dto/FarmRequest.java)) — no separate **`/auth/register`** needed
- **`--no-compose`**: API only, no **`docker compose up`**

## Verification

```bash
export SUPER_ADMIN_TOKEN="<super_admin_jwt>"
./scripts/deploy_saas_farm.sh \
  --plan water_only \
  --farm-name "Test Farm" \
  --slug test-farm \
  --admin-user testadmin \
  --admin-email test@example.com \
  --no-compose   # optional: skip compose if stack already running
```

Stack deploy alone:

```bash
./cloud/deploy.sh --profile security
```

## Notes

- **Garuda master plan** acceptance: use **`./scripts/deploy_saas_farm.sh`** for farm + modules + optional compose, and **`./cloud/deploy.sh --profile full`** for image build + full stack (no farm API in `cloud/deploy.sh`).

---
