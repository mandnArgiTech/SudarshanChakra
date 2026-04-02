# MDM Agent Instructions for Cursor

## CRITICAL: Read Before Starting

This document tells you how to implement the MDM module. Execute stories in strict order.

## Execution Order (MANDATORY)

```
Story 01 → Story 02 → Story 03 → Story 04-05 → Story 06-08 → Story 09-10 → Story 11 → Story 12
```

Each story file is at `docs/mdm/stories/STORY_XX_*.md`. Read the ENTIRE story before writing ANY code.

## Rules

1. **Read the story file completely first.** Each story lists exact file paths, exact code, exact imports.
2. **Read the reference files listed in each story.** They show the existing patterns to follow.
3. **Do NOT modify files not listed in "Files to MODIFY".** The MDM module is additive.
4. **Do NOT add dependencies not listed in the story.** Extra deps cause build conflicts.
5. **After each story, run the verification commands** at the bottom.
6. **If a compile error occurs**, check that you used the exact package names: `com.sudarshanchakra.mdm` for backend, `com.sudarshanchakra.mdm` for Android.
7. **Match existing code style exactly.** Look at the reference file and copy its formatting.

## Package Conventions

| Layer | Base Package |
|:------|:------------|
| Backend mdm-service Java | `com.sudarshanchakra.mdm` |
| Android MDM Kotlin | `com.sudarshanchakra.mdm` |
| Android MDM Room entities | `com.sudarshanchakra.mdm.data` |
| Dashboard MDM pages | `src/pages/mdm/` |
| Dashboard MDM components | `src/components/mdm/` |
| Dashboard MDM API | `src/api/mdm.ts` |

## Backend mdm-service

- Spring Boot 3.x (same as auth-service)
- Port: 8085
- Uses same PostgreSQL database (shared schema, own tables prefixed `mdm_`)
- Uses same RabbitMQ (new exchange: `farm.mdm.commands`)
- Uses same JWT secret (tokens from auth-service work here)
- Copy `JwtAuthFilter.java`, `SecurityConfig.java`, `TenantContext.java` from auth-service, change package to `com.sudarshanchakra.mdm.config`

## Android

- Room DB version bump: 2 → 3 (add migration)
- New entities go in `com.sudarshanchakra.mdm.data` package
- New classes go in `com.sudarshanchakra.mdm` package
- DeviceAdminReceiver component name: `com.sudarshanchakra/.mdm.SudarshanDeviceAdminReceiver`
- WorkManager worker: `TelemetryUploadWorker` with HiltWorker
- All MDM features are no-ops when NOT Device Owner — check `kioskManager.isDeviceOwner` before enforcing policies

## Dashboard

- New pages at `src/pages/mdm/`
- New components at `src/components/mdm/`
- Use existing `sc-*` Tailwind color classes
- Use existing `ModuleRoute` component with `module="mdm"`
- Add `Smartphone` from lucide-react for nav icon
- Visual reference: `docs/mdm/mdm-dashboard-mockups.jsx`

## Story File Locations

```
docs/mdm/stories/STORY_01_DB_MIGRATION.md
docs/mdm/stories/STORY_02_SERVICE_SKELETON.md
docs/mdm/stories/STORY_03_TELEMETRY_API.md
docs/mdm/stories/STORY_04_05_COMMANDS_OTA.md
docs/mdm/stories/STORY_06_07_08_ANDROID_TELEMETRY.md
docs/mdm/stories/STORY_09_10_ANDROID_KIOSK_OTA.md
docs/mdm/stories/STORY_11_DASHBOARD_PAGES.md
docs/mdm/stories/STORY_12_TESTS_DOCKER.md
```

## Quick Reference: Files This Module Creates

### Backend (new service)
```
backend/mdm-service/
├── build.gradle.kts
├── src/main/java/com/sudarshanchakra/mdm/
│   ├── MdmServiceApplication.java
│   ├── config/{JwtAuthFilter,SecurityConfig,TenantContext,RabbitMQConfig}.java
│   ├── controller/{DeviceController,TelemetryController,CommandController,OtaController}.java
│   ├── service/{DeviceManagementService,TelemetryIngestionService,CommandDispatchService,OtaService}.java
│   ├── model/{MdmDevice,AppUsage,CallLogEntry,ScreenTime,MdmCommand,OtaPackage}.java
│   ├── dto/{TelemetryBatchRequest,CommandRequest,OtaPackageRequest}.java
│   └── repository/{MdmDevice,AppUsage,CallLog,ScreenTime,MdmCommand,OtaPackage}Repository.java
└── src/main/resources/
    ├── application.yml
    └── db/migration/V4__mdm_tables.sql
```

