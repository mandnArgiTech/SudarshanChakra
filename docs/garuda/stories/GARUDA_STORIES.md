# Garuda Stories — Cursor Execution Guide

> Execute in workstream order: WS-1 → WS-2 → WS-3 → WS-4 (MDM) → WS-5 → WS-6 → WS-7
> Within each workstream, execute stories in numerical order.

---

## WS-1: SaaS Gap Closure

### G-01: Hibernate Tenant Filter

**Files to modify:**
- `backend/auth-service/src/main/java/.../model/User.java`
- `backend/device-service/src/main/java/.../model/Camera.java`
- `backend/device-service/src/main/java/.../model/Zone.java`
- `backend/device-service/src/main/java/.../model/EdgeNode.java`
- `backend/device-service/src/main/java/.../model/WorkerTag.java`
- `backend/device-service/src/main/java/.../model/water/WaterTank.java`
- `backend/device-service/src/main/java/.../model/water/WaterMotorController.java`
- `backend/alert-service/src/main/java/.../model/AlertEntity.java` (if it has farm_id)

Add to EACH entity that has a `farm_id` column:
```java
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "farmId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "farm_id = :farmId")
```

**Files to create:**
- `backend/device-service/src/main/java/.../config/TenantFilterConfig.java`
- `backend/alert-service/src/main/java/.../config/TenantFilterConfig.java`

Each service needs a request-scoped bean that enables the filter:
```java
@Component
@RequestScope
public class TenantFilterConfig {
    @Autowired private EntityManager em;
    
    @PostConstruct
    public void enableFilter() {
        UUID farmId = TenantContext.getFarmId();
        if (farmId != null) {
            Session session = em.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("farmId", farmId);
        }
    }
}
```

Copy `TenantContext.java` from auth-service to device-service and alert-service (same code, different package). Wire it in each service's `JwtAuthFilter`.

**Verification:** Login as Farm A user → GET /api/v1/cameras → only Farm A cameras. Login as Farm B → different cameras. Cross-tenant query returns empty.

---

### G-02: Per-Endpoint RBAC

**Files to modify:** Every controller with mutating endpoints across auth, device, alert, siren services.

Add `@PreAuthorize` annotations. Read `PermissionService.java` for the permission matrix.

Key mappings:
```java
// Siren
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_MANAGER')")
@PostMapping("/trigger") public ResponseEntity<?> triggerSiren(...)

// Zones
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_MANAGER')")
@PostMapping public ResponseEntity<?> createZone(...)

@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_MANAGER')")
@DeleteMapping("/{id}") public ResponseEntity<?> deleteZone(...)

// Cameras
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_MANAGER')")
@PostMapping public ResponseEntity<?> createCamera(...)

// Water/Motor
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_MANAGER')")
@PostMapping("/{id}/command") public ResponseEntity<?> sendCommand(...)

// All GET endpoints: any authenticated user (viewer+)
// No @PreAuthorize needed on GETs — default is permitAll for authenticated
```

**Enable method security** in each service's SecurityConfig:
```java
@EnableMethodSecurity
@Configuration
public class SecurityConfig { ... }
```

**Verification:** Login as VIEWER → POST /api/v1/siren/trigger → 403. Login as MANAGER → same endpoint → 200.

---

### G-03: Dashboard Sidebar Module Filter

**File to modify:** `dashboard/src/components/Layout/Sidebar.tsx`

Find where `mainNavItems` is rendered and add the filter:
```tsx
const { modules } = useAuth();

const visibleMainNav = mainNavItems.filter(item =>
    item.module === null || modules.includes(item.module)
);

// Use visibleMainNav instead of mainNavItems in the render
```

**Verification:** Login with a water_only user (modules=["water","pumps","alerts"]) → sidebar shows only Dashboard, Alerts, Water, Pumps, Settings. Cameras/Sirens/Zones/Devices/Workers/Analytics hidden.

---

### G-04: AOP Audit Aspect

**File to modify:** `backend/auth-service/src/main/java/.../audit/AuditAspect.java` (already exists — may need wiring)

