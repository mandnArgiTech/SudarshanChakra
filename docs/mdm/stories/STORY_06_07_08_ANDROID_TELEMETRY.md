# Story 06: Android Room DB v3 — MDM Offline Cache

## Prerequisites
- Stories 01-03 complete (backend ready to receive telemetry)

## Goal
Extend the Android Room database with MDM telemetry cache tables. Data is stored locally first (synced=false) and batch-uploaded when network is available.

## Reference files (READ FIRST)
- `android/app/src/main/java/com/sudarshanchakra/data/db/AppDatabase.kt`
- `android/app/src/main/java/com/sudarshanchakra/data/db/AlertEntity.kt`
- `android/app/src/main/java/com/sudarshanchakra/data/db/AlertDao.kt`

## Files to CREATE

### 1. `android/app/src/main/java/com/sudarshanchakra/mdm/data/MdmAppUsageEntity.kt`
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mdm_app_usage_cache")
data class MdmAppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                    // "2026-03-22"
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String = "",
    @ColumnInfo(name = "foreground_time_sec") val foregroundTimeSec: Int = 0,
    @ColumnInfo(name = "launch_count") val launchCount: Int = 0,
    val category: String = "",
    val synced: Boolean = false,
)
```

### 2. `android/app/src/main/java/com/sudarshanchakra/mdm/data/MdmCallLogEntity.kt`
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mdm_call_log_cache")
data class MdmCallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number_masked") val phoneNumberMasked: String = "",
    @ColumnInfo(name = "call_type") val callType: String,          // incoming, outgoing, missed
    @ColumnInfo(name = "call_timestamp") val callTimestamp: String, // ISO 8601
    @ColumnInfo(name = "duration_sec") val durationSec: Int = 0,
    @ColumnInfo(name = "contact_name") val contactName: String = "",
    val synced: Boolean = false,
)
```

### 3. `android/app/src/main/java/com/sudarshanchakra/mdm/data/MdmScreenTimeEntity.kt`
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mdm_screen_time_cache")
data class MdmScreenTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,                    // "2026-03-22"
    @ColumnInfo(name = "total_screen_time_sec") val totalScreenTimeSec: Int = 0,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int = 0,
    val synced: Boolean = false,
)
```

### 4. `android/app/src/main/java/com/sudarshanchakra/mdm/data/MdmAppUsageDao.kt`
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.*

@Dao
interface MdmAppUsageDao {
    @Query("SELECT * FROM mdm_app_usage_cache WHERE synced = 0 ORDER BY date DESC LIMIT 500")
    suspend fun getUnsynced(): List<MdmAppUsageEntity>

    @Query("UPDATE mdm_app_usage_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MdmAppUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MdmAppUsageEntity>)

    @Query("DELETE FROM mdm_app_usage_cache WHERE synced = 1 AND date < :beforeDate")
    suspend fun cleanOldSynced(beforeDate: String)
}
```

### 5. `android/app/src/main/java/com/sudarshanchakra/mdm/data/MdmCallLogDao.kt`
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.*

@Dao
interface MdmCallLogDao {
    @Query("SELECT * FROM mdm_call_log_cache WHERE synced = 0 ORDER BY call_timestamp DESC LIMIT 500")
    suspend fun getUnsynced(): List<MdmCallLogEntity>

