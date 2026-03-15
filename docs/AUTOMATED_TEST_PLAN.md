# AUTOMATED_TEST_PLAN.md — Complete Test Suite for SudarshanChakra

## For Cloud Agent (Cursor) — Implement and Run Automatically

---

## Overview

This plan defines **6 test layers** covering every feature. All tests must be runnable with a single command per layer, no manual setup, no real hardware. The dev mode mock system enables full end-to-end testing on any machine.

```
Layer 1: Edge AI Unit Tests        (pytest)           — 80+ tests
Layer 2: Edge AI Integration Tests (pytest + MQTT)    — 20+ tests
Layer 3: Backend Unit Tests        (JUnit 5)          — 60+ tests
Layer 4: Backend Integration Tests (Testcontainers)   — 30+ tests
Layer 5: Dashboard Component Tests (Vitest + RTL)     — 25+ tests
Layer 6: Full Stack E2E Tests      (Docker Compose)   — 12 tests
```

**Run everything:**
```bash
./run_all_tests.sh
```

---

## Layer 1: Edge AI Unit Tests (pytest)

**Location:** `edge/tests/`
**Run:** `cd edge && python -m pytest tests/ -v`
**Existing:** 50 tests in 3 files — KEEP THESE, add more below.

### 1.1 New File: `edge/tests/test_mock_camera.py`

```
Tests for mock_camera.py — synthetic frame generation and mock detection cycling.

test_mock_camera_generates_frame()
    Create MockCameraGrabber → call grab_frame() → assert returns 640×480×3 numpy array

test_mock_camera_cycles_scenarios()
    Create MockCameraGrabber → call grab_frame() 10 times
    → get_current_scenario() should cycle through all MOCK_SCENARIOS
    → assert scenario[0] has class="person", scenario[3] has class="snake"
    → assert scenario[8] is None (empty frame)

test_mock_camera_frame_count_increments()
    Grab 5 frames → assert frame_count == 5

test_mock_camera_video_file_mode()
    Create a small test video (3 frames) → MockCameraGrabber with video_path
    → grab 5 frames → assert it loops (frame 4 = frame 1)

test_mock_pipeline_creates_detections()
    Create MockInferencePipeline with 2 cameras
    → register a callback → start in thread → wait 6s → stop
    → assert callback was called with detection dict containing:
      camera_id, class, confidence, bbox, bottom_center, timestamp, mock=True

test_mock_pipeline_detection_interval()
    Set detection_interval=2.0 → start → count callbacks in 7s
    → assert 3-4 detections (not 1, not 20)

test_mock_pipeline_get_stats()
    Start pipeline → wait → get_stats()
    → assert keys: mode="MOCK", cameras_total, cameras_connected, scenario_index
```

### 1.2 New File: `edge/tests/test_mock_lora.py`

```
Tests for mock_lora.py — simulated worker tags and fall detection.

test_mock_lora_worker_present()
    MockLoRaReceiver(worker_present=True) → start → wait 6s
    → is_worker_nearby() → assert True

test_mock_lora_worker_absent()
    MockLoRaReceiver(worker_present=False) → start → wait 6s
    → is_worker_nearby() → assert False

test_mock_lora_toggle_worker()
    Start with worker_present=True → is_worker_nearby() == True
    → set_worker_present(False) → wait 16s (exceed 15s max_age)
    → is_worker_nearby() → assert False

test_mock_lora_get_nearby_workers()
    worker_present=True → start → wait 6s
    → get_nearby_workers() → assert list with 1 entry
    → entry has: tag_id, type="WORKER_PING", mock=True

test_mock_lora_simulate_fall()
    Create MockLoRaReceiver → register fall_callback
    → simulate_fall("TAG-C001")
    → assert callback was called with tag_id="TAG-C001"
    → assert callback payload has TYPE="FALL", PRIORITY="CRITICAL"

test_mock_lora_fall_callback_multiple()
    Register 2 callbacks → simulate_fall → assert BOTH called

test_mock_lora_get_all_tags()
    worker_present=True → start → simulate_fall("TAG-C001")
    → get_all_tags() → assert 2 entries (worker + child)

test_mock_lora_authorized_tags_from_config()
    Create config file with {"tags": ["TAG-X1", "TAG-X2"]}
    → MockLoRaReceiver with that path
    → assert authorized_tags == {"TAG-X1", "TAG-X2"}
```

