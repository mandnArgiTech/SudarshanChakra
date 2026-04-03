# MDM Implementation — Master Tracker

> **Module:** `mdm` (SaaS module, opt-in per farm)
> **Total stories:** 12
> **Estimated effort:** 7 days
> **Dependency order:** Stories MUST be executed in numerical order.

## Status


| Story | Title                              | Status | Files Created | Files Modified |
| ----- | ---------------------------------- | ------ | ------------- | -------------- |
| 01    | DB migration V4                    | Done   | `cloud/db/flyway/V4__mdm_tables.sql`, `backend/mdm-service/.../V4__mdm_tables.sql` | `cloud/db/init.sql` (V3+V4) |
| 02    | mdm-service skeleton               | ☐ TODO | 18            | 3              |
| 03    | Telemetry ingestion API            | ☐ TODO | 4             | 1              |
| 04    | Command dispatch API               | ☐ TODO | 4             | 1              |
| 05    | OTA package API                    | ☐ TODO | 4             | 0              |
| 06    | Android Room DB v3 + offline cache | ☐ TODO | 8             | 2              |
| 07    | Android telemetry collector        | ☐ TODO | 2             | 1              |
| 08    | Android WorkManager upload         | ☐ TODO | 2             | 1              |
| 09    | Android Device Owner + kiosk       | ☐ TODO | 5             | 2              |
| 10    | Android MQTT commands + silent OTA | ☐ TODO | 3             | 1              |
| 11    | Dashboard MDM pages                | ☐ TODO | 6             | 3              |
| 12    | Tests + integration                | ☐ TODO | 6             | 0              |


## Execution Rules for Cursor

1. Execute stories IN ORDER (01 → 02 → ... → 12). Each depends on the prior.
2. Each story document is self-contained: exact file paths, exact code, exact imports.
3. Do NOT modify files not listed in the story's "Files to modify" section.
4. Do NOT add dependencies not listed in the story.
5. After completing each story, run the verification commands listed at the bottom of that story.
6. If a story says "copy pattern from X", read file X first and replicate the same structure.

## Key Reference Files (Read Before Starting)

These files establish the patterns every story must follow:


| File                                                                    | Why Read It                                                         |
| ----------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `backend/auth-service/build.gradle.kts`                                 | Dependency pattern for new Spring Boot service                      |
| `backend/auth-service/src/main/java/.../model/Farm.java`                | JPA entity pattern (UUID PK, farm_id, timestamps)                   |
| `backend/auth-service/src/main/java/.../controller/FarmController.java` | REST controller pattern (@GetMapping, @PostMapping, ResponseEntity) |
| `backend/auth-service/src/main/java/.../service/FarmService.java`       | Service layer pattern (repository injection, DTO mapping)           |
| `backend/auth-service/src/main/java/.../config/JwtAuthFilter.java`      | JWT filter to copy into mdm-service                                 |
| `backend/auth-service/src/main/java/.../config/SecurityConfig.java`     | Security config pattern                                             |
| `backend/auth-service/src/main/java/.../context/TenantContext.java`     | Tenant context for farm_id extraction                               |
| `backend/auth-service/src/main/java/.../support/ModuleConstants.java`   | Where to add "mdm" module                                           |
| `backend/api-gateway/src/main/resources/application.yml`                | Gateway route pattern                                               |
| `cloud/docker-compose.vps.yml`                                          | Docker Compose service pattern                                      |
| `cloud/db/init.sql`                                                     | SQL table pattern (farm_id, indexes, constraints)                   |
| `android/app/src/main/java/.../data/db/AppDatabase.kt`                  | Room DB pattern                                                     |
| `android/app/src/main/java/.../data/db/AlertEntity.kt`                  | Room Entity pattern                                                 |
| `android/app/src/main/java/.../data/db/AlertDao.kt`                     | Room DAO pattern                                                    |
| `android/app/src/main/java/.../service/MqttForegroundService.kt`        | MQTT service pattern                                                |
| `android/app/src/main/java/.../ui/navigation/NavGraph.kt`               | Android navigation pattern                                          |
| `android/app/build.gradle.kts`                                          | Android dependency pattern                                          |
| `android/app/src/main/AndroidManifest.xml`                              | Manifest pattern                                                    |
| `dashboard/src/components/Layout/Sidebar.tsx`                           | Sidebar nav + module filtering                                      |
| `dashboard/src/App.tsx`                                                 | Dashboard route registration                                        |
| `dashboard/src/pages/AdminFarmsPage.tsx`                                | Admin page pattern                                                  |
| `docs/mdm/mdm-dashboard-mockups.jsx`                                    | Visual reference for dashboard UI                                   |


## Package / Namespace Conventions


| Layer                    | Package / Path                       |
| ------------------------ | ------------------------------------ |
| Backend mdm-service      | `com.sudarshanchakra.mdm`            |
| Backend models           | `com.sudarshanchakra.mdm.model`      |
| Backend controllers      | `com.sudarshanchakra.mdm.controller` |
| Backend services         | `com.sudarshanchakra.mdm.service`    |
| Backend DTOs             | `com.sudarshanchakra.mdm.dto`        |
| Backend repos            | `com.sudarshanchakra.mdm.repository` |
| Backend config           | `com.sudarshanchakra.mdm.config`     |
| Android MDM package      | `com.sudarshanchakra.mdm`            |
| Dashboard MDM pages      | `src/pages/mdm/`                     |
| Dashboard MDM components | `src/components/mdm/`                |
| Dashboard MDM API hooks  | `src/api/mdm.ts`                     |