### Android (new package)
```
android/app/src/main/java/com/sudarshanchakra/mdm/
├── SudarshanDeviceAdminReceiver.kt
├── KioskManager.kt
├── KioskLauncherActivity.kt
├── DevEscapeDialog.kt
├── TelemetryCollector.kt
├── TelemetryUploadWorker.kt
├── MdmWorkScheduler.kt
├── MdmCommandHandler.kt
├── SilentInstaller.kt
├── InstallResultReceiver.kt
└── data/
    ├── MdmAppUsageEntity.kt
    ├── MdmAppUsageDao.kt
    ├── MdmCallLogEntity.kt
    ├── MdmCallLogDao.kt
    ├── MdmScreenTimeEntity.kt
    └── MdmScreenTimeDao.kt
```

### Dashboard (new pages)
```
dashboard/src/
├── api/mdm.ts
├── pages/mdm/
│   ├── MdmDeviceListPage.tsx
│   └── MdmDeviceDetailPage.tsx
└── components/mdm/
    ├── ScreenTimeChart.tsx
    ├── CallLogTable.tsx
    └── CommandPanel.tsx
```

### Files MODIFIED (minimal, existing files)
```
backend/settings.gradle.kts                    ← add include("mdm-service")
backend/auth-service/.../ModuleConstants.java  ← add "mdm" to list
backend/api-gateway/.../application.yml        ← add mdm-service route
cloud/db/init.sql                              ← append V4 tables
cloud/db/flyway/V4__mdm_tables.sql            ← new migration file
cloud/docker-compose.vps.yml                   ← add mdm-service container
android/app/src/main/AndroidManifest.xml       ← add receiver + activity + permissions
android/app/.../data/db/AppDatabase.kt         ← bump version, add entities + migration
android/app/.../di/AppModule.kt                ← provide new DAOs
android/app/build.gradle.kts                   ← add work-runtime-ktx, hilt-work
android/app/.../SudarshanChakraApp.kt          ← schedule MdmWorkScheduler
android/app/.../settings/SettingsScreen.kt     ← add escape hatch tap counter
android/app/.../service/MqttForegroundService.kt ← subscribe MDM command topic
dashboard/src/components/Layout/Sidebar.tsx    ← add MDM nav item
dashboard/src/App.tsx                          ← add MDM routes
```

## Android Mockup Reference

**Visual reference for ALL Android MDM screens:** `docs/mdm/mdm-android-mockups.jsx`

5 screens using the existing Terracotta/Cream Material 3 palette:

| Screen | What to Implement | Compose Component |
|:-------|:------------------|:------------------|
| Kiosk Home | Main dashboard + whitelisted app grid at bottom (WhatsApp, YouTube, Maps, Phone as 4 rounded icon squares) | `KioskLauncherActivity.kt` — wraps existing `NavGraph()` with bottom app grid |
| Locked State | Full-screen "Kiosk Mode Active" with SC branding. Shows lock status (status bar disabled, home locked, recents blocked) | Part of `KioskManager.kt` lock state |
| Settings + MDM | Existing SettingsScreen PLUS: MDM status card (6 policy toggles), telemetry sync status (last sync, pending items, next sync), allowed apps list, server connection status. Version text is the 7-tap escape trigger → shows PIN dialog with "Exit Kiosk" and "Decommission" buttons | `SettingsScreen.kt` modifications + `DevEscapeDialog.kt` |
| Usage Detail | Per-app screen time bars (SC green, WhatsApp green, YouTube red, Maps blue, Phone orange), recent calls table, sync status indicator | Optional screen accessible from Settings |
| OTA Update | Bottom sheet dialog showing version, release notes, download progress bar with percentage, "Installing silently" message | `SilentInstaller.kt` UI feedback (can be a notification or overlay) |