### 1.3 New File: `edge/tests/test_mock_siren.py`

```
Tests for mock_siren.py — siren command handling and ack publishing.

test_mock_siren_trigger()
    Create MockSirenHandler with mock mqtt client
    → call handle_command with farm/siren/trigger payload
    → assert siren_active == True
    → assert mqtt.publish was called with farm/siren/ack containing status="siren_activated"

test_mock_siren_stop()
    Trigger first → then handle stop command
    → assert siren_active == False
    → assert ack with status="siren_stopped"

test_mock_siren_activation_count()
    Trigger 3 times → assert activation_count == 3

test_mock_siren_get_status()
    Trigger → get_status()
    → assert {"active": True, "mock": True, "activation_count": 1}

test_mock_siren_invalid_payload()
    Send non-JSON payload → should not crash, siren_active stays False
```

### 1.4 New File: `edge/tests/test_pipeline_integration.py`

```
Tests for the full detection → zone → fusion → alert chain using mocks.

test_person_in_perimeter_worker_present()
    Setup: zone engine with perimeter zone + mock LoRa (worker=True)
    → feed person detection inside perimeter
    → alert_engine.process_detection()
    → assert NO alert published (worker suppressed)
    → assert suppression event on farm/events/worker_identified

test_person_in_perimeter_no_worker()
    Setup: zone engine + mock LoRa (worker=False)
    → feed person detection inside perimeter
    → assert alert published to farm/alerts/high

test_child_in_pond_zone()
    Setup: zone engine with zero_tolerance pond zone + mock LoRa (worker=True)
    → feed child detection inside pond zone
    → assert CRITICAL alert published (NOT suppressed despite worker present)

test_person_in_pond_zone_not_suppressed()
    Same as above but class="person"
    → assert CRITICAL alert (zero_tolerance never suppresses)

test_snake_in_hazard_zone()
    → feed snake detection inside hazard zone
    → assert alert published to farm/alerts/high

test_cow_inside_containment_no_alert()
    Setup: zone with type=livestock_containment, cow bbox INSIDE polygon
    → assert NO alert (cow is where it should be)

test_cow_outside_containment_alert()
    → cow bbox OUTSIDE polygon → assert warning alert published

test_fire_detection_temporal_confirmation()
    → feed 1 fire detection → assert NO alert (needs 3 in 5s)
    → feed 2 more within 5s → assert alert published after 3rd

test_detection_deduplication()
    → feed person detection in zone → alert published
    → feed same class+zone within 30s → assert NOT published again
    → wait 31s → feed again → assert published (dedup window expired)

test_dog_detection_no_alert()
    → feed dog detection inside perimeter → assert NO alert (suppression class)

test_fall_event_critical_alert()
    → mock LoRa simulate_fall("TAG-C001")
    → assert CRITICAL alert with detection_class="fall_detected"

test_night_mode_threshold_adjustment()
    → mock nighttime → feed snake detection at confidence 0.42
    → assert alert (night threshold is 0.40, lower than day's 0.50)
    → mock daytime → same detection → assert NO alert (0.42 < 0.50)
```

---

## Layer 2: Edge AI Integration Tests (pytest + MQTT broker)

**Location:** `edge/tests/test_integration.py`
**Run:** `cd edge && docker compose -f docker-compose.dev.yml up -d mqtt-broker && python -m pytest tests/test_integration.py -v`
**Requires:** Local Mosquitto broker running (from dev compose)

### 2.1 New File: `edge/tests/test_integration.py`

