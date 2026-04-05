# G-04: Extend @Auditable to Critical Resource-Server Controllers

## Status
**COMPLETE** — Resource servers use a local `@Auditable` + `ResourceAuditAspect` + JPA `AuditLog` / `AuditService` writing to the shared `audit_log` table. JWTs include optional **`user_id`** (strategy A) for traceability on resource servers.

## Identity strategy (A)

- **JWT:** `JwtService.generateToken` adds claim `user_id` (user UUID). Issued on login and register.
- **jwt-support:** `JwtTokenParser` reads `user_id`; `ResourceServerJwtAuthFilter` calls `TenantContext.set(farmId, superAdmin, userId)`.
- **Aspect:** Uses `TenantContext.getFarmId()` and `TenantContext.getUserId()`. If `userId` is null (legacy tokens), `details` JSON may include `username` from `SecurityContext`.

## Super-admin / null farm

If **`farm_id` is absent** in the JWT, `ResourceAuditAspect` **skips** the insert (avoids violating `audit_log.farm_id` NOT NULL). Debug log: `Skip audit …: no farm in jwt context`.

## auth-service: no duplicate @Auditable on User / Auth

`UserService` and `AuthService` already call **`auditService.log`** for create/update/deactivate/login/register. **Do not** add `@Auditable` on `UserController` / `AuthController` for those flows without removing the imperative calls, or you would double-insert into `audit_log`.

`FarmController` continues to use auth’s `AuditAspect` + `@Auditable` as before.

## Annotated actions (resource servers)

| Service | Controller | Actions |
|---------|------------|---------|
| **device** | `ZoneController` | `zone.create`, `zone.delete` |
| **device** | `CameraController` | `camera.create` |
| **device** | `WaterMotorRestController` | `pump.command` |
| **siren** | `SirenController` | `siren.trigger`, `siren.stop` |
| **alert** | `AlertController` | `alert.acknowledge`, `alert.resolve`, `alert.false_positive` |

**Siren note:** `SirenCommandService` still persists siren-domain rows; `audit_log` adds a cross-service compliance trail — you may see **two** logical records (domain + audit) until product deduplicates.

**Failures:** Only successful controller returns are audited (`@AfterReturning`), same pattern as auth `AuditAspect`.

## Verification

With PostgreSQL, gateway, and services running, obtain a JWT from auth (so it includes `user_id`), then:

```bash
# Trigger siren (via api-gateway)
curl -X POST http://localhost:8080/api/v1/siren/trigger \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"edge-node-a"}'

# Create zone
curl -X POST http://localhost:8080/api/v1/zones \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cameraId":"cam-01","name":"Test Zone","zoneType":"intrusion","priority":"high","targetClasses":["person"],"polygon":"[[0,0],[100,0],[100,100],[0,100]]"}'
```

Check `audit_log` as **`scadmin`** (see [AGENTS.md](../../../AGENTS.md) / [PORTS_AND_CREDENTIALS.md](../../../docs/PORTS_AND_CREDENTIALS.md)):

```bash
psql -U scadmin -d sudarshanchakra -h localhost -c \
  "SELECT action, entity_type, created_at FROM audit_log ORDER BY created_at DESC LIMIT 5;"
```

If using Docker exec against the repo’s postgres container, adjust host/user to match your run (e.g. same `-U scadmin`).

**Expected:** rows such as `siren.trigger`, `zone.create` (and existing auth/farm rows).

## Implementation notes

- Per-service copies: `audit/Auditable.java`, `audit/ResourceAuditAspect.java`, `model/AuditLog.java`, `repository/AuditLogRepository.java`, `service/AuditService.java` (mirrors auth table mapping).
- `spring-boot-starter-aop` added to **device-service**, **siren-service**, **alert-service**.
