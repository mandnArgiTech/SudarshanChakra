# G-04: Extend @Auditable to All Critical Controller Methods

## Status
PARTIAL — AuditAspect.java exists and works. @Auditable is only on FarmController (4 methods). Need to add it to siren, zone, water, user, and alert controllers.

## Already Done
- `AuditAspect.java` — full @Aspect with @AfterReturning ✓
- `Auditable.java` — annotation with action, entityType, entityId ✓
- `FarmController` — @Auditable on create/update/suspend/activate ✓

## Files to MODIFY

The `@Auditable` annotation lives in `backend/auth-service`. Other services (device-service, siren-service, alert-service) cannot import it directly because they're separate Gradle modules.

**Option A (recommended):** Copy `Auditable.java` + `AuditAspect.java` + `AuditService.java` into each service that needs it. Each service writes to the same `audit_log` table via its own AuditService.

**Option B:** Extract a shared `backend/common` module. More complex, skip for now.

### For device-service:

**Create** `backend/device-service/src/main/java/com/sudarshanchakra/device/audit/Auditable.java`
Copy from `backend/auth-service/src/main/java/com/sudarshanchakra/auth/audit/Auditable.java`, change package to `com.sudarshanchakra.device.audit`.

**Create** `backend/device-service/src/main/java/com/sudarshanchakra/device/audit/AuditAspect.java`
Copy from auth-service, change package, adapt imports (TenantContext, AuditService paths).

**Create** `backend/device-service/src/main/java/com/sudarshanchakra/device/audit/AuditService.java`
Simplified version — writes directly to audit_log table via JdbcTemplate:
```java
package com.sudarshanchakra.device.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void log(UUID farmId, UUID userId, String action, String entityType,
                    String entityId, String details, String ip) {
        jdbc.update("INSERT INTO audit_log (farm_id, user_id, action, entity_type, entity_id, details, ip_address) VALUES (?,?,?,?,?,?::jsonb,?)",
            farmId, userId, action, entityType, entityId, details, ip);
    }
}
```

**Ensure** device-service `build.gradle.kts` has `spring-boot-starter-aop` (check — auth-service has it).

**Then add @Auditable to device-service controllers:**

`ZoneController.java`:
```java
@Auditable(action = "zone.create", entityType = "zone")
@PostMapping public ResponseEntity<Zone> createZone(...) { ... }

@Auditable(action = "zone.delete", entityType = "zone", entityId = "#id")
@DeleteMapping("/{id}") public ResponseEntity<Void> deleteZone(...) { ... }
```

`CameraController.java`:
```java
@Auditable(action = "camera.create", entityType = "camera")
@PostMapping public ResponseEntity<Camera> createCamera(...) { ... }
```

`WaterMotorRestController.java`:
```java
@Auditable(action = "pump.command", entityType = "motor", entityId = "#id")
@PostMapping("/{id}/command") public ResponseEntity<?> sendCommand(...) { ... }
```

### For siren-service:
Same pattern — copy Auditable + AuditAspect + AuditService into siren-service.

`SirenController.java`:
```java
@Auditable(action = "siren.trigger", entityType = "siren")
@PostMapping("/trigger") public ResponseEntity<?> triggerSiren(...) { ... }

@Auditable(action = "siren.stop", entityType = "siren")
@PostMapping("/stop") public ResponseEntity<?> stopSiren(...) { ... }
```

### For alert-service:
`AlertController.java`:
```java
@Auditable(action = "alert.acknowledge", entityType = "alert", entityId = "#id.toString()")
@PatchMapping("/{id}/acknowledge") public ResponseEntity<?> acknowledgeAlert(...) { ... }

@Auditable(action = "alert.resolve", entityType = "alert", entityId = "#id.toString()")
@PatchMapping("/{id}/resolve") public ResponseEntity<?> resolveAlert(...) { ... }
```

### For auth-service (extend existing):

`UserController.java`:
```java
@Auditable(action = "user.create", entityType = "user")
@PostMapping public ResponseEntity<?> createUser(...) { ... }

@Auditable(action = "user.deactivate", entityType = "user", entityId = "#id.toString()")
@PatchMapping("/{id}/deactivate") public ResponseEntity<?> deactivateUser(...) { ... }
```

`AuthController.java`:
```java
@Auditable(action = "user.login", entityType = "user")
@PostMapping("/login") public ResponseEntity<?> login(...) { ... }
```

## Verification
```bash
# Trigger siren
curl -X POST http://localhost:8080/api/v1/siren/trigger \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"edge-node-a"}'

# Check audit_log
docker exec -i postgres psql -U postgres -d sudarshanchakra \
  -c "SELECT action, entity_type, created_at FROM audit_log ORDER BY created_at DESC LIMIT 5;"
# Expected: row with action='siren.trigger'

# Create zone
curl -X POST http://localhost:8080/api/v1/zones \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cameraId":"cam-01","name":"Test Zone","zoneType":"intrusion","priority":"high","targetClasses":["person"],"polygon":"[[0,0],[100,0],[100,100],[0,100]]"}'

# Check audit_log again
# Expected: row with action='zone.create'
```
