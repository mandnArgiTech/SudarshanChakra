# MDM Kiosk & Device Management Plan

> **Role:** Principal Full-Stack Architect + MDM Specialist
> **Objective:** Retrofit MDM/Kiosk capabilities without breaking existing functionality.
> **Module ID:** `mdm` (added to SaaS module system — `farms.modules_enabled`)

---

## Design Principles

1. **Modular extension:** MDM is a new SaaS module (`mdm`). Farms without it see zero changes. Existing agricultural data flows untouched.
2. **Dual-mode Android app:** Same APK works as a normal app (current behavior) OR as a Device Owner kiosk. Mode determined by provisioning, not code path.
3. **Offline-first telemetry:** Room DB caches all telemetry locally. WorkManager batch-uploads when connectivity returns. Zero data loss on farm network drops.
4. **MQTT for commands:** Reuses existing RabbitMQ MQTT infrastructure for real-time device commands. No new protocol.
5. **Android 13/14 compliance:** Uses `FOREGROUND_SERVICE_SPECIAL_USE` (API 34), `UsageStatsManager` (no deprecated APIs), `PackageInstaller` session API (not deprecated `installPackage`).

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                    BACKEND (VM1)                              │
│                                                                │
│  ┌────────────┐  ┌─────────────┐  ┌────────────────────┐    │
│  │ mdm-service│  │ auth-service│  │ device-service     │    │
│  │ (new)      │  │ (JWT+RBAC)  │  │ (existing)         │    │
│  │            │  │             │  │                     │    │
│  │ Telemetry  │  │ Farm/user   │  │ Cameras, zones,    │    │
│  │ ingestion  │  │ modules     │  │ nodes, water       │    │
│  │ Device cmd │  │             │  │                     │    │
│  │ OTA push   │  │             │  │                     │    │
│  └─────┬──────┘  └─────────────┘  └────────────────────┘    │
│        │                                                       │
│  ┌─────┴──────┐  ┌─────────────┐  ┌─────────────┐           │
│  │ PostgreSQL │  │ RabbitMQ    │  │ Dashboard   │           │
│  │ +mdm tables│  │ MQTT broker │  │ +MDM pages  │           │
│  └────────────┘  └──────┬──────┘  └─────────────┘           │
└──────────────────────────┼───────────────────────────────────┘
                           │ MQTT
           ┌───────────────┴───────────────┐
           ▼                               ▼
   ┌──────────────┐               ┌──────────────┐
   │ Worker Phone │               │ Worker Phone │
   │ (Kiosk Mode) │               │ (Normal Mode)│
   │              │               │              │
   │ DeviceOwner  │               │ Standard app │
   │ Lock task    │               │ No lockdown  │
   │ Telemetry    │               │ No telemetry │
   │ Silent OTA   │               │ Normal update│
   └──────────────┘               └──────────────┘
```

---

## Step 1: Database Migrations

### New tables (in `mdm-service` schema, same PostgreSQL instance)

```sql
-- ═══════════════════════════════════════════════════════════════
-- Migration V4: MDM Kiosk & Device Management
-- Non-destructive: new tables only, no existing table changes
-- ═══════════════════════════════════════════════════════════════

BEGIN;

-- ── Managed devices (one row per provisioned phone) ──
CREATE TABLE mdm_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL REFERENCES farms(id),
    user_id UUID REFERENCES users(id),              -- Assigned farm worker
    device_name VARCHAR(200) NOT NULL,               -- "Ramesh's Phone"
    android_id VARCHAR(64) UNIQUE NOT NULL,          -- Settings.Secure.ANDROID_ID
    model VARCHAR(100),                              -- "Samsung Galaxy A14"
    os_version VARCHAR(20),                          -- "Android 14"
    app_version VARCHAR(20),                         -- "2.1.0"
    serial_number VARCHAR(100),
    imei VARCHAR(20),
    phone_number VARCHAR(20),
    is_device_owner BOOLEAN DEFAULT FALSE,           -- Provisioned as kiosk?
    is_lock_task_active BOOLEAN DEFAULT FALSE,
    kiosk_pin_hash VARCHAR(255),                     -- Admin escape PIN (bcrypt)
    whitelisted_apps JSONB DEFAULT '["com.sudarshanchakra","com.whatsapp","com.google.android.youtube","com.google.android.apps.maps","com.android.dialer"]',
    policies JSONB DEFAULT '{"status_bar_disabled":true,"safe_boot_blocked":true,"factory_reset_blocked":true,"wifi_config_locked":true,"mobile_data_forced":true}',
    last_heartbeat TIMESTAMPTZ,
    last_telemetry_sync TIMESTAMPTZ,
    mqtt_client_id VARCHAR(100),
    status VARCHAR(20) DEFAULT 'pending',            -- pending, active, locked, wiped, decommissioned
    provisioned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mdm_devices_farm ON mdm_devices(farm_id);
