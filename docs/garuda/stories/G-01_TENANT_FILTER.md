# G-01: Complete Hibernate Tenant Filter

## Status
**COMPLETE** for auth-service, device-service, alert-service, and siren-service.

Resource servers (**device**, **alert**, **siren**) use **`jwt-support`**: `JwtResourceServerConfiguration` imports `TenantHibernateWebConfiguration`, which registers `TenantHibernateFilterActivationFilter`. Farm id comes from **`com.sudarshanchakra.jwt.TenantContext`** (`ResourceServerJwtAuthFilter`). **Do not** add a per-service `TenantFilterActivator` or duplicate `TenantContext` in device-service — that would conflict with this design.

**auth-service** uses **`com.sudarshanchakra.auth.context.TenantContext`** and **`AuthTenantHibernateFilterActivationFilter`** (servlet filter, `Ordered.LOWEST_PRECEDENCE`) so Hibernate `tenantFilter` applies to **`User`** queries for non–super-admin callers. **`ROLE_SUPER_ADMIN`** bypasses the filter (cross-farm user admin). **`spring.jpa.open-in-view: true`** is required on auth (same session-spanning rationale as jwt-support).

**Out of scope here:** **mdm-service** uses its own JWT stack and does not import `JwtResourceServerConfiguration`; add a separate story if MDM entities need the same Hibernate filter pattern.

## Already implemented (entities)

| Area | Notes |
|------|--------|
| `alert-service` / `Alert.java` | `@FilterDef` + `@Filter` on `farm_id` |
| `device-service` / `EdgeNode.java` | `@FilterDef` + `@Filter` (defines `tenantFilter` for the persistence unit) |
| `device-service` / `WorkerTag`, `WaterTank`, `WaterMotorController` | `@Filter` only (`farm_id = :farmId`) — **no duplicate `@FilterDef`** needed |
| `device-service` / `Camera`, `Zone` | `@Filter` with **subquery** via `edge_nodes.farm_id` (not direct `farm_id` on entity) |
| `siren-service` / `SirenAction.java` | `@FilterDef` + `@Filter` |
| `auth-service` / `User.java` | `@FilterDef` + `@Filter` on `farm_id` |

## Auth-service files (this story)

- `backend/auth-service/src/main/java/com/sudarshanchakra/auth/model/User.java` — Hibernate tenant annotations
- `backend/auth-service/src/main/java/com/sudarshanchakra/auth/config/AuthTenantHibernateFilterActivationFilter.java` — enable/disable `tenantFilter` per request
- `backend/auth-service/src/main/java/com/sudarshanchakra/auth/config/AuthTenantHibernateWebConfiguration.java` — `FilterRegistrationBean` registration
- `backend/auth-service/src/main/resources/application.yml` — `spring.jpa.open-in-view: true`, `tenant.filter.enabled` (optional disable for ops)

## Integration test (device-service)

`DeviceServiceIntegrationTest#listNodes_jwtFarmScopesResults` asserts JWT farm `b0000000-0000-0000-0000-000000000001` sees **no** nodes while farm `a0000000-0000-0000-0000-000000000001` sees seed nodes. Requires **Docker** for Testcontainers:

```bash
cd backend && ./gradlew :device-service:integrationTest
```

## Manual verification

```bash
# Start Postgres + services (or use full stack)
cd backend && ./gradlew :auth-service:bootRun &
cd backend && ./gradlew :device-service:bootRun &

# Login as Farm A user
TOKEN_A=$(curl -s -X POST http://localhost:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"deviprasad","password":"test"}' | jq -r '.token')

# GET nodes — should only return Farm A nodes
curl -s http://localhost:8082/api/v1/nodes -H "Authorization: Bearer $TOKEN_A" | jq length
# Expected: 2 (edge-node-a, edge-node-b) with default seed data

# If you have a Farm B user, login and verify empty:
# curl -s http://localhost:8082/api/v1/nodes -H "Authorization: Bearer $TOKEN_B" | jq length
# Expected: 0
```

---

## Historical note (superseded instructions)

Earlier drafts of this story proposed `TenantFilterActivator` + copying `TenantContext` into **device-service** and **siren-service**. The monorepo **does not** follow that path; use **`jwt-support`** as described above.
