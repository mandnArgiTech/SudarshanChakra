# Release GARUDA — Master Plan

> **Codename:** Garuda (गरुड़) — The divine eagle mount of Vishnu, carrier of the Sudarshan Chakra
> **Repo:** 582 files, 96 commits, 149 Java + 71 Kotlin + 47 Python + 72 TSX + 15 Simulator TS
> **Tests:** 348 functions (100 edge + 143 backend + 92 dashboard + 13 E2E)
> **Date:** March 2026

---

## Release Scope

Garuda is the first production release. It closes every known gap, adds MDM, and makes the system deployable to real farms and commercial customers.

### 7 Workstreams

| # | Workstream | Stories | Status | Blocks |
|:--|:-----------|:--------|:-------|:-------|
| WS-1 | SaaS gap closure | G-01 to G-05 | ☐ TODO | Must complete before WS-6 |
| WS-2 | Camera/Video gap closure | G-06 to G-09 | ☐ TODO | Independent |
| WS-3 | Android production polish | G-10 to G-12 | ☐ TODO | Independent |
| WS-4 | MDM Kiosk module | M-01 to M-12 | ☐ TODO | Needs WS-1 (module system) |
| WS-5 | E2E testing | G-13 to G-14 | ☐ TODO | Needs WS-1,2,3 |
| WS-6 | Infrastructure (VPN/TLS) | G-15 to G-16 | ☐ TODO | Independent |
| WS-7 | Build, package, release | G-17 to G-18 | ☐ TODO | Needs all above |

### Story Dependency Graph

```
WS-1 (SaaS gaps)──────────┐
WS-2 (Camera gaps)─────────┼──→ WS-5 (E2E) ──→ WS-7 (Release)
WS-3 (Android polish)──────┤
WS-6 (VPN/TLS)─────────────┘
WS-1 ──→ WS-4 (MDM) ──→ WS-5
```

---

## Current State — What EXISTS (Completed Before Garuda)

| Component | Files | Lines | Tests | Completion |
|:----------|:------|:------|:------|:-----------|
| Backend (5 services) | 149 Java | ~12K | 143 @Test | 96% |
| Dashboard (React) | 72 TSX/TS | ~8K | 92 tests | 95% |
| Android App | 71 Kotlin | ~7K | 0 | 93% |
| Edge AI (Python) | 47 Python | ~6K | 100 pytest | 92% |
| Simulator | 15 TS | ~1.3K | 0 | 90% |
| CI/CD | 4 workflows | — | — | 100% |
| Cloud/VPS | Docker + Nginx | — | — | 100% |
| Firmware (ESP32) | 2 .ino | ~400 | — | 100% |
| PA System | 7 Python | ~1.5K | 1 test | 80% |
| Water/Motor | Across all layers | ~2K | 19 @Test | 95% |
| SaaS Multi-Tenant | Across auth+gateway | ~1.5K | 0 | 72% |
| Camera/Video/PTZ | Across all layers | ~4K | 0 | 90% |

---

## Garuda Stories

### WS-1: SaaS Gap Closure (5 stories)

| Story | Title | Deliverable | Acceptance Criteria |
|:------|:------|:-----------|:-------------------|
| G-01 | Hibernate tenant filter | `@FilterDef`/`@Filter` on all entities with farm_id | Query any entity → only own farm's data returned. Cross-tenant request returns empty. |
| G-02 | Per-endpoint RBAC | `@PreAuthorize` on all mutating controller methods | VIEWER cannot POST /sirens/trigger (403). OPERATOR cannot DELETE /zones (403). ADMIN can do both. |
| G-03 | Dashboard sidebar module filter | Actual `.filter()` in Sidebar.tsx | Water-only customer login → only Water/Pumps/Alerts tabs visible. Cameras/Sirens/Zones hidden. |
| G-04 | AOP audit aspect | `@Aspect` auto-logging all `@Auditable` methods | Trigger siren → audit_log row appears without manual code. |
| G-05 | SaaS test coverage | Unit tests for FarmService, PermissionService, ModuleResolution, GatewayFilter, admin pages | 15+ new @Test methods passing. |

### WS-2: Camera/Video Gap Closure (4 stories)