    @Query("UPDATE mdm_call_log_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MdmCallLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MdmCallLogEntity>)

    @Query("SELECT MAX(call_timestamp) FROM mdm_call_log_cache")
    suspend fun getLastTimestamp(): String?

    @Query("DELETE FROM mdm_call_log_cache WHERE synced = 1 AND call_timestamp < :before")
    suspend fun cleanOldSynced(before: String)
}
```

### 6. `android/app/src/main/java/com/sudarshanchakra/mdm/data/MdmScreenTimeDao.kt`
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.*

@Dao
interface MdmScreenTimeDao {
    @Query("SELECT * FROM mdm_screen_time_cache WHERE synced = 0 LIMIT 30")
    suspend fun getUnsynced(): List<MdmScreenTimeEntity>

    @Query("UPDATE mdm_screen_time_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MdmScreenTimeEntity)

    @Query("DELETE FROM mdm_screen_time_cache WHERE synced = 1 AND date < :beforeDate")
    suspend fun cleanOldSynced(beforeDate: String)
}
```

## Files to MODIFY

### 1. `android/app/src/main/java/com/sudarshanchakra/data/db/AppDatabase.kt`
```kotlin
// Change version from 2 to 3
// Add new entities to @Database annotation
// Add new DAOs
// Add migration 2→3

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS mdm_app_usage_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            date TEXT NOT NULL, package_name TEXT NOT NULL,
            app_label TEXT NOT NULL DEFAULT '', foreground_time_sec INTEGER NOT NULL DEFAULT 0,
            launch_count INTEGER NOT NULL DEFAULT 0, category TEXT NOT NULL DEFAULT '',
            synced INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS mdm_call_log_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            phone_number_masked TEXT NOT NULL DEFAULT '', call_type TEXT NOT NULL,
            call_timestamp TEXT NOT NULL, duration_sec INTEGER NOT NULL DEFAULT 0,
            contact_name TEXT NOT NULL DEFAULT '', synced INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS mdm_screen_time_cache (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            date TEXT NOT NULL, total_screen_time_sec INTEGER NOT NULL DEFAULT 0,
            unlock_count INTEGER NOT NULL DEFAULT 0, synced INTEGER NOT NULL DEFAULT 0)""")
    }
}

@Database(
    entities = [AlertEntity::class, MdmAppUsageEntity::class, MdmCallLogEntity::class, MdmScreenTimeEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun mdmAppUsageDao(): MdmAppUsageDao
    abstract fun mdmCallLogDao(): MdmCallLogDao
    abstract fun mdmScreenTimeDao(): MdmScreenTimeDao
}
```

### 2. `android/app/src/main/java/com/sudarshanchakra/di/AppModule.kt`
Add the MIGRATION_2_3 to Room builder and provide new DAOs:
```kotlin
// In the Room.databaseBuilder chain, add:
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)

// Add @Provides for each new DAO:
@Provides fun provideMdmAppUsageDao(db: AppDatabase) = db.mdmAppUsageDao()
@Provides fun provideMdmCallLogDao(db: AppDatabase) = db.mdmCallLogDao()
@Provides fun provideMdmScreenTimeDao(db: AppDatabase) = db.mdmScreenTimeDao()
```

## Verification
```bash
# Build the Android project — should compile without errors
cd android && ./gradlew assembleDebug
# Install on emulator and verify Room DB creates the new tables
```

---

# Story 07: Android Telemetry Collector

## Prerequisites
- Story 06 complete (Room entities + DAOs exist)

## Goal
Create `TelemetryCollector.kt` that reads app usage (UsageStatsManager), call logs (ContentResolver), and screen time and stores them in Room DB.

## Files to CREATE

### 1. `android/app/src/main/java/com/sudarshanchakra/mdm/TelemetryCollector.kt`
```kotlin
package com.sudarshanchakra.mdm

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.provider.CallLog
import com.sudarshanchakra.mdm.data.*
import java.time.*
import java.time.format.DateTimeFormatter

class TelemetryCollector(private val context: Context) {

    fun collectAppUsage(date: LocalDate): List<MdmAppUsageEntity> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        return stats
            .filter { it.totalTimeInForeground > 60_000 } // > 1 minute
            .map { stat ->
                MdmAppUsageEntity(
                    date = date.toString(),
                    packageName = stat.packageName,
                    appLabel = getAppLabel(stat.packageName),
                    foregroundTimeSec = (stat.totalTimeInForeground / 1000).toInt(),
                    launchCount = if (android.os.Build.VERSION.SDK_INT >= 29) stat.appLaunchCount else 0,
                    category = categorizeApp(stat.packageName),
                )
            }
    }

    fun collectCallLogs(sinceTimestamp: String?): List<MdmCallLogEntity> {
        val since = sinceTimestamp?.let {
            Instant.parse(it).toEpochMilli()
        } ?: (System.currentTimeMillis() - 86_400_000) // Default: last 24h

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
                CallLog.Calls.DATE, CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME
            ),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC"
        ) ?: return emptyList()

        return cursor.use { c ->
            generateSequence { if (c.moveToNext()) c else null }.map {
                val number = c.getString(0) ?: ""
                MdmCallLogEntity(
                    phoneNumberMasked = maskPhoneNumber(number),
                    callType = when (c.getInt(1)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        else -> "incoming"
                    },
                    callTimestamp = Instant.ofEpochMilli(c.getLong(2)).toString(),
                    durationSec = c.getInt(3),
                    contactName = c.getString(4) ?: "",
                )
            }.toList()
        }
    }

    fun collectScreenTime(date: LocalDate): MdmScreenTimeEntity {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return MdmScreenTimeEntity(date = date.toString())

        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val events = usm.queryEvents(start, end)
        var totalForeground = 0L
        var unlocks = 0
        var lastResumed = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> lastResumed = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastResumed > 0) totalForeground += event.timeStamp - lastResumed
                    lastResumed = 0
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }

        return MdmScreenTimeEntity(
            date = date.toString(),
            totalScreenTimeSec = (totalForeground / 1000).toInt(),
            unlockCount = unlocks,
        )
    }

    private fun getAppLabel(packageName: String): String = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) { packageName.substringAfterLast('.') }

    private fun categorizeApp(pkg: String): String = when {
        pkg == "com.sudarshanchakra" -> "sudarshanchakra"
        pkg.contains("whatsapp") -> "social"
        pkg.contains("youtube") -> "entertainment"
        pkg.contains("maps") -> "navigation"
        pkg.contains("dialer") || pkg.contains("phone") -> "communication"
        pkg.contains("camera") -> "camera"
        pkg.contains("chrome") || pkg.contains("browser") -> "browser"
        else -> "other"
    }

    private fun maskPhoneNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 4) "****${digits.takeLast(4)}" else "****"
    }
}
```

## Files to MODIFY

### 1. `android/app/src/main/AndroidManifest.xml`
Add these permissions (they only take effect when Device Owner):
```xml
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
```

## Verification
```bash
cd android && ./gradlew assembleDebug
# Compile should succeed. Runtime testing requires Device Owner or manual permission grant.
```

---

# Story 08: Android WorkManager Telemetry Upload

## Prerequisites
- Story 06 + 07 complete

## Goal
Create a WorkManager periodic worker that collects telemetry into Room DB and batch-uploads to backend every 30 minutes.

## Files to CREATE

### 1. `android/app/src/main/java/com/sudarshanchakra/mdm/TelemetryUploadWorker.kt`
```kotlin
package com.sudarshanchakra.mdm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.db.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class TelemetryUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val api: ApiService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val collector = TelemetryCollector(applicationContext)
        val today = LocalDate.now()

        // 1. Collect fresh data into Room cache
        try {
            val usage = collector.collectAppUsage(today)
            db.mdmAppUsageDao().upsertAll(usage)

            val lastCallTs = db.mdmCallLogDao().getLastTimestamp()
            val calls = collector.collectCallLogs(lastCallTs)
            db.mdmCallLogDao().insertAll(calls)

            val screen = collector.collectScreenTime(today)
            db.mdmScreenTimeDao().upsert(screen)
        } catch (e: Exception) {
            // Collection failed (permission issue?) — continue with upload of cached data
        }

        // 2. Upload unsynced data
        try {
            val unsyncedUsage = db.mdmAppUsageDao().getUnsynced()
            val unsyncedCalls = db.mdmCallLogDao().getUnsynced()
            val unsyncedScreen = db.mdmScreenTimeDao().getUnsynced()

            if (unsyncedUsage.isEmpty() && unsyncedCalls.isEmpty() && unsyncedScreen.isEmpty()) {
                return Result.success()
            }

            // Build batch request (see TelemetryBatchRequest in Story 03)
            // POST to /api/v1/mdm/telemetry/batch
            // If 200 OK → mark all as synced
            // If network error → return Result.retry()

            // Mark synced
            if (unsyncedUsage.isNotEmpty()) db.mdmAppUsageDao().markSynced(unsyncedUsage.map { it.id })
            if (unsyncedCalls.isNotEmpty()) db.mdmCallLogDao().markSynced(unsyncedCalls.map { it.id })
            if (unsyncedScreen.isNotEmpty()) db.mdmScreenTimeDao().markSynced(unsyncedScreen.map { it.id })

            // 3. Cleanup old synced records (older than 7 days)
            val cutoff = today.minusDays(7).toString()
            db.mdmAppUsageDao().cleanOldSynced(cutoff)
            db.mdmCallLogDao().cleanOldSynced(cutoff)
            db.mdmScreenTimeDao().cleanOldSynced(cutoff)

            return Result.success()
        } catch (e: Exception) {
            return Result.retry() // Network down — will retry with backoff
        }
    }
}
```

### 2. `android/app/src/main/java/com/sudarshanchakra/mdm/MdmWorkScheduler.kt`
```kotlin
package com.sudarshanchakra.mdm

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object MdmWorkScheduler {
    fun schedule(context: Context) {
        val uploadWork = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(
            30, TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            10, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "mdm_telemetry_upload",
            ExistingPeriodicWorkPolicy.KEEP,
            uploadWork
        )
    }
}
```

## Files to MODIFY

### 1. `android/app/src/main/java/com/sudarshanchakra/SudarshanChakraApp.kt`
In `onCreate()`, after existing initialization:
```kotlin
// Schedule MDM telemetry if device is managed
MdmWorkScheduler.schedule(this)
```

### 2. `android/app/build.gradle.kts`
Add WorkManager Hilt integration:
```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.0")
implementation("androidx.hilt:hilt-work:1.1.0")
kapt("androidx.hilt:hilt-compiler:1.1.0")
```

## Verification
```bash
cd android && ./gradlew assembleDebug
# Worker should compile. Actual upload test requires backend running.
```

---

## ADDENDUM: Location Tracking (Android)

### New Room Entity: `MdmLocationEntity.kt`
Create at `android/app/src/main/java/com/sudarshanchakra/mdm/data/MdmLocationEntity.kt`:
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mdm_location_cache")
data class MdmLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Float? = null,
    @ColumnInfo(name = "altitude_meters") val altitudeMeters: Float? = null,
    @ColumnInfo(name = "speed_mps") val speedMps: Float? = null,
    val bearing: Float? = null,
    val provider: String? = null,
    @ColumnInfo(name = "battery_percent") val batteryPercent: Int? = null,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,   // ISO 8601
    val synced: Boolean = false,
)
```

### New DAO: `MdmLocationDao.kt`
```kotlin
package com.sudarshanchakra.mdm.data