```
Integration tests with a real MQTT broker.

test_alert_published_to_mqtt()
    Connect to localhost:1883 → subscribe to farm/alerts/#
    → create full pipeline (mock camera → zone engine → alert engine)
    → process a person detection in a zone
    → assert message received on farm/alerts/high within 5s
    → parse JSON → assert has alert_id, node_id, camera_id, priority

test_siren_ack_published()
    Subscribe to farm/siren/ack
    → publish trigger command to farm/siren/trigger
    → assert ack received within 5s with status="siren_activated"

test_worker_suppression_event_published()
    Subscribe to farm/events/worker_identified
    → setup with mock LoRa (worker=True)
    → process person detection in intrusion zone
    → assert suppression event received

test_heartbeat_published()
    Subscribe to farm/nodes/+/heartbeat
    → start heartbeat → wait 35s
    → assert heartbeat message with node_id, status, timestamp

test_node_status_online_on_connect()
    Subscribe to farm/nodes/+/status
    → create MQTT client with LWT → connect
    → assert "online" status published (retained)

test_dev_simulate_fall_via_mqtt()
    Subscribe to farm/alerts/critical
    → publish to dev/simulate/fall with {"tag_id": "TAG-C001"}
    → assert CRITICAL fall alert received within 10s

test_dev_simulate_worker_toggle()
    Subscribe to farm/alerts/# and farm/events/worker_identified
    → publish dev/simulate/worker_toggle {"present": false}
    → wait for person detection cycle → assert intruder alert
    → publish {"present": true} → wait → assert suppression event
```

---

## Layer 3: Backend Unit Tests (JUnit 5 + Mockito)

**Location:** `backend/<service>/src/test/java/...`
**Run:** `cd backend && ./gradlew test`

### 3.1 alert-service tests

**File:** `backend/alert-service/src/test/java/com/sudarshanchakra/alert/`

```
AlertServiceTest.java
    test_createAlert_savesToRepository()
    test_getAlerts_withPaginationAndFiltering()
    test_getAlerts_filterByPriority()
    test_getAlerts_filterByStatus()
    test_acknowledgeAlert_updatesStatus()
    test_acknowledgeAlert_setsAcknowledgedByAndTimestamp()
    test_resolveAlert_updatesStatus()
    test_markFalsePositive_updatesStatus()
    test_acknowledgeAlert_notFound_throws()
    test_getAlertById_returnsAlert()
    test_getAlertById_notFound_throws()

AlertConsumerServiceTest.java
    test_handleCriticalAlert_parsesAndSaves()
    test_handleHighAlert_parsesAndSaves()
    test_handleWarningAlert_parsesAndSaves()
    test_handleAlert_invalidJson_rejectsMessage()
    test_handleAlert_broadcastsViaWebSocket()

AlertControllerTest.java (@WebMvcTest)
    test_getAlerts_returns200()
    test_getAlerts_withFilters_returns200()
    test_getAlertById_returns200()
    test_getAlertById_notFound_returns404()
    test_acknowledgeAlert_returns200()
    test_acknowledgeAlert_unauthorized_returns403()
```

### 3.2 auth-service tests

**File:** `backend/auth-service/src/test/java/com/sudarshanchakra/auth/`

```
AuthServiceTest.java
    test_register_createsUser()
    test_register_duplicateUsername_throws()
    test_login_validCredentials_returnsToken()
    test_login_invalidPassword_throws()
    test_login_unknownUser_throws()

JwtServiceTest.java
    test_generateToken_returnsValidJwt()
    test_extractUsername_fromToken()
    test_isTokenValid_validToken_returnsTrue()
    test_isTokenValid_expiredToken_returnsFalse()
    test_isTokenValid_tamperedToken_returnsFalse()

AuthControllerTest.java (@WebMvcTest)
    test_login_returns200_withToken()
    test_login_invalidCredentials_returns401()
    test_register_returns201()
    test_register_missingFields_returns400()
    test_updateMqttClientId_returns200()

UserServiceTest.java
    test_getAllUsers_returnsList()
    test_updateMqttClientId_savesId()
```

### 3.3 device-service tests

**File:** `backend/device-service/src/test/java/com/sudarshanchakra/device/`

```
DeviceServiceTest.java
    test_getAllNodes_returnsList()
    test_getNodeById_returnsNode()
    test_getNodeById_notFound_throws()
    test_createCamera_savesToRepository()
    test_getCamerasByNode_returnsList()
    test_createZone_savesToRepository()
    test_deleteZone_removesFromRepository()
    test_getAllTags_returnsList()
    test_createTag_savesToRepository()
    test_deleteTag_removesFromRepository()

EdgeNodeControllerTest.java (@WebMvcTest)
    test_getNodes_returns200()
    test_getNodeById_returns200()

ZoneControllerTest.java (@WebMvcTest)
    test_createZone_returns201()
    test_createZone_invalidPolygon_returns400()
    test_deleteZone_returns204()
```

### 3.4 siren-service tests

