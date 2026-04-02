# Story 02: mdm-service Spring Boot Skeleton

## Prerequisites
- Story 01 complete (tables exist)

## Goal
Create a new Spring Boot microservice `backend/mdm-service` following the exact same patterns as `backend/auth-service`. Register it in the API gateway and Docker Compose.

## Reference files (READ FIRST)
- `backend/auth-service/build.gradle.kts` — dependency pattern
- `backend/auth-service/src/main/java/.../AuthServiceApplication.java` — main class
- `backend/auth-service/src/main/java/.../config/JwtAuthFilter.java` — JWT filter (COPY this)
- `backend/auth-service/src/main/java/.../config/SecurityConfig.java` — security config (COPY and adapt)
- `backend/auth-service/src/main/java/.../context/TenantContext.java` — tenant context (COPY this)
- `backend/auth-service/src/main/resources/application.yml` — app config pattern
- `backend/settings.gradle.kts` — where to register the new module

## Files to CREATE

### Directory structure
```
backend/mdm-service/
├── build.gradle.kts
└── src/main/java/com/sudarshanchakra/mdm/
    ├── MdmServiceApplication.java
    ├── config/
    │   ├── JwtAuthFilter.java          ← COPY from auth-service, change package
    │   ├── SecurityConfig.java         ← COPY from auth-service, adapt paths
    │   ├── TenantContext.java          ← COPY from auth-service
    │   └── RabbitMQConfig.java         ← New: MDM exchanges/queues
    ├── model/
    │   ├── MdmDevice.java
    │   ├── AppUsage.java
    │   ├── CallLogEntry.java
    │   ├── ScreenTime.java
    │   ├── MdmCommand.java
    │   └── OtaPackage.java
    ├── repository/
    │   ├── MdmDeviceRepository.java
    │   ├── AppUsageRepository.java
    │   ├── CallLogRepository.java
    │   ├── ScreenTimeRepository.java
    │   ├── MdmCommandRepository.java
    │   └── OtaPackageRepository.java
    └── src/main/resources/
        ├── application.yml
        └── db/migration/V4__mdm_tables.sql    ← Same as Story 01
```

### `build.gradle.kts`
Copy from `backend/auth-service/build.gradle.kts`. Add:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-amqp") // For MQTT command dispatch
```
Keep all existing deps (JPA, security, JWT, PostgreSQL, validation, Flyway, test).

### `MdmServiceApplication.java`
```java
package com.sudarshanchakra.mdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MdmServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MdmServiceApplication.class, args);
    }
}
```

### `application.yml`
```yaml
spring:
  application:
    name: mdm-service
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/sudarshanchakra}
    username: ${DATABASE_USER:postgres}
    password: ${DATABASE_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASSWORD:guest}

server:
  port: ${SERVER_PORT:8085}

jwt:
  secret: ${JWT_SECRET:default-dev-secret-change-in-production-must-be-at-least-256-bits-long-for-hs256}
```

### JPA Entity: `MdmDevice.java`
```java
package com.sudarshanchakra.mdm.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mdm_devices")
public class MdmDevice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "android_id", nullable = false, unique = true)
    private String androidId;

    private String model;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "serial_number")
    private String serialNumber;

    private String imei;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_device_owner")
    private Boolean isDeviceOwner = false;

    @Column(name = "is_lock_task_active")
    private Boolean isLockTaskActive = false;

    @Column(name = "kiosk_pin_hash")
    private String kioskPinHash;

    @Column(name = "whitelisted_apps", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String whitelistedApps;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String policies;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "last_telemetry_sync")
    private Instant lastTelemetrySync;

    @Column(name = "mqtt_client_id")
    private String mqttClientId;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "provisioned_at")
    private Instant provisionedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // Generate getters and setters for ALL fields
    // Follow the pattern in backend/auth-service/.../model/Farm.java
}
```

Create the remaining 5 entities (AppUsage, CallLogEntry, ScreenTime, MdmCommand, OtaPackage) following the same pattern, mapping to the SQL columns in V4__mdm_tables.sql.

### Repositories
One interface per entity, all extending `JpaRepository<Entity, Type>`:
```java
package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.MdmDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MdmDeviceRepository extends JpaRepository<MdmDevice, UUID> {
    List<MdmDevice> findByFarmId(UUID farmId);
    MdmDevice findByAndroidId(String androidId);
}
```

Add custom query methods per entity:
- `AppUsageRepository`: `findByDeviceIdAndDateBetween(UUID deviceId, LocalDate from, LocalDate to)`
- `CallLogRepository`: `findByDeviceIdAndCallTimestampBetween(UUID deviceId, Instant from, Instant to)`
- `ScreenTimeRepository`: `findByDeviceIdAndDateBetween(UUID deviceId, LocalDate from, LocalDate to)`
- `MdmCommandRepository`: `findByDeviceIdOrderByIssuedAtDesc(UUID deviceId)`
- `OtaPackageRepository`: `findByFarmIdOrderByCreatedAtDesc(UUID farmId)`

### RabbitMQConfig.java
```java
package com.sudarshanchakra.mdm.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE_MDM_COMMANDS = "farm.mdm.commands";
    public static final String QUEUE_MDM_TELEMETRY = "mdm.telemetry";

    @Bean
    public TopicExchange mdmCommandExchange() {
        return new TopicExchange(EXCHANGE_MDM_COMMANDS);
    }

    @Bean
    public Queue mdmTelemetryQueue() {
        return QueueBuilder.durable(QUEUE_MDM_TELEMETRY).build();
    }
}
```

## Files to MODIFY

### 1. `backend/settings.gradle.kts`
Add: `include("mdm-service")`

### 2. `backend/api-gateway/src/main/resources/application.yml`
Add a new route BEFORE the existing routes:
```yaml
        - id: mdm-service
          uri: ${MDM_SERVICE_URL:http://localhost:8085}
          predicates:
            - Path=/api/v1/mdm/**
```

### 3. `backend/auth-service/src/main/java/.../support/ModuleConstants.java`
Add `"mdm"` to the ALL_MODULES list:
```java
public static final List<String> ALL_MODULES = List.of(
    "alerts", "cameras", "sirens", "water", "pumps",
    "zones", "devices", "workers", "analytics", "mdm"
);
```

## Verification
```bash
cd backend/mdm-service && ../gradlew bootRun
# Should start on port 8085
# Check: curl http://localhost:8085/actuator/health → {"status":"UP"}
```
