# G-01: Complete Hibernate Tenant Filter

## Status
PARTIAL — 3 of 6 entities already have @FilterDef. Need 3 more + activation beans in device-service.

## Already Done
- `backend/alert-service/.../model/Alert.java` — has @FilterDef + @Filter ✓
- `backend/device-service/.../model/EdgeNode.java` — has @FilterDef + @Filter ✓
- `backend/siren-service/.../model/SirenAction.java` — has @FilterDef + @Filter ✓

## Files to MODIFY (add @FilterDef + @Filter)

### 1. `backend/auth-service/src/main/java/com/sudarshanchakra/auth/model/User.java`
Add before `@Entity`:
```java
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "farmId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "farm_id = :farmId")
```

### 2. `backend/device-service/src/main/java/com/sudarshanchakra/device/model/WorkerTag.java`
Same annotations as above.

### 3. `backend/device-service/src/main/java/com/sudarshanchakra/device/model/water/WaterTank.java`
Same annotations as above.

### 4. `backend/device-service/src/main/java/com/sudarshanchakra/device/model/water/WaterMotorController.java`
Same annotations as above.

**Note:** Camera and Zone do NOT have direct farm_id columns — they're scoped via Camera→EdgeNode→farm_id and Zone→Camera→EdgeNode→farm_id. The EdgeNode filter already handles this. Do NOT add @FilterDef to Camera or Zone.

## Files to CREATE

### 5. `backend/device-service/src/main/java/com/sudarshanchakra/device/config/TenantFilterActivator.java`
```java
package com.sudarshanchakra.device.config;

import com.sudarshanchakra.device.config.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import java.util.UUID;

@Component
@RequestScope
public class TenantFilterActivator {

    @Autowired
    private EntityManager entityManager;

    @PostConstruct
    public void activateFilter() {
        UUID farmId = TenantContext.getFarmId();
        if (farmId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("farmId", farmId);
        }
    }
}
```

### 6. Check if `TenantContext.java` exists in device-service
Read `backend/auth-service/src/main/java/com/sudarshanchakra/auth/context/TenantContext.java`.
If `backend/device-service/src/main/java/com/sudarshanchakra/device/config/TenantContext.java` does NOT exist, copy it from auth-service with package changed to `com.sudarshanchakra.device.config`.

### 7. Check if device-service JwtAuthFilter sets TenantContext
Read `backend/device-service/src/main/java/com/sudarshanchakra/device/config/JwtAuthFilter.java`.
Ensure it extracts `farm_id` from JWT claims and calls `TenantContext.setFarmId(farmId)`.
If not, copy the pattern from `backend/auth-service/src/main/java/com/sudarshanchakra/auth/config/JwtAuthFilter.java`.

### 8. Repeat steps 5-7 for siren-service
Check if siren-service has TenantContext + TenantFilterActivator. If not, create them following the same pattern.

## Verification
```bash
# Start all services
cd backend && ./gradlew :device-service:bootRun &
cd backend && ./gradlew :auth-service:bootRun &

# Login as Farm A user
TOKEN_A=$(curl -s -X POST http://localhost:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"deviprasad","password":"test"}' | jq -r '.token')

# GET nodes — should only return Farm A nodes
curl -s http://localhost:8082/api/v1/nodes -H "Authorization: Bearer $TOKEN_A" | jq length
# Expected: 2 (edge-node-a, edge-node-b)

# If you have a Farm B user, login and verify empty:
# curl -s http://localhost:8082/api/v1/nodes -H "Authorization: Bearer $TOKEN_B" | jq length
# Expected: 0
```