**File:** `backend/siren-service/src/test/java/com/sudarshanchakra/siren/`

```
SirenCommandServiceTest.java
    test_triggerSiren_publishesToRabbitMQ()
    test_triggerSiren_savesAuditLog()
    test_stopSiren_publishesToRabbitMQ()
    test_stopSiren_savesAuditLog()
    test_getSirenHistory_returnsList()

SirenControllerTest.java (@WebMvcTest)
    test_triggerSiren_returns200()
    test_triggerSiren_unauthorized_returns403()
    test_stopSiren_returns200()
    test_getSirenHistory_returns200()
```

---

## Layer 4: Backend Integration Tests (Testcontainers)

**Location:** `backend/<service>/src/test/java/.../integration/`
**Run:** `cd backend && ./gradlew integrationTest`
**Requires:** Docker (Testcontainers starts PostgreSQL + RabbitMQ automatically)

### 4.1 New File per Service: `*IntegrationTest.java`

```
Each test uses @Testcontainers to spin up PostgreSQL and RabbitMQ.

AlertServiceIntegrationTest.java
    test_alertConsumer_receivesFromRabbitMQ_storesToPostgres()
        Publish JSON to alert.critical queue → verify row in DB

    test_alertLifecycle_create_acknowledge_resolve()
        Insert alert → PATCH acknowledge → PATCH resolve
        → verify status transitions in DB

    test_alertPagination_withRealData()
        Insert 50 alerts → GET /api/v1/alerts?page=0&size=20
        → assert 20 returned, totalPages=3

AuthServiceIntegrationTest.java
    test_registerAndLogin_fullFlow()
        POST /register → POST /login → use token → GET /users/me

    test_jwtProtection_noToken_returns401()
        GET /api/v1/alerts without Authorization header → 401

    test_jwtProtection_expiredToken_returns401()
        Generate token with past expiry → use it → 401

SirenServiceIntegrationTest.java
    test_triggerSiren_publishesToRabbitMQ_andLogsAudit()
        POST /siren/trigger → verify message in RabbitMQ
        → verify row in siren_actions table

DeviceServiceIntegrationTest.java
    test_crudZone_fullLifecycle()
        POST zone → GET zone → DELETE zone → GET returns 404
```

Add to each service's `build.gradle.kts`:

```kotlin
// Testcontainers
testImplementation("org.testcontainers:testcontainers:1.19.8")
testImplementation("org.testcontainers:postgresql:1.19.8")
testImplementation("org.testcontainers:rabbitmq:1.19.8")
testImplementation("org.testcontainers:junit-jupiter:1.19.8")
testImplementation("org.springframework.boot:spring-boot-testcontainers")

// Separate integration test source set
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}
tasks.register<Test>("integrationTest") {
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
}
```

---

## Layer 5: Dashboard Component Tests (Vitest + React Testing Library)

**Location:** `dashboard/src/**/__tests__/`
**Run:** `cd dashboard && npm test`

Add to `dashboard/package.json` devDependencies:
```json
"@testing-library/react": "^15.0.0",
"@testing-library/jest-dom": "^6.4.0",
"@testing-library/user-event": "^14.5.0",
"jsdom": "^24.0.0"
```

Add `dashboard/vitest.config.ts`:
```typescript
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: './src/test-setup.ts',
    globals: true,
  },
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
});
```

### 5.1 Component Tests

```
src/components/__tests__/AlertCard.test.tsx
    test_renders_alert_with_priority_badge()
    test_renders_detection_class_and_zone()
    test_critical_alert_has_red_indicator()
    test_acknowledged_alert_shows_status()

src/components/__tests__/AlertTable.test.tsx
    test_renders_table_on_desktop()
    test_renders_cards_on_mobile()  (mock window.innerWidth < 1024)
    test_ack_button_visible_for_new_alerts()
    test_ack_button_hidden_for_resolved_alerts()

src/components/__tests__/SirenButton.test.tsx
    test_renders_trigger_button()
    test_click_calls_onTrigger()
    test_shows_loading_state()

src/components/__tests__/Sidebar.test.tsx
    test_renders_all_nav_items()
    test_active_item_highlighted()
    test_alert_badge_shows_count()

src/hooks/__tests__/useAuth.test.tsx
    test_login_stores_token()
    test_logout_clears_token()
    test_provides_user_to_context()
```