import androidx.room.*

@Dao
interface MdmLocationDao {
    @Query("SELECT * FROM mdm_location_cache WHERE synced = 0 ORDER BY recorded_at ASC LIMIT 500")
    suspend fun getUnsynced(): List<MdmLocationEntity>

    @Query("UPDATE mdm_location_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MdmLocationEntity)

    @Query("DELETE FROM mdm_location_cache WHERE synced = 1 AND recorded_at < :before")
    suspend fun cleanOldSynced(before: String)

    @Query("SELECT COUNT(*) FROM mdm_location_cache WHERE synced = 0")
    suspend fun unsyncedCount(): Int
}
```

### Update `AppDatabase.kt` migration (2→3 already adds mdm tables, extend it):
Add to MIGRATION_2_3:
```kotlin
db.execSQL("""CREATE TABLE IF NOT EXISTS mdm_location_cache (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    latitude REAL NOT NULL, longitude REAL NOT NULL,
    accuracy_meters REAL, altitude_meters REAL,
    speed_mps REAL, bearing REAL, provider TEXT,
    battery_percent INTEGER,
    recorded_at TEXT NOT NULL, synced INTEGER NOT NULL DEFAULT 0)""")
```

Add `MdmLocationEntity::class` to `@Database(entities=[...])`.
Add `abstract fun mdmLocationDao(): MdmLocationDao` to `AppDatabase`.
Add `@Provides fun provideMdmLocationDao(db: AppDatabase) = db.mdmLocationDao()` to `AppModule.kt`.

### Update `TelemetryCollector.kt` — add location collection:
```kotlin
import android.annotation.SuppressLint
import android.location.LocationManager
import android.os.BatteryManager

@SuppressLint("MissingPermission")  // Permission auto-granted by Device Owner
fun collectCurrentLocation(): MdmLocationEntity? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    
    // Try GPS first, fall back to network
    val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        ?: return null
    
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    return MdmLocationEntity(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMeters = location.accuracy,
        altitudeMeters = if (location.hasAltitude()) location.altitude.toFloat() else null,
        speedMps = if (location.hasSpeed()) location.speed else null,
        bearing = if (location.hasBearing()) location.bearing else null,
        provider = location.provider,
        batteryPercent = batteryPct,
        recordedAt = java.time.Instant.ofEpochMilli(location.time).toString(),
    )
}
```

### New: `LocationTrackingWorker.kt`
Separate periodic worker specifically for location — runs every 1 min (or configurable interval). Stores to Room cache.
```kotlin
package com.sudarshanchakra.mdm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.sudarshanchakra.data.db.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LocationTrackingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val collector = TelemetryCollector(applicationContext)
        val location = collector.collectCurrentLocation() ?: return Result.success()
        db.mdmLocationDao().insert(location)
        return Result.success()
    }
}
```

### Update `MdmWorkScheduler.kt`:
```kotlin
fun scheduleLocationTracking(context: Context, intervalMinutes: Long = 1) {
    // WorkManager minimum interval is 15 min for PeriodicWork.
    // For 1-min tracking, use a repeating OneTimeWork chain or
    // FusedLocationProviderClient requestLocationUpdates in the
    // existing MqttForegroundService.
    
    // RECOMMENDED: Use FusedLocationProviderClient in MqttForegroundService
    // since it already runs as a foreground service. This avoids WorkManager
    // 15-min minimum limitation.
}
```

**IMPORTANT:** WorkManager has a 15-minute minimum for periodic work. For 1-minute / 30-second location tracking, use `FusedLocationProviderClient.requestLocationUpdates()` inside the existing `MqttForegroundService` which already runs as a foreground service. The interval is read from `SharedPreferences` (synced from backend via MQTT SET_POLICY command).

### Update `MqttForegroundService.kt` — add location tracking:
```kotlin
// In connectAndSubscribe(), after MQTT setup:
startLocationTracking()

