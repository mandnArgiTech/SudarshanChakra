# G-02: Per-Endpoint RBAC with @PreAuthorize

## Status

**COMPLETE** for the services listed below, with intentional differences from the original story text.

| Service | Method security | Pattern |
|---------|------------------|---------|
| **device-service** | `@EnableMethodSecurity` | `hasAuthority('PERMISSION_*')` on controllers (see `EdgeNodeController`, `CameraController`, `ZoneController`, `WorkerTagController`, water controllers) |
| **alert-service** | `@EnableMethodSecurity` | `hasAuthority('PERMISSION_*')` on `AlertController` |
| **siren-service** | `@EnableMethodSecurity` | `hasAuthority('PERMISSION_*')` on `SirenController` |
| **auth-service** | `@EnableMethodSecurity` | `hasAnyRole('SUPER_ADMIN','ADMIN',…)` on `UserController`; user paths in `SecurityConfig` are **authenticated only** so `@PreAuthorize` is the primary rule for `/api/v1/users/**` |
| **mdm-service** | `@EnableMethodSecurity` | `hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')` on mutating endpoints in `DeviceController`, `CommandController`, `OtaController`; telemetry remains chain-level **authenticated** only |

**JWT authorities on resource servers (device, alert, siren):** `ResourceServerJwtAuthFilter` (`backend/jwt-support/.../ResourceServerJwtAuthFilter.java`) adds `ROLE_<role>` and `PERMISSION_<permission>` from the token’s `permissions` claim. Prefer **`hasAuthority('PERMISSION_...')`** over duplicating the matrix with `hasAnyRole` only.

**mdm-service:** `JwtAuthFilter` normalizes the JWT `role` claim (`toUpperCase`, `-` → `_`) and sets `ROLE_*` so `@PreAuthorize("hasAnyRole('ADMIN',…)")` matches tokens such as `admin` or `super_admin`. MDM does **not** currently add `PERMISSION_*` authorities (role-based checks only there).

**Permission source of truth:** `backend/auth-service/.../service/PermissionService.java` — issued JWTs carry `permissions` used by resource servers.

---

## Divergences from the original G-02 story

1. **Alert POST (create)** — The story suggested no `@PreAuthorize` on create (edge ingestion). The implementation uses **`@PreAuthorize("hasAuthority('PERMISSION_alerts:acknowledge')")`** on create in `AlertController`. Callers need that permission in the JWT (e.g. operator-level and above per `PermissionService`). To allow broader ingest, change to `isAuthenticated()` or a dedicated permission and update this doc.

2. **Alert DELETE** — The story listed `DELETE /{id}`. **No delete endpoint** exists in alert-service today; out of scope unless added separately.

3. **Story said “zero @PreAuthorize”** — That was incorrect for device/alert/siren; those already used permission-based `@PreAuthorize` before this refresh.

---

## Verification

### Siren (permissions in JWT)

Story curls remain valid when tokens include the right **`permissions`** (e.g. `sirens:trigger` for roles that should trigger). VIEWER typically lacks it → **403**; MANAGER with `sirens:trigger` → **200**.

```bash
# Login as VIEWER
TOKEN_V=$(curl -s -X POST http://localhost:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testviewer","password":"test"}' | jq -r '.token')

curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8084/api/v1/siren/trigger \
  -H "Authorization: Bearer $TOKEN_V" \
  -H "Content-Type: application/json" \
  -d '{}'
# Expected: 403

# Login as MANAGER (or other role with sirens:trigger in JWT)
TOKEN_M=$(curl -s -X POST http://localhost:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"ramesh","password":"test"}' | jq -r '.token')

curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8084/api/v1/siren/trigger \
  -H "Authorization: Bearer $TOKEN_M" \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"edge-node-a"}'
# Expected: 200 (if user has permission)
```

### Auth user mutation (role-based)

```bash
# VIEWER must not create users
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8083/api/v1/users \
  -H "Authorization: Bearer $TOKEN_V" \
  -H "Content-Type: application/json" \
  -d '{"username":"x","password":"secret12","role":"viewer"}'
# Expected: 403
```

### Alerts create

`POST /api/v1/alerts` (via gateway `8080` or alert-service `8081`) requires JWT **`permissions`** to include **`alerts:acknowledge`** (authority `PERMISSION_alerts:acknowledge`). Without it → **403**.

---

## Key files

| Area | File |
|------|------|
| Resource-server JWT | `backend/jwt-support/.../ResourceServerJwtAuthFilter.java` |
| Auth security + users | `backend/auth-service/.../config/SecurityConfig.java`, `.../controller/UserController.java` |
| MDM security | `backend/mdm-service/.../config/SecurityConfig.java`, `.../config/JwtAuthFilter.java` |
| Device RBAC | `backend/device-service/.../config/SecurityConfig.java`, device `*Controller.java` |

---

## Tests

- **device-service:** `EdgeNodeControllerTest` includes a case where POST `/api/v1/nodes` returns **403** without `PERMISSION_devices:manage`.
- **auth-service:** `UserControllerTest` includes **403** for `POST /api/v1/users` as VIEWER and success path as ADMIN.