CREATE INDEX idx_mdm_devices_user ON mdm_devices(user_id);
CREATE INDEX idx_mdm_devices_status ON mdm_devices(status);

-- ── App usage telemetry (time-series, partitioned by day) ──
CREATE TABLE mdm_app_usage (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    date DATE NOT NULL,                              -- Usage date
    package_name VARCHAR(200) NOT NULL,              -- "com.whatsapp"
    app_label VARCHAR(200),                          -- "WhatsApp"
    foreground_time_sec INT NOT NULL DEFAULT 0,      -- Seconds in foreground
    launch_count INT DEFAULT 0,
    category VARCHAR(50),                            -- "social", "productivity", "sudarshanchakra"
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mdm_usage_device_date ON mdm_app_usage(device_id, date DESC);
CREATE INDEX idx_mdm_usage_farm_date ON mdm_app_usage(farm_id, date DESC);
CREATE UNIQUE INDEX idx_mdm_usage_unique ON mdm_app_usage(device_id, date, package_name);

-- ── Call log telemetry ──
CREATE TABLE mdm_call_logs (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    phone_number VARCHAR(20),                        -- Masked: last 4 digits shown
    call_type VARCHAR(20) NOT NULL,                  -- incoming, outgoing, missed, rejected
    call_timestamp TIMESTAMPTZ NOT NULL,
    duration_sec INT DEFAULT 0,
    contact_name VARCHAR(200),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_mdm_calls_device_time ON mdm_call_logs(device_id, call_timestamp DESC);
CREATE INDEX idx_mdm_calls_farm_time ON mdm_call_logs(farm_id, call_timestamp DESC);

-- ── Screen time daily aggregates ──
CREATE TABLE mdm_screen_time (
    id BIGSERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    date DATE NOT NULL,
    total_screen_time_sec INT DEFAULT 0,
    unlock_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_mdm_screen_device_date ON mdm_screen_time(device_id, date);

-- ── Remote commands (audit trail) ──
CREATE TABLE mdm_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES mdm_devices(id) ON DELETE CASCADE,
    farm_id UUID NOT NULL,
    command VARCHAR(50) NOT NULL,                     -- UPDATE_APP, LOCK_SCREEN, WIPE_DEVICE, SYNC_TELEMETRY, SET_POLICY
    payload JSONB,                                    -- {"apk_url": "...", "version": "2.2.0"}
    status VARCHAR(20) DEFAULT 'pending',             -- pending, delivered, executed, failed
    issued_by UUID REFERENCES users(id),
    issued_at TIMESTAMPTZ DEFAULT NOW(),
    delivered_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    result JSONB                                      -- {"success": true, "error": null}
);

CREATE INDEX idx_mdm_commands_device ON mdm_commands(device_id, issued_at DESC);

-- ── OTA update packages ──
CREATE TABLE mdm_ota_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    farm_id UUID NOT NULL,
    version VARCHAR(20) NOT NULL,
    apk_url TEXT NOT NULL,
    apk_sha256 VARCHAR(64) NOT NULL,
    apk_size_bytes BIGINT,
    release_notes TEXT,
    mandatory BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

COMMIT;
```

**Key design decisions:**
- All tables scoped by `farm_id` for multi-tenant isolation
- `mdm_app_usage` has a unique index on (device, date, package) — upsert-friendly for batch telemetry
- `mdm_commands` stores full audit trail of every remote action
- Phone numbers in call logs are masked (last 4 digits) for privacy
- Policies stored as JSONB — extensible without schema changes

---

## Step 2: Backend — mdm-service (New Microservice)

### Why a separate service?

MDM is a distinct bounded context (device management, telemetry, policies) that doesn't belong in auth-service or device-service. Keeping it separate means:
- Farms without MDM don't load MDM code
- Can be scaled independently (telemetry ingestion is write-heavy)
- Clean module boundary matches SaaS module system

### Service structure

```
backend/mdm-service/
├── build.gradle.kts
├── src/main/java/com/sudarshanchakra/mdm/
│   ├── MdmServiceApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── RabbitMQConfig.java         # MQTT command topics
│   │   └── JwtAuthFilter.java          # Shared JWT filter
│   ├── controller/
│   │   ├── DeviceController.java       # CRUD managed devices
│   │   ├── TelemetryController.java    # Batch upload endpoint
│   │   ├── CommandController.java      # Issue remote commands
│   │   └── OtaController.java          # OTA package management
│   ├── service/
│   │   ├── DeviceManagementService.java
│   │   ├── TelemetryIngestionService.java
│   │   ├── CommandDispatchService.java # Publishes MQTT commands
│   │   └── OtaService.java
│   ├── model/
│   │   ├── MdmDevice.java
│   │   ├── AppUsage.java
│   │   ├── CallLog.java
│   │   ├── ScreenTime.java
│   │   ├── MdmCommand.java
│   │   └── OtaPackage.java
│   ├── dto/
│   │   ├── TelemetryBatchRequest.java  # Batch upload payload
│   │   ├── DeviceRegistrationRequest.java
│   │   ├── CommandRequest.java
│   │   └── DeviceStatusResponse.java
│   └── repository/
│       ├── MdmDeviceRepository.java
│       ├── AppUsageRepository.java
│       ├── CallLogRepository.java
│       ├── ScreenTimeRepository.java
│       └── MdmCommandRepository.java
└── src/main/resources/
    └── application.yml
```

### API Endpoints

```
DEVICE MANAGEMENT (ADMIN/MANAGER):
  GET    /api/v1/mdm/devices                  → List managed devices (farm-scoped)
  POST   /api/v1/mdm/devices                  → Register new device
  GET    /api/v1/mdm/devices/{id}             → Device detail + status
  PUT    /api/v1/mdm/devices/{id}             → Update device config/policies
  PATCH  /api/v1/mdm/devices/{id}/decommission → Mark decommissioned
  GET    /api/v1/mdm/devices/{id}/usage       → App usage for date range
  GET    /api/v1/mdm/devices/{id}/calls       → Call log for date range
  GET    /api/v1/mdm/devices/{id}/screentime  → Daily screen time

TELEMETRY (DEVICE → SERVER, authenticated by device JWT):
  POST   /api/v1/mdm/telemetry/batch          → Batch upload (usage + calls + screen time)
  POST   /api/v1/mdm/telemetry/heartbeat      → Device alive ping

COMMANDS (ADMIN → DEVICE via MQTT):
  POST   /api/v1/mdm/commands                  → Issue command (UPDATE_APP, LOCK_SCREEN, WIPE_DEVICE, SYNC_TELEMETRY, SET_POLICY)
  GET    /api/v1/mdm/commands/{deviceId}       → Command history

OTA:
  POST   /api/v1/mdm/ota/packages              → Upload new APK version
  GET    /api/v1/mdm/ota/packages              → List available versions
  GET    /api/v1/mdm/ota/packages/{id}/download → Download APK binary
```

### Telemetry Batch Upload Payload

```json
{
    "device_id": "uuid",
    "android_id": "abc123",
    "app_version": "2.1.0",
    "batch_timestamp": "2026-03-22T14:30:00Z",
    "app_usage": [
        {
            "date": "2026-03-22",
            "package_name": "com.whatsapp",
            "app_label": "WhatsApp",
            "foreground_time_sec": 3420,
            "launch_count": 15,
            "category": "social"
        },
        {
            "date": "2026-03-22",
            "package_name": "com.sudarshanchakra",
            "app_label": "SudarshanChakra",
            "foreground_time_sec": 18200,
            "launch_count": 3,
            "category": "sudarshanchakra"
        }
    ],
    "call_logs": [
        {
            "phone_number_masked": "****5678",
            "call_type": "outgoing",
            "call_timestamp": "2026-03-22T10:15:00Z",
            "duration_sec": 180,
            "contact_name": "Supplier"
        }
    ],
    "screen_time": {
        "date": "2026-03-22",
        "total_screen_time_sec": 28800,
        "unlock_count": 45
    }
}
```

### Command Dispatch via MQTT

```java
@Service
public class CommandDispatchService {
    @Autowired private RabbitTemplate rabbitTemplate;
    
    public void dispatchCommand(UUID deviceId, String command, JsonNode payload) {
        // Save command record
        MdmCommand cmd = MdmCommand.builder()
            .deviceId(deviceId).command(command)
            .payload(payload).status("pending").build();
        commandRepo.save(cmd);
        
        // Publish to device-specific MQTT topic
        // Device subscribes to: farm/mdm/{deviceId}/command
        String topic = "farm.mdm." + deviceId + ".command";
        String message = objectMapper.writeValueAsString(Map.of(
            "command_id", cmd.getId(),
            "command", command,
            "payload", payload,
            "issued_at", Instant.now()
        ));
        rabbitTemplate.convertAndSend("farm.commands", topic, message);
    }
}
```

---

## Step 3: Dashboard — MDM Pages

### Navigation Integration

Add to existing sidebar as a new module:

```typescript
// In Sidebar.tsx navItems array — only visible when farm has 'mdm' module
{ to: '/mdm', icon: Smartphone, label: 'MDM', module: 'mdm' },
```

This uses the existing SaaS module filtering — farms without `mdm` in `modules_enabled` never see this tab.

### New Pages

#### 3a. Device List (`/mdm`)

```
┌─────────────────────────────────────────────────────────────┐
│  Device Management                    [+ Register Device]    │
│  3 devices · 2 active · 1 pending                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ ● Ramesh's Phone              Samsung A14 · Android 14  ││
│  │   v2.1.0 · Kiosk: ON · Last seen: 2 min ago            ││
│  │   Today: 8.2h screen · WhatsApp 57m · YouTube 23m      ││
│  │   [View Details]  [Send Command ▾]                      ││
│  ├─────────────────────────────────────────────────────────┤│
│  │ ● Suresh's Phone              Redmi Note 12 · Android 13││
│  │   v2.1.0 · Kiosk: ON · Last seen: 15 min ago           ││
│  │   Today: 6.5h screen · WhatsApp 1h12m · YouTube 45m    ││
│  │   [View Details]  [Send Command ▾]                      ││
│  ├─────────────────────────────────────────────────────────┤│
│  │ ○ Priya's Phone               Pending provisioning      ││
│  │   Not yet connected                                      ││
│  │   [View Details]  [Copy Provision CMD]                  ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

#### 3b. Device Detail (`/mdm/devices/:id`)

```
┌─────────────────────────────────────────────────────────────┐
│  ← Ramesh's Phone                                            │
│  Samsung Galaxy A14 · Android 14 · v2.1.0                   │
│  Kiosk: ON · Lock Task: Active · Last heartbeat: 2 min ago │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─── Quick Actions ──────────────────────────────────────┐ │
│  │ [Force Sync]  [Push OTA]  [Lock Screen]  [⚠ Wipe]    │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─── Screen Time (7 days) ───────────────────────────────┐ │
│  │  ██████████████████████████████████████  8.2h today    │ │
│  │  ████████████████████████████           6.5h yesterday │ │
│  │  ██████████████████████████████████     7.8h Mar 20    │ │
│  │                                                         │ │
│  │  App breakdown today:                                   │ │
│  │  SudarshanChakra  ████████████████████  5h 4m (62%)    │ │
│  │  WhatsApp          ██████               57m   (12%)    │ │
│  │  YouTube           ████                 23m   (5%)     │ │
│  │  Maps              ██                   12m   (2%)     │ │
│  │  Phone             █                    8m    (2%)     │ │
│  │  Other             ███                  17m   (3%)     │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─── Call Log ───────────────────────────────────────────┐ │
│  │  Time         Type       Number     Duration           │ │
│  │  10:15 AM     Outgoing   ****5678   3m 0s              │ │
│  │  09:42 AM     Incoming   ****1234   1m 15s             │ │
│  │  09:10 AM     Missed     ****9012   —                  │ │
│  │  Yesterday 6:30 PM  Outgoing  ****3456   5m 22s        │ │
│  │  [Load more...]                                         │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─── Policies ───────────────────────────────────────────┐ │
│  │  Status bar disabled     [ON]                           │ │
│  │  Safe boot blocked       [ON]                           │ │
│  │  Factory reset blocked   [ON]                           │ │
│  │  Wi-Fi config locked     [ON]                           │ │
│  │  Mobile data forced      [ON]                           │ │
│  │  Whitelisted apps: SC, WhatsApp, YouTube, Maps, Dialer │ │
│  │  [Edit Policies]                                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─── Command History ────────────────────────────────────┐ │
│  │  Mar 22 14:30  SYNC_TELEMETRY   ✓ Executed             │ │
│  │  Mar 22 10:00  UPDATE_APP       ✓ Installed v2.1.0     │ │
│  │  Mar 21 08:00  SET_POLICY       ✓ Applied              │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

These mockups use the existing `sc-*` dark theme: `sc-surface` cards, `sc-accent` for active elements, `sc-critical` for wipe button, `sc-success` for online status, `sc-text-dim` for secondary info.

---

## Step 4: Android — MDM Agent Retrofit

### 4a. Device Admin Receiver

```
android/app/src/main/java/com/sudarshanchakra/mdm/
├── SudarshanDeviceAdminReceiver.kt     # DeviceAdminReceiver subclass
├── KioskManager.kt                      # Lock task, policies, status bar
├── TelemetryCollector.kt                # UsageStatsManager + CallLog
├── TelemetryUploadWorker.kt             # WorkManager periodic batch upload
├── SilentInstaller.kt                   # PackageInstaller session API
├── MdmCommandHandler.kt                 # MQTT command processor
├── MdmLocalDatabase.kt                  # Room entities for offline cache
├── KioskLauncherActivity.kt            # Home launcher with whitelisted grid
└── DevEscapeDialog.kt                   # Hidden PIN-protected escape menu
```

### 4b. Provisioning

Device Owner provisioning via ADB (one-time setup per phone):

```bash
# Factory reset the phone first, then:
adb shell dpm set-device-owner com.sudarshanchakra/.mdm.SudarshanDeviceAdminReceiver

# Or via QR code / NFC for zero-touch enrollment (Android 7+)
```

The `SudarshanDeviceAdminReceiver` is registered in AndroidManifest:

```xml
<!-- MDM Device Admin (only active when provisioned as Device Owner) -->
<receiver
    android:name=".mdm.SudarshanDeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_policies" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
        <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
    </intent-filter>
</receiver>

<!-- Kiosk launcher (declared as HOME to replace default launcher) -->
<activity
    android:name=".mdm.KioskLauncherActivity"
    android:exported="true"
    android:launchMode="singleInstance">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>

<!-- Permissions for telemetry -->
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

### 4c. KioskManager — Policy Enforcement

```kotlin
class KioskManager(private val context: Context) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val componentName = ComponentName(context, SudarshanDeviceAdminReceiver::class.java)
    
    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)
    
    fun enforceKioskPolicies() {
        if (!isDeviceOwner) return
        
        // 1. Set as preferred home activity (replaces default launcher)
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        dpm.addPersistentPreferredActivity(
            componentName,
            filter,
            ComponentName(context, KioskLauncherActivity::class.java)
        )
        
        // 2. Start lock task mode (pins the app, blocks home/recent)
        val packages = arrayOf(
            context.packageName,
            "com.whatsapp",
            "com.google.android.youtube",
            "com.google.android.apps.maps",
            "com.android.dialer",
        )
        dpm.setLockTaskPackages(componentName, packages)
        
        // 3. Disable status bar
        dpm.setStatusBarDisabled(componentName, true)
        
        // 4. Block factory reset
        dpm.addUserRestriction(componentName, UserManager.DISALLOW_FACTORY_RESET)
        
        // 5. Block safe mode
        dpm.addUserRestriction(componentName, UserManager.DISALLOW_SAFE_BOOT)
        
        // 6. Lock Wi-Fi config
        dpm.addUserRestriction(componentName, UserManager.DISALLOW_CONFIG_WIFI)
        
        // 7. Force mobile data (prevent turning off)
        // API 34+: use setMobileDataEnabled if available
        if (Build.VERSION.SDK_INT >= 34) {
            // Managed configuration via DevicePolicyManager
        }
        
        // 8. Auto-grant runtime permissions
        dpm.setPermissionGrantState(componentName, context.packageName,
            Manifest.permission.READ_CALL_LOG,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
        // PACKAGE_USAGE_STATS requires AppOps, not runtime permission:
        dpm.setPermissionPolicy(componentName,
            DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)
    }
    
    fun exitKiosk(pin: String): Boolean {
        // Verify PIN against stored hash
        if (!verifyEscapePin(pin)) return false
        // Stop lock task
        (context as? Activity)?.stopLockTask()
        // Re-enable status bar
        dpm.setStatusBarDisabled(componentName, false)
        return true
    }
    
    fun decommission(pin: String): Boolean {
        if (!verifyEscapePin(pin)) return false
        exitKiosk(pin)
        dpm.clearDeviceOwnerApp(context.packageName)
        return true
    }
}
```

### 4d. Telemetry Collector

```kotlin
class TelemetryCollector(private val context: Context) {
    
    // ── App Usage (UsageStatsManager — not deprecated in API 34) ──
    fun collectAppUsage(date: LocalDate): List<AppUsageEntity> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .map { stat ->
                AppUsageEntity(
                    date = date,
                    packageName = stat.packageName,
                    appLabel = getAppLabel(stat.packageName),
                    foregroundTimeSec = (stat.totalTimeInForeground / 1000).toInt(),
                    launchCount = if (Build.VERSION.SDK_INT >= 29) stat.appLaunchCount else 0,
                    category = categorizeApp(stat.packageName),
                    synced = false,
                )
            }
    }
    
    // ── Call Log ──
    fun collectCallLog(since: Long): List<CallLogEntity> {
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION,
                    CallLog.Calls.CACHED_NAME),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        ) ?: return emptyList()
        
        return cursor.use { c ->
            generateSequence { if (c.moveToNext()) c else null }.map {
                val number = c.getString(0) ?: ""
                CallLogEntity(
                    phoneNumberMasked = maskNumber(number), // "****5678"
                    callType = when (c.getInt(1)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "unknown"
                    },
                    callTimestamp = Instant.ofEpochMilli(c.getLong(2)),
                    durationSec = c.getInt(3),
                    contactName = c.getString(4),
                    synced = false,
                )
            }.toList()
        }
    }
    
    // ── Screen Time ──
    fun collectScreenTime(date: LocalDate): ScreenTimeEntity {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val events = usm.queryEvents(start, end)
        var totalForeground = 0L
        var unlocks = 0
        var lastForegroundStart = 0L
        
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> lastForegroundStart = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastForegroundStart > 0) totalForeground += event.timeStamp - lastForegroundStart
                    lastForegroundStart = 0
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        
        return ScreenTimeEntity(
            date = date,
            totalScreenTimeSec = (totalForeground / 1000).toInt(),
            unlockCount = unlocks,
            synced = false,
        )
    }
}
```

### 4e. Offline Cache (Room DB Extension)

```kotlin
// Extend existing AppDatabase — bump version to 2
@Database(
    entities = [AlertEntity::class, AppUsageEntity::class, 
                CallLogEntity::class, ScreenTimeEntity::class,
                PendingCommandEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun callLogDao(): CallLogDao
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun pendingCommandDao(): PendingCommandDao
}

@Entity(tableName = "mdm_app_usage_cache")
data class AppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val packageName: String,
    val appLabel: String,
    val foregroundTimeSec: Int,
    val launchCount: Int,
    val category: String,
    val synced: Boolean = false,
)

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM mdm_app_usage_cache WHERE synced = 0")
    suspend fun getUnsyncedUsage(): List<AppUsageEntity>
    
    @Query("UPDATE mdm_app_usage_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
    
    @Upsert
    suspend fun upsert(entity: AppUsageEntity)
}
```

### 4f. Telemetry Upload Worker (WorkManager)

```kotlin
class TelemetryUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val api = ApiService.getInstance()
        
        // Collect fresh data
        val collector = TelemetryCollector(applicationContext)
        val today = LocalDate.now()
        
        // Collect and cache locally first
        collector.collectAppUsage(today).forEach { db.appUsageDao().upsert(it) }
        collector.collectCallLog(getLastSyncTimestamp()).forEach { db.callLogDao().insert(it) }
        db.screenTimeDao().upsert(collector.collectScreenTime(today))
        
        // Attempt batch upload
        try {
            val unsyncedUsage = db.appUsageDao().getUnsyncedUsage()
            val unsyncedCalls = db.callLogDao().getUnsyncedCalls()
            val unsyncedScreen = db.screenTimeDao().getUnsynced()
            
            if (unsyncedUsage.isEmpty() && unsyncedCalls.isEmpty()) {
                return Result.success()
            }
            
            val batch = TelemetryBatchRequest(
                deviceId = getDeviceId(),
                androidId = getAndroidId(),
                appVersion = getAppVersion(),
                appUsage = unsyncedUsage.map { it.toDto() },
                callLogs = unsyncedCalls.map { it.toDto() },
                screenTime = unsyncedScreen.firstOrNull()?.toDto(),
            )
            
            val response = api.uploadTelemetryBatch(batch)
            if (response.isSuccessful) {
                db.appUsageDao().markSynced(unsyncedUsage.map { it.id })
                db.callLogDao().markSynced(unsyncedCalls.map { it.id })
                db.screenTimeDao().markSynced(unsyncedScreen.map { it.id })
                return Result.success()
            }
            return Result.retry() // Will retry with backoff
        } catch (e: Exception) {
            // Network down — data stays in Room, will retry
            return Result.retry()
        }
    }
}

// Scheduled in SudarshanChakraApp.onCreate():
val uploadWork = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(
    30, TimeUnit.MINUTES        // Every 30 min
).setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
).setBackoffCriteria(
    BackoffPolicy.EXPONENTIAL,
    10, TimeUnit.MINUTES        // Retry 10m, 20m, 40m...
).build()

WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "telemetry_upload",
    ExistingPeriodicWorkPolicy.KEEP,
    uploadWork
)
```

### 4g. Silent OTA Installer

```kotlin
class SilentInstaller(private val context: Context) {
    
    fun installApk(apkUri: Uri): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return false
        
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        
        context.contentResolver.openInputStream(apkUri)?.use { input ->
            session.openWrite("sudarshanchakra_update.apk", 0, -1).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }
        
        val intent = PendingIntent.getBroadcast(
            context, sessionId,
            Intent("com.sudarshanchakra.INSTALL_COMPLETE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        
        session.commit(intent.intentSender)
        return true
    }
}
```

### 4h. MQTT Command Handler

```kotlin
class MdmCommandHandler(
    private val kioskManager: KioskManager,
    private val silentInstaller: SilentInstaller,
    private val context: Context,
) {
    fun handleCommand(topic: String, payload: String) {
        val cmd = Json.decodeFromString<MdmCommandMessage>(payload)
        
        when (cmd.command) {
            "UPDATE_APP" -> {
                val apkUrl = cmd.payload?.get("apk_url")?.asString ?: return
                // Download APK, then silent install
                downloadAndInstall(apkUrl)
                ackCommand(cmd.commandId, success = true)
            }
            "LOCK_SCREEN" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.lockNow()
                ackCommand(cmd.commandId, success = true)
            }
            "WIPE_DEVICE" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.wipeData(0)
            }
            "SYNC_TELEMETRY" -> {
                // Trigger immediate WorkManager upload
                WorkManager.getInstance(context).enqueue(
                    OneTimeWorkRequest.from(TelemetryUploadWorker::class.java)
                )
                ackCommand(cmd.commandId, success = true)
            }
            "SET_POLICY" -> {
                // Update kiosk policies from payload
                cmd.payload?.let { kioskManager.applyPolicies(it) }
                ackCommand(cmd.commandId, success = true)
            }
        }
    }
    
    private fun ackCommand(commandId: String, success: Boolean) {
        // Publish ack to farm/mdm/{deviceId}/ack
        mqttClient.publish("farm/mdm/$deviceId/ack", 
            """{"command_id":"$commandId","success":$success}""")
    }
}
```

### 4i. Kiosk Launcher Home Screen

```kotlin
@Composable
fun KioskLauncherScreen(
    onAppLaunch: (String) -> Unit,
    onOpenDashboard: () -> Unit,
    onDevEscape: () -> Unit,
) {
    // Main area: SudarshanChakra dashboard (existing NavGraph)
    // Bottom: whitelisted app grid
    
    Column(modifier = Modifier.fillMaxSize()) {
        // SudarshanChakra dashboard fills most of the screen
        Box(modifier = Modifier.weight(1f)) {
            NavGraph() // Existing dashboard
        }
        
        // Whitelisted app grid at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val apps = listOf(
                AppShortcut("WhatsApp", "com.whatsapp", Icons.Filled.Chat),
                AppShortcut("YouTube", "com.google.android.youtube", Icons.Filled.PlayArrow),
                AppShortcut("Maps", "com.google.android.apps.maps", Icons.Filled.Map),
                AppShortcut("Phone", "com.android.dialer", Icons.Filled.Phone),
            )
            apps.forEach { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onAppLaunch(app.packageName) }
                ) {
                    Icon(app.icon, contentDescription = app.label, modifier = Modifier.size(32.dp))
                    Text(app.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    
    // Hidden escape: triple-tap on app version in Settings → PIN dialog
    // Implemented in SettingsScreen via a click counter on version text
}
```

### 4j. Developer Escape Hatch

Hidden in the existing SettingsScreen — tap the app version 7 times to reveal the PIN dialog:

```kotlin
// In SettingsScreen.kt — add tap counter on version text
var tapCount by remember { mutableIntStateOf(0) }
var showEscapeDialog by remember { mutableStateOf(false) }

Text(
    text = "v${BuildConfig.VERSION_NAME}",
    modifier = Modifier.clickable {
        tapCount++
        if (tapCount >= 7) { showEscapeDialog = true; tapCount = 0 }
    }
)

if (showEscapeDialog) {
    DevEscapeDialog(
        onDismiss = { showEscapeDialog = false },
        onExitKiosk = { pin -> kioskManager.exitKiosk(pin) },
        onDecommission = { pin -> kioskManager.decommission(pin) },
    )
}
```

---

## Step 5: Android 13/14 API Compliance

| API | Status in API 34 | Our approach |
|:----|:-----------------|:-------------|
| `DevicePolicyManager.setStatusBarDisabled` | Available for Device Owner | ✅ Used |
| `DevicePolicyManager.addUserRestriction` | Available | ✅ Used |
| `setLockTaskPackages` | Available for Device Owner | ✅ Used |
| `PackageInstaller` session API | Preferred over deprecated `installPackage` | ✅ Used |
| `UsageStatsManager.queryUsageStats` | Available (requires AppOps permission) | ✅ Auto-granted via DPM |
| `READ_CALL_LOG` | Requires runtime permission (API 33+) | ✅ Auto-granted via `setPermissionGrantState` |
| `FOREGROUND_SERVICE_SPECIAL_USE` | New in API 34, required for non-standard foreground services | ✅ Declared in manifest |
| `PACKAGE_USAGE_STATS` | Protected permission — granted via AppOps, not runtime | ✅ DPM auto-grant policy |
| `setMobileDataEnabled` (ConnectivityManager) | Removed in API 33 | ⚠️ Use `UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS` restriction instead |
| `installPackage` (deprecated) | Removed | ✅ Using `PackageInstaller` session API |

---

## Step 6: Offline Telemetry Caching Flow

```
NORMAL FLOW (network available):
  Collector → Room DB (synced=false) → WorkManager → POST /telemetry/batch → Room (synced=true)

OFFLINE FLOW (farm network down):
  Collector → Room DB (synced=false) → WorkManager retries with exponential backoff
  Data accumulates in Room: up to 30 days of app usage + call logs + screen time
  
  Network returns → WorkManager fires → batch upload → Room marks synced → cleanup

BATCH SIZE CONTROL:
  Max 500 records per upload → paginate if more accumulated
  Compressed JSON (gzip) → ~10KB per day per device

ROOM CLEANUP:
  Synced records older than 7 days → auto-deleted by scheduled worker
```

---

## Module Integration — Non-Breaking

| Change | Impact on existing |
|:-------|:------------------|
| New `mdm-service` microservice | Zero — separate service, separate tables |
| `mdm` in `ModuleConstants.ALL_MODULES` | Zero — only visible to farms with `mdm` enabled |
| New sidebar nav item | Zero — filtered by module, invisible to non-MDM farms |
| Room DB version 1→2 | Migration adds new tables, doesn't alter `AlertEntity` |
| New Android permissions | Only effective when Device Owner; normal installs ignore them |
| AndroidManifest additions | DeviceAdminReceiver only activates on `dpm set-device-owner` |
| KioskLauncherActivity | Only becomes HOME when provisioned; normal installs use default launcher |
| WorkManager workers | Run in background, zero impact on UI thread |
| MQTT command topics | New topic namespace `farm/mdm/...`, doesn't conflict with existing `farm/alerts/...` |

---

## Implementation Order (For Cursor)

**Phase 1 (2 days): Database + backend service**
- V4 migration SQL
- mdm-service skeleton (controllers, services, models, repos)
- Telemetry batch endpoint
- Command dispatch via MQTT
- Add `mdm` to ModuleConstants + gateway routes

**Phase 2 (2 days): Android MDM agent**
- DeviceAdminReceiver + device_admin_policies.xml
- KioskManager (policies, lock task)
- TelemetryCollector (usage stats, call log, screen time)
- Room DB migration v2 (offline cache entities)
- TelemetryUploadWorker (WorkManager)
- MdmCommandHandler (MQTT)

**Phase 3 (1 day): Silent OTA + kiosk launcher**
- SilentInstaller (PackageInstaller session API)
- KioskLauncherActivity (HOME + whitelisted app grid)
- DevEscapeDialog (hidden PIN menu)

**Phase 4 (2 days): Dashboard MDM pages**
- Device list page
- Device detail (screen time chart, call log, commands, policies)
- Command action buttons (sync, OTA, lock, wipe)
- New sidebar nav with `mdm` module filter

**Total: 7 days, zero existing code modified (except Room DB version bump and ModuleConstants addition).**