Verify the aspect is:
1. Annotated with `@Aspect` and `@Component`
2. Has `@AfterReturning("@annotation(auditable)")` pointcut
3. Writes to `AuditLog` entity via `AuditLogRepository`
4. Extracts farm_id from `TenantContext`, user from `SecurityContext`

Add `@Auditable` annotation to key methods:
- `SirenController.triggerSiren()` → `@Auditable(action="siren.trigger")`
- `AuthService.login()` → `@Auditable(action="user.login")`
- `DeviceService.createZone()` → `@Auditable(action="zone.create")`
- `DeviceService.deleteZone()` → `@Auditable(action="zone.delete")`
- `WaterService.sendMotorCommand()` → `@Auditable(action="pump.command")`
- `UserService.createUser()` → `@Auditable(action="user.create")`

**Verification:** Trigger siren via API → query audit_log table → row exists with action="siren.trigger".

---

### G-05: SaaS Test Coverage

**Files to create:**
- `backend/auth-service/src/test/.../service/FarmServiceTest.java` (5 @Test)
- `backend/auth-service/src/test/.../service/PermissionServiceTest.java` (5 @Test)
- `backend/auth-service/src/test/.../service/ModuleResolutionServiceTest.java` (3 @Test)
- `backend/api-gateway/src/test/.../filter/ModuleAccessGatewayFilterTest.java` (4 @Test)

Follow existing test patterns in `backend/auth-service/src/test/`.

**Verification:** `cd backend && ./gradlew test` → all new tests pass.

---

## WS-2: Camera/Video Gap Closure

### G-06: ExoPlayer Dependency

**File to modify:** `android/app/build.gradle.kts`

Add:
```kotlin
implementation("androidx.media3:media3-exoplayer:1.3.1")
implementation("androidx.media3:media3-ui:1.3.1")
```

**Verification:** `cd android && ./gradlew assembleDebug` succeeds.

---

### G-07: Edge Zone MQTT Subscriber

**File to modify:** `edge/farm_edge_node.py`

After MQTT client connects and subscribes to alert topics, add:
```python
def on_zone_update(client, userdata, msg):
    log.info("Zone update received, reloading zone engine")
    zone_engine.reload()

mqtt_client.subscribe("farm/admin/zone_updated")
mqtt_client.message_callback_add("farm/admin/zone_updated", on_zone_update)
```

**Verification:** POST new zone via dashboard → edge logs "Zone update received" → new zone active in detection pipeline.

---

### G-08: camera_sync.py

**File to create:** `edge/camera_sync.py`

Periodically (every 5 min) pulls camera config from backend API and updates local cameras.json. If cameras changed, signals pipeline restart.

```python
class CameraSync:
    def __init__(self, api_base, node_id, config_path, interval=300):
        ...
    def _sync_loop(self):
        while not self._stop.is_set():
            try:
                remote = requests.get(f"{self.api_base}/api/v1/cameras?nodeId={self.node_id}").json()
                local = self._load_local()
                if remote != local:
                    self._save_local(remote)
                    self._signal_restart()
            except Exception as e:
                log.warning("Camera sync failed: %s", e)
            self._stop.wait(self.interval)
```

Start in `farm_edge_node.py` alongside other services.

**Verification:** Register camera in dashboard → within 5 min, edge picks it up and starts grabbing frames.

---

### G-09: Dashboard Alert Video Clip

**File to modify:** `dashboard/src/pages/AlertsPage.tsx` or the alert detail component.

Add a `<video>` element when alert has `clip_path`:
```tsx
{alert.clipPath && (
    <video
        src={`${edgeBase}/api/clips/${alert.id}.mp4`}
        controls
        className="w-full rounded-lg mt-3"
    />
)}
```

**Verification:** Trigger alert with video recording active → view alert detail → 30s clip plays.

---

## WS-3: Android Production Polish

### G-10: Pull-to-Refresh

**Files to modify:**
- `android/.../ui/screens/alerts/AlertFeedScreen.kt`
- `android/.../ui/screens/cameras/CameraGridScreen.kt`
- `android/.../ui/screens/water/WaterTanksScreen.kt`
- `android/.../ui/screens/devices/DeviceStatusScreen.kt`