private fun startLocationTracking() {
    val intervalMs = getLocationIntervalMs()  // From SharedPrefs, default 60000
    val fusedClient = LocationServices.getFusedLocationProviderClient(this)
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
        .setMinUpdateIntervalMillis(intervalMs / 2)
        .build()
    
    fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
}

private val locationCallback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
        val loc = result.lastLocation ?: return
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val entity = MdmLocationEntity(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy,
            altitudeMeters = if (loc.hasAltitude()) loc.altitude.toFloat() else null,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
            bearing = if (loc.hasBearing()) loc.bearing else null,
            provider = loc.provider,
            batteryPercent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            recordedAt = java.time.Instant.ofEpochMilli(loc.time).toString(),
        )
        // Insert to Room on IO thread
        CoroutineScope(Dispatchers.IO).launch {
            db.mdmLocationDao().insert(entity)
        }
    }
}

// When interval changes via MQTT SET_POLICY command:
fun updateLocationInterval(newIntervalMs: Long) {
    fusedClient.removeLocationUpdates(locationCallback)
    // Re-request with new interval
    startLocationTracking()
}
```

### Permissions — add to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

### build.gradle.kts — add Google Play Services Location:
```kotlin
implementation("com.google.android.gms:play-services-location:21.1.0")
```

### Update `TelemetryUploadWorker` — include location in batch:
```kotlin
// In doWork(), after collecting usage/calls/screen:
val unsyncedLocations = db.mdmLocationDao().getUnsynced()

// Add to batch request:
// locations = unsyncedLocations.map { LocationDto(it.latitude, it.longitude, ...) }

// After successful upload:
if (unsyncedLocations.isNotEmpty()) db.mdmLocationDao().markSynced(unsyncedLocations.map { it.id })
```
