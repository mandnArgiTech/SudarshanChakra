package com.sudarshanchakra.mdm.api

/**
 * JSON body for POST /api/v1/mdm/telemetry/batch (matches backend TelemetryBatchRequest).
 */
data class TelemetryAppUsageJson(
    val date: String,
    val packageName: String,
    val appLabel: String = "",
    val foregroundTimeSec: Int = 0,
    val launchCount: Int = 0,
    val category: String = "",
)

data class TelemetryCallLogJson(
    val phoneNumberMasked: String? = null,
    val callType: String,
    val callTimestamp: String,
    val durationSec: Int = 0,
    val contactName: String? = null,
)

data class TelemetryScreenTimeJson(
    val date: String,
    val totalScreenTimeSec: Int,
    val unlockCount: Int,
)

data class TelemetryLocationJson(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Float? = null,
    val speedMps: Float? = null,
    val bearing: Float? = null,
    val provider: String? = null,
    val batteryPercent: Int? = null,
    val recordedAt: String,
)

data class TelemetryBatchRequestDto(
    val androidId: String,
    val appVersion: String? = null,
    val deviceId: String? = null,
    val appUsage: List<TelemetryAppUsageJson>? = null,
    val callLogs: List<TelemetryCallLogJson>? = null,
    val screenTime: TelemetryScreenTimeJson? = null,
    val locations: List<TelemetryLocationJson>? = null,
)