### 5.2 Page Tests

```
src/pages/__tests__/DashboardPage.test.tsx
    test_renders_stat_cards()
    test_renders_alert_feed()
    test_renders_node_status()
    test_fetches_alerts_on_mount()  (mock API)

src/pages/__tests__/LoginPage.test.tsx
    test_renders_login_form()
    test_submit_calls_login_api()
    test_shows_error_on_invalid_credentials()
    test_redirects_on_success()

src/pages/__tests__/SirenPage.test.tsx
    test_renders_siren_button()
    test_trigger_calls_api()
    test_shows_siren_history()

src/pages/__tests__/AlertsPage.test.tsx
    test_renders_filter_buttons()
    test_filter_by_priority()
    test_pagination_controls()
```

---

## Layer 6: Full Stack E2E Tests (Docker Compose)

**Location:** `e2e/`
**Run:** `cd e2e && ./run_e2e.sh`
**Requires:** Docker only — everything starts automatically

### 6.1 New File: `e2e/docker-compose.e2e.yml`

Starts the complete stack for E2E testing:
- Mosquitto MQTT broker
- PostgreSQL (with schema init)
- Edge AI (dev mode — mock cameras, mock LoRa, mock siren)
- alert-service, auth-service, device-service, siren-service, api-gateway
- React dashboard

### 6.2 New File: `e2e/run_e2e.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "Starting full stack..."
docker compose -f docker-compose.e2e.yml up -d --build --wait

echo "Waiting for services to be healthy..."
# Wait for API gateway
for i in $(seq 1 60); do
    curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1 && break
    sleep 2
done

echo "Running E2E tests..."
python3 test_full_stack.py

echo "Tearing down..."
docker compose -f docker-compose.e2e.yml down -v
```

### 6.3 New File: `e2e/test_full_stack.py`

```
12 end-to-end tests covering the complete system:

test_01_health_check()
    GET /health → 200

test_02_register_user()
    POST /api/v1/auth/register
    → 201 with token

test_03_login_user()
    POST /api/v1/auth/login
    → 200 with JWT token

test_04_mock_detection_produces_alert_in_db()
    Wait for edge mock detection → query GET /api/v1/alerts
    → assert at least 1 alert exists with mock=true metadata

test_05_alert_filtering()
    GET /api/v1/alerts?priority=critical → only critical alerts
    GET /api/v1/alerts?status=new → only new alerts

test_06_acknowledge_alert()
    PATCH /api/v1/alerts/{id}/acknowledge
    → assert status changed to "acknowledged"

test_07_resolve_alert()
    PATCH /api/v1/alerts/{id}/resolve
    → assert status changed to "resolved"

test_08_trigger_siren()
    POST /api/v1/siren/trigger {"nodeId": "edge-node-dev"}
    → 200 → subscribe to farm/siren/ack → assert ack received

test_09_stop_siren()
    POST /api/v1/siren/stop {"nodeId": "edge-node-dev"}
    → 200 → assert ack received

test_10_simulate_fall_via_mqtt()
    Publish to dev/simulate/fall → wait 10s
    → GET /api/v1/alerts?priority=critical
    → assert fall_detected alert exists

test_11_device_crud()
    GET /api/v1/nodes → assert edge-node-dev present
    POST /api/v1/zones → create zone → GET → verify → DELETE → verify gone

test_12_dashboard_loads()
    GET http://localhost:9080/ → 200 → assert HTML contains "SudarshanChakra"
```

---

## Master Test Runner

### New File: `run_all_tests.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BOLD='\033[1m'
NC='\033[0m'

PASSED=0
FAILED=0

run_layer() {
    local name="$1"
    local cmd="$2"
    echo -e "\n${BOLD}═══ $name ═══${NC}"
    if eval "$cmd"; then
        echo -e "${GREEN}✓ $name PASSED${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ $name FAILED${NC}"
        ((FAILED++))
    fi
}

echo -e "${BOLD}SudarshanChakra — Full Test Suite${NC}"
echo "=================================="

run_layer "Layer 1: Edge Unit Tests" \
    "cd edge && python -m pytest tests/ -v --tb=short 2>&1"

