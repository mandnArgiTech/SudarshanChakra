# G-02: Per-Endpoint RBAC with @PreAuthorize

## Status
NOT DONE — zero @PreAuthorize on any controller method. FarmController has @Auditable but no access control beyond the SecurityConfig `hasAuthority` check.

## Context
24 mutating endpoints across 10 controllers need role-based access control.

Read `backend/auth-service/src/main/java/com/sudarshanchakra/auth/service/PermissionService.java` first — it defines the full permission matrix for all 5 roles.

## Files to MODIFY

### Step 1: Enable method security in EACH service

**`backend/auth-service/src/main/java/com/sudarshanchakra/auth/config/SecurityConfig.java`**
Add `@EnableMethodSecurity` above `@Configuration`:
```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@EnableMethodSecurity
@Configuration
public class SecurityConfig { ... }
```

**`backend/device-service/src/main/java/com/sudarshanchakra/device/config/SecurityConfig.java`**
Same — add `@EnableMethodSecurity`.

**`backend/alert-service/src/main/java/com/sudarshanchakra/alert/config/SecurityConfig.java`**
Same.

**`backend/siren-service/src/main/java/com/sudarshanchakra/siren/config/SecurityConfig.java`**
Same.

### Step 2: Ensure JwtAuthFilter sets authorities from role

Check each service's JwtAuthFilter. The filter must set GrantedAuthority from the JWT `role` claim:
```java
List<GrantedAuthority> authorities = List.of(
    new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())
);
// e.g., role="manager" → "ROLE_MANAGER"
```

If any service's JwtAuthFilter does NOT do this, add it. Read `backend/auth-service/src/main/java/com/sudarshanchakra/auth/config/JwtAuthFilter.java` for the reference pattern.

### Step 3: Add @PreAuthorize to ALL mutating controller methods

**`backend/siren-service/.../controller/SirenController.java`** (2 endpoints)
```java
import org.springframework.security.access.prepost.PreAuthorize;

// Trigger siren — admin, manager only
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@PostMapping("/trigger")
public ResponseEntity<?> triggerSiren(...) { ... }

// Stop siren — admin, manager only
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@PostMapping("/stop")
public ResponseEntity<?> stopSiren(...) { ... }
```

**`backend/device-service/.../controller/ZoneController.java`** (2 endpoints)
```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@PostMapping
public ResponseEntity<Zone> createZone(...) { ... }

@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteZone(...) { ... }
```

**`backend/device-service/.../controller/CameraController.java`** (1 endpoint)
```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@PostMapping
public ResponseEntity<Camera> createCamera(...) { ... }
```

**`backend/device-service/.../controller/EdgeNodeController.java`** (2 endpoints)
```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@PostMapping
public ResponseEntity<EdgeNode> createNode(...) { ... }

@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteNode(...) { ... }
```

**`backend/device-service/.../controller/WorkerTagController.java`** (1 endpoint)
```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@PostMapping
public ResponseEntity<WorkerTag> createTag(...) { ... }
```

**`backend/device-service/.../controller/water/WaterMotorRestController.java`** (2 endpoints)
```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@PostMapping("/{id}/command")
public ResponseEntity<?> sendCommand(...) { ... }

// GET endpoints — no @PreAuthorize (any authenticated user can view)
```

**`backend/alert-service/.../controller/AlertController.java`** (4 endpoints)
```java
// POST create alert — allow any authenticated (edge node posts alerts)
// No @PreAuthorize on create

@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER','OPERATOR')")
@PatchMapping("/{id}/acknowledge")
public ResponseEntity<?> acknowledgeAlert(...) { ... }

@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
@PatchMapping("/{id}/resolve")
public ResponseEntity<?> resolveAlert(...) { ... }

@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<?> deleteAlert(...) { ... }
```

**`backend/auth-service/.../controller/UserController.java`** (4 endpoints)
```java
// FarmController already has hasAuthority("ROLE_SUPER_ADMIN") in SecurityConfig
// UserController:
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@PostMapping
public ResponseEntity<?> createUser(...) { ... }

@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@PutMapping("/{id}")
public ResponseEntity<?> updateUser(...) { ... }

@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@PatchMapping("/{id}/deactivate")
public ResponseEntity<?> deactivateUser(...) { ... }
```

### Summary — do NOT add @PreAuthorize to:
- GET endpoints (any authenticated user can read)
- POST /api/v1/auth/login, /api/v1/auth/register (public)
- POST /api/v1/alerts (edge node creates alerts — authenticated but any role)

## Verification
```bash
# Login as VIEWER
TOKEN_V=$(curl -s -X POST http://localhost:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testviewer","password":"test"}' | jq -r '.token')

# Try to trigger siren — should get 403
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8084/api/v1/siren/trigger \
  -H "Authorization: Bearer $TOKEN_V" \
  -H "Content-Type: application/json" \
  -d '{}'
# Expected: 403

# Login as MANAGER
TOKEN_M=$(curl -s -X POST http://localhost:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"ramesh","password":"test"}' | jq -r '.token')

# Trigger siren — should succeed
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8084/api/v1/siren/trigger \
  -H "Authorization: Bearer $TOKEN_M" \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"edge-node-a"}'
# Expected: 200
```