Add to each:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
val pullRefreshState = rememberPullToRefreshState()
PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = { viewModel.refresh() },
    state = pullRefreshState,
) { /* existing LazyColumn content */ }
```

Add `isRefreshing` to each ViewModel's UI state + `refresh()` method.

**Verification:** Swipe down on alerts → spinner → list refreshes.

---

### G-11: Android Alert Video Clip

**File to modify:** `android/.../ui/screens/alerts/AlertDetailScreen.kt`

Add ExoPlayer video for clip (requires G-06 first):
```kotlin
if (alert.clipPath != null) {
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("${edgeBase}/api/clips/${alert.id}.mp4"))
            prepare()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { player = exoPlayer } },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}
```

**Verification:** Open alert with clip → video plays in alert detail.

---

### G-12: Android Test Coverage

**Files to create:**
- `android/app/src/test/.../AlertViewModelTest.kt` (3 tests)
- `android/app/src/test/.../WaterViewModelTest.kt` (3 tests)
- `android/app/src/test/.../SirenViewModelTest.kt` (2 tests)
- `android/app/src/test/.../LoginViewModelTest.kt` (2 tests)

Use `kotlinx-coroutines-test`, MockK, JUnit5. Follow standard ViewModel test patterns.

**Verification:** `cd android && ./gradlew test` → all pass.

---

## WS-5: E2E Testing

### G-13: Preflight Checker
Already complete: `e2e/preflight_check.py`. Mark as DONE.

### G-14: E2E Test Execution
Implement the test suites from `docs/E2E_REAL_HARDWARE_TEST_PLAN.md`. This is the largest story — creates:
- `e2e/playwright/` — 8 spec files
- `e2e/maestro/flows/` — 5 YAML flows  
- `e2e/tests/` — 11 pytest files
- `e2e/helpers/` — MQTT, ONVIF, audio helpers

**Verification:** `./e2e/run_full_e2e.sh` → 68 tests pass on real hardware.

---

## WS-6: Infrastructure

### G-15: OpenVPN Setup

**Files to create:**
- `cloud/vpn/server.conf`
- `cloud/vpn/setup_vpn.sh`
- `edge/vpn/client.conf`

Follow standard OpenVPN setup: VPS as server (10.8.0.1), edge nodes as clients (10.8.0.10, 10.8.0.11).

**Verification:** `ping 10.8.0.10` from VPS succeeds.

### G-16: TLS / HTTPS

**Files to modify:**
- `cloud/nginx/nginx-vps.conf` — add HTTPS server block with Let's Encrypt cert

Use certbot for certificate. Redirect HTTP → HTTPS.

**Verification:** `https://vivasvan-tech.in` loads dashboard.

---

## WS-7: Build & Release

### G-17: Docker Image Tagging + GHCR Push

**Files to modify:** `.github/workflows/backend.yml`, `.github/workflows/dashboard.yml`

Add step after build:
```yaml
- name: Push to GHCR
  if: startsWith(github.ref, 'refs/tags/')
  run: |
    echo ${{ secrets.GITHUB_TOKEN }} | docker login ghcr.io -u ${{ github.actor }} --password-stdin
    docker tag auth-service ghcr.io/mandnargitech/auth-service:${{ github.ref_name }}
    docker push ghcr.io/mandnargitech/auth-service:${{ github.ref_name }}
```

**Verification:** `git tag garuda && git push --tags` → images appear in GHCR.

### G-18: Deployment Script + Compose Profiles

**Files to create/modify:**
- `scripts/deploy_saas_farm.sh` — replace stub with functional script
- `cloud/docker-compose.profile-security.yml`
- `cloud/docker-compose.profile-monitoring.yml`

deploy.sh should:
1. Accept `--plan`, `--farm-name`, `--admin-user`, `--domain`
2. Call farm creation API
3. Start appropriate compose profile
4. Configure Nginx for the domain

**Verification:** `./scripts/deploy_saas_farm.sh --plan water_only --farm-name "Test" --admin-user admin` → farm created + services running.
