package com.sudarshanchakra.mdm

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.provider.CallLog
import com.sudarshanchakra.mdm.data.MdmAppUsageEntity
import com.sudarshanchakra.mdm.data.MdmCallLogEntity
import com.sudarshanchakra.mdm.data.MdmLocationEntity
import com.sudarshanchakra.mdm.data.MdmScreenTimeEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TelemetryCollector(private val context: Context) {

    fun collectAppUsage(date: LocalDate): List<MdmAppUsageEntity> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        return stats
            .filter { it.totalTimeInForeground > 60_000 }
            .map { stat ->
                val launchCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    stat.appLaunchCount
                } else {
                    0
                }
                MdmAppUsageEntity(
                    date = date.toString(),
                    packageName = stat.packageName,
                    appLabel = getAppLabel(stat.packageName),
                    foregroundTimeSec = (stat.totalTimeInForeground / 1000).toInt(),
                    launchCount = launchCount,
                    category = categorizeApp(stat.packageName),
                )
            }
    }

    fun collectCallLogs(sinceTimestamp: String?): List<MdmCallLogEntity> {
        val since = sinceTimestamp?.let {
            Instant.parse(it).toEpochMilli()
        } ?: (System.currentTimeMillis() - 86_400_000)

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME,
            ),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            "${CallLog.Calls.DATE} DESC",
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

    @SuppressLint("MissingPermission")
    fun collectCurrentLocation(): MdmLocationEntity? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val location = try {
            @Suppress("DEPRECATION")
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        } ?: return null

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
            recordedAt = Instant.ofEpochMilli(location.time).toString(),
        )
    }

    private fun getAppLabel(packageName: String): String = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName.substringAfterLast('.')
    }

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
