# Story 12: Tests + Docker Integration

## Prerequisites
- Stories 01-11 complete

## Goal
Add unit tests for mdm-service, dashboard component tests, and Docker Compose integration for the new service.

## Files to CREATE

### Backend Tests

#### 1. `backend/mdm-service/src/test/java/com/sudarshanchakra/mdm/service/TelemetryIngestionServiceTest.java`
Test: processBatch with valid data, unknown device, empty batch, duplicate usage (upsert), heartbeat update.
Follow pattern of `backend/auth-service/src/test/java/.../service/AuthServiceTest.java`.
Use `@MockBean` for repositories. Minimum 5 `@Test` methods.

#### 2. `backend/mdm-service/src/test/java/com/sudarshanchakra/mdm/service/CommandDispatchServiceTest.java`
Test: dispatch valid command, invalid command name, device not found, MQTT publish success, MQTT publish failure.
Minimum 5 `@Test` methods.

#### 3. `backend/mdm-service/src/test/java/com/sudarshanchakra/mdm/controller/DeviceControllerTest.java`
Test: list devices, get device, register device, decommission.
Use `@WebMvcTest` pattern from `backend/auth-service/src/test/.../controller/AuthControllerTest.java`.
Minimum 4 `@Test` methods.

#### 4. `backend/mdm-service/src/test/java/com/sudarshanchakra/mdm/controller/TelemetryControllerTest.java`
Test: batch upload endpoint, heartbeat endpoint, invalid request body.
Minimum 3 `@Test` methods.

#### 5. `backend/mdm-service/src/test/resources/application-test.yml`
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  flyway:
    enabled: false
  rabbitmq:
    host: localhost
    port: 5672

jwt:
  secret: test-secret-must-be-at-least-256-bits-long-for-hs256-algorithm-testing-only
```

### Dashboard Tests

#### 6. `dashboard/src/pages/mdm/MdmDeviceListPage.test.tsx`
Test: renders device list, shows status badges, shows screen time summary.
Follow pattern of `dashboard/src/pages/WaterPage.test.tsx`.
Use Vitest + React Testing Library. Minimum 3 tests.

### Docker Compose

#### 7. Add to `cloud/docker-compose.vps.yml`
```yaml
  mdm-service:
    image: sudarshanchakra/mdm-service:latest
    container_name: mdm-service
    build:
      context: ../backend/mdm-service
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/sudarshanchakra
      DATABASE_USER: postgres
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
      RABBITMQ_HOST: rabbitmq
      JWT_SECRET: ${JWT_SECRET}
      SERVER_PORT: 8085
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    restart: unless-stopped
```

Update API gateway environment in the same compose file:
```yaml
    environment:
      # Add:
      MDM_SERVICE_URL: http://mdm-service:8085
```

## Verification
```bash
# Backend tests
cd backend/mdm-service && ../gradlew test
# Should show 17+ @Test methods passing

# Dashboard tests
cd dashboard && npm run test -- --run src/pages/mdm/
# Should show 3+ tests passing

# Docker integration
cd cloud && docker compose -f docker-compose.vps.yml up -d mdm-service
docker logs mdm-service | grep "Started MdmServiceApplication"
curl http://localhost:8085/actuator/health
# Should return {"status":"UP"}
```