| Story | Title | Deliverable | Acceptance Criteria |
|:------|:------|:-----------|:-------------------|
| G-06 | ExoPlayer dependency | `media3-exoplayer` + `media3-ui` in build.gradle.kts | `./gradlew assembleDebug` succeeds. VideoPlayerScreen compiles. |
| G-07 | Edge zone MQTT subscriber | MQTT listener in farm_edge_node.py for zone reload | Draw zone from dashboard → edge zone engine reloads within 5s (no restart). |
| G-08 | camera_sync.py | Edge periodically pulls camera config from backend API | Register camera in dashboard → edge picks it up within 5 min. |
| G-09 | Dashboard alert video clip | `<video>` element in alert detail page playing 30s clip | View alert → see video clip with play/pause/seek. |

### WS-3: Android Production Polish (3 stories)

| Story | Title | Deliverable | Acceptance Criteria |
|:------|:------|:-----------|:-------------------|
| G-10 | Pull-to-refresh | `PullToRefreshBox` on AlertFeed, Cameras, Water, Devices screens | Swipe down on alerts → spinner shows → list refreshes. |
| G-11 | Alert video clip in Android | ExoPlayer clip in AlertDetailScreen | Tap alert → see 30s video clip playing. |
| G-12 | Android test coverage | Unit tests for key ViewModels + UI tests | 10+ test functions. |

### WS-4: MDM Kiosk Module (12 stories)

**Stories are in `docs/mdm/stories/`.** Linked here for tracking:

| Story | Title | Location |
|:------|:------|:---------|
| M-01 | DB migration V4 | `docs/mdm/stories/STORY_01_DB_MIGRATION.md` |
| M-02 | mdm-service skeleton | `docs/mdm/stories/STORY_02_SERVICE_SKELETON.md` |
| M-03 | Telemetry API | `docs/mdm/stories/STORY_03_TELEMETRY_API.md` |
| M-04 | Command dispatch | `docs/mdm/stories/STORY_04_05_COMMANDS_OTA.md` |
| M-05 | OTA packages | `docs/mdm/stories/STORY_04_05_COMMANDS_OTA.md` |
| M-06 | Android Room v3 | `docs/mdm/stories/STORY_06_07_08_ANDROID_TELEMETRY.md` |
| M-07 | Telemetry collector | `docs/mdm/stories/STORY_06_07_08_ANDROID_TELEMETRY.md` |
| M-08 | WorkManager upload | `docs/mdm/stories/STORY_06_07_08_ANDROID_TELEMETRY.md` |
| M-09 | Device Owner kiosk | `docs/mdm/stories/STORY_09_10_ANDROID_KIOSK_OTA.md` |
| M-10 | MQTT commands + OTA | `docs/mdm/stories/STORY_09_10_ANDROID_KIOSK_OTA.md` |
| M-11 | Dashboard MDM pages | `docs/mdm/stories/STORY_11_DASHBOARD_PAGES.md` |
| M-12 | Tests + Docker | `docs/mdm/stories/STORY_12_TESTS_DOCKER.md` |

### WS-5: E2E Testing (2 stories)

| Story | Title | Deliverable | Acceptance Criteria |
|:------|:------|:-----------|:-------------------|
| G-13 | Preflight checker | Already done: `e2e/preflight_check.py` | `python3 preflight_check.py` → all green. |
| G-14 | E2E test suite execution | Playwright + Maestro + real cameras + ESP8266 | 68 tests across 11 suites. Green on real hardware. |

### WS-6: Infrastructure (2 stories)

| Story | Title | Deliverable | Acceptance Criteria |
|:------|:------|:-----------|:-------------------|
| G-15 | OpenVPN setup | Edge nodes ↔ VPS tunnel | `ping 10.8.0.10` from VPS succeeds. Edge MQTT over VPN. |
| G-16 | TLS / HTTPS | Let's Encrypt cert + Nginx HTTPS | `https://vivasvan-tech.in` loads dashboard. API over HTTPS. |

### WS-7: Build & Release (2 stories)

| Story | Title | Deliverable | Acceptance Criteria |
|:------|:------|:-----------|:-------------------|
| G-17 | Docker image tagging + GHCR push | CI pushes versioned images on tag | `docker pull ghcr.io/mandnargitech/auth-service:garuda` works. |
| G-18 | deploy.sh + compose profiles | Functional deployment script + water_only/security/full profiles | `./deploy.sh --plan full` provisions new farm end-to-end. |

---

## Total: 30 stories (18 Garuda + 12 MDM)

```
WS-1:  5 stories (SaaS gaps)
WS-2:  4 stories (Camera gaps)
WS-3:  3 stories (Android polish)
WS-4: 12 stories (MDM — in docs/mdm/stories/)
WS-5:  2 stories (E2E)
WS-6:  2 stories (Infrastructure)
WS-7:  2 stories (Release)
```
