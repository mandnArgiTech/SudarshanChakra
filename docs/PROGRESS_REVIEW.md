# SudarshanChakra — Progress Review

**Date:** March 22, 2026  
**Repo:** 477+ files  
**Stack:** 118 Java + 64 Kotlin + 34 Python + 59 Dashboard TS/TSX + 15 Simulator TS/TSX

---

## Phase Completion

| Phase | Status | Score |
|:------|:-------|:------|
| 1. Backend (5 Spring Boot services) | Done | 96% |
| 2. React Dashboard | Done | 95% |
| 3. Android App | Done | **93%** |
| 4. CI/CD | Done | 100% |
| 5. Cloud VPS | Done | 100% |
| 6. Edge AI | Done | 92% |
| 7. ESP32 Firmware | Done | 100% |
| 8. PA System | Partial | 80% |
| 9. Water/Motor Integration | Done | **95%** |
| 10. Farm Simulator | Done | **90%** |

---

## Android — Scorecard (aligned with ANDROID_PRODUCTION_OVERHAUL.md)

### Critical Issues (5/5 fixed)

| Issue | Status | Evidence |
|:------|:-------|:---------|
| C1: Back press kills app | Done | `BackHandler { moveTaskToBack(true) }` in AlertFeedScreen |
| C2: No logout | Done | SettingsScreen + SessionViewModel.logout() |
| C3: Notification tap goes nowhere | Done | `EXTRA_ALERT_ID` + MainActivity deep link |
| C4: No critical alert sound | Done | TYPE_ALARM channel, vibration, lockscreen |
| C5: Service dies on reboot | Done | BootReceiver + RECEIVE_BOOT_COMPLETED |

### High Issues (6/6 fixed)

| Issue | Status | Evidence |
|:------|:-------|:---------|
| H1: Emoji icons | Done | Material Icons / colored shapes: camera grid, alert detail snapshot, siren history, motor mode selector |
| H2: Profile placeholder | Done | SettingsScreen + ServerSettingsScreen |
| H3: Pull-to-refresh | Done | AlertFeedScreen, WaterTanksScreen, DeviceStatusScreen, CameraGridScreen; **plus** SirenControlScreen, MotorControlScreen |
| H4: Dark theme | Done | darkColorScheme, isSystemInDarkTheme |
| H5: Haptic feedback | Done | Siren, alerts, motor control |
| H6: Connection banner | Done | ConnectionBanner.kt, OfflineBanner.kt |

### Medium Issues (8/8 addressed in app or docs)

| Issue | Status | Evidence |
|:------|:-------|:---------|
| M1: Badge count | Done | `AlertBadgeRepository` + `NotificationCompat.setNumber` in MqttForegroundService; cleared when Alert feed loads |
| M2: Biometric / PIN | Done | Settings toggle “Require unlock after leaving app”; BiometricPrompt + device credential on resume (MainActivity) |
| M3: Offline indicator | Done | OfflineBanner |
| M4: Relative timestamps | Done | RelativeTimeFormatter.kt |
| M5: Sound preview | Done | Settings “Preview critical alert sound (2s)” |
| M6: Siren confirmation | Done | AlertDialog on siren screen |
| M7: Swipe actions on alerts | Done | SwipeToDismissBox in AlertCard.kt |
| M8: Camera auto-refresh | Done | Coil `AsyncImage` + 3s cache-bust to `{edgeGui}/api/snapshot/{id}` when Edge GUI URL set in Server settings |

### Beyond the original plan (highlights)

| Area | What |
|:-----|:-----|
| Server settings | Edge GUI base URL for camera JPEG snapshots (Flask :5000) |
| Security | Optional app lock on return from background |
| Debug networking | `src/debug` network security config for cleartext LAN snapshots |

---

## Simulator (summary)

Standalone React app: MQTT, device inventory from API, zone-based alerts, sequences, Docker/nginx. See repo `simulator/` and `seed_simulator_cameras_zones.sql`.

---

## Recent backend improvements

| Change | Impact |
|:-------|:-------|
| WebSocket `/ws` + `/ws/alerts` (SockJS) | Dashboard real-time alerts |
| `saveAndFlush` in alert-service | ID before WS broadcast |
| Water FK / consumer hardening | Fewer cascade errors |
| device-service GlobalExceptionHandler | Clearer 400 responses |

---

## Recent dashboard improvements

| Change | Impact |
|:-------|:-------|
| Device API paths `/nodes`, `/cameras`, `/zones`, `/tags` | Correct gateway routing |
| Cameras page + `VITE_EDGE_SNAPSHOT_BASE` | JPEG snapshot UX |
| MotorControlPage + **WaterPage link to `/water/motors`** | Pump control discoverable |
| `dashboard/.env.example` | Env documentation |

---

## Test inventory (approximate)

| Layer | Files | Notes |
|:------|:------|:------|
| Edge pytest | 8 | — |
| Backend unit / integration | ~30 / 8 | Includes water/motor |
| Dashboard (Vitest) | 24+ | Includes WaterPage pumps link |
| Android unit | — | Run `./gradlew testDebugUnitTest` where SDK is installed |
| E2E | 1 | — |

---

## Operational follow-ups (not “code gaps”)

Tracked as **runbooks / field work** — see linked docs:

| Topic | Document |
|:------|:---------|
| Train and ship YOLO weights | [MODEL_TRAINING_RUNBOOK.md](MODEL_TRAINING_RUNBOOK.md) |
| HTTPS reverse proxy, MQTTS, VPN choice | [PRODUCTION_TLS_AND_VPN.md](PRODUCTION_TLS_AND_VPN.md) |
| PA system on-site validation | [PA_SYSTEM_FIELD_TEST_CHECKLIST.md](PA_SYSTEM_FIELD_TEST_CHECKLIST.md) |

**Android CI note:** `assembleDebug` / `testDebugUnitTest` require `ANDROID_HOME` or `local.properties` `sdk.dir` (see [AGENTS.md](../AGENTS.md)).

---

## Overall (~92% excluding custom model artifacts)

```
███████████████████████░░  92%

Done:     Backend, Dashboard, Android (incl. refresh, snapshots, badge, lock, sound preview),
          CI/CD, Cloud VPS, Edge AI, Firmware, Water/Motor, Simulator
Follow-up: Custom trained weights (external artifact), production TLS/VPN rollout, PA field sign-off
```