run_layer "Layer 2: Edge Integration Tests" \
    "cd edge && docker compose -f docker-compose.dev.yml up -d mqtt-broker && sleep 3 && python -m pytest tests/test_integration.py -v --tb=short 2>&1; docker compose -f docker-compose.dev.yml down"

run_layer "Layer 3: Backend Unit Tests" \
    "cd backend && ./gradlew test --no-daemon 2>&1"

run_layer "Layer 4: Backend Integration Tests" \
    "cd backend && ./gradlew integrationTest --no-daemon 2>&1"

run_layer "Layer 5: Dashboard Component Tests" \
    "cd dashboard && npm test -- --run 2>&1"

run_layer "Layer 6: Full Stack E2E Tests" \
    "cd e2e && ./run_e2e.sh 2>&1"

echo ""
echo -e "${BOLD}═══ RESULTS ═══${NC}"
echo -e "Passed: ${GREEN}$PASSED${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
TOTAL=$((PASSED + FAILED))
echo "Total:  $TOTAL"

[ "$FAILED" -eq 0 ] && exit 0 || exit 1
```

---

## Test Count Summary

| Layer | Scope | Tests | Framework |
|:------|:------|:------|:----------|
| 1 | Edge unit tests | ~80 (50 existing + 30 new) | pytest |
| 2 | Edge integration (MQTT) | ~20 | pytest + Mosquitto |
| 3 | Backend unit tests | ~60 | JUnit 5 + Mockito |
| 4 | Backend integration | ~30 | Testcontainers |
| 5 | Dashboard components | ~25 | Vitest + RTL |
| 6 | Full stack E2E | 12 | Docker Compose + Python |
| **Total** | | **~227 tests** | |

---

## Feature Coverage Matrix

| Feature | L1 Unit | L2 Integration | L3 Backend | L4 Backend Int | L6 E2E |
|:--------|:-------:|:--------------:|:----------:|:--------------:|:------:|
| Snake detection → alert | ✓ | ✓ | | | ✓ |
| Scorpion detection → alert | ✓ | | | | |
| Fire + color validation | ✓ | ✓ | | | |
| Smoke + texture validation | ✓ | | | | |
| Fire temporal confirm (3 frames) | ✓ | | | | |
| Person + LoRa suppress (worker) | ✓ | ✓ | | | ✓ |
| Person + no worker = intruder | ✓ | ✓ | | | |
| Child + zero-tolerance = CRITICAL | ✓ | ✓ | | | |
| Zero-tolerance NEVER suppressed | ✓ | ✓ | | | |
| Cow inside pen = OK | ✓ | | | | |
| Cow outside pen = warning | ✓ | ✓ | | | |
| ESP32 fall → CRITICAL alert | ✓ | ✓ | | | ✓ |
| Alert deduplication (30s) | ✓ | | | | |
| Night threshold adjustment | ✓ | | | | |
| Dog = suppression class | ✓ | | | | |
| Siren trigger → ack | ✓ | ✓ | ✓ | ✓ | ✓ |
| Siren stop → ack | ✓ | ✓ | ✓ | ✓ | ✓ |
| Alert CRUD + pagination | | | ✓ | ✓ | ✓ |
| JWT auth (login/register) | | | ✓ | ✓ | ✓ |
| Device/zone/tag CRUD | | | ✓ | ✓ | ✓ |
| RabbitMQ → alert-service | | | ✓ | ✓ | ✓ |
| Dashboard renders | | | | | ✓ |
| Dashboard alert table | | | | | ✓ |
| WebSocket live alerts | | | | | ✓ |
| Mock camera frames | ✓ | | | | |
| Mock LoRa worker toggle | ✓ | ✓ | | | |

---

## CI/CD Integration

Add to `.github/workflows/test.yml`:

```yaml
name: Test Suite
on: [push, pull_request]

jobs:
  edge-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.11' }
      - run: pip install -r edge/requirements-dev.txt pytest
      - run: cd edge && python -m pytest tests/ -v --tb=short

  backend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: cd backend && ./gradlew test

  dashboard-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: cd dashboard && npm ci && npm test -- --run

  e2e-tests:
    runs-on: ubuntu-latest
    needs: [edge-tests, backend-tests, dashboard-tests]
    steps:
      - uses: actions/checkout@v4
      - run: cd e2e && ./run_e2e.sh
```
