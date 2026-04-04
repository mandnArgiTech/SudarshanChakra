package com.sudarshanchakra.mdm

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sudarshanchakra.BuildConfig
import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.db.AppDatabase
import com.sudarshanchakra.mdm.api.TelemetryAppUsageJson
import com.sudarshanchakra.mdm.api.TelemetryBatchRequestDto
import com.sudarshanchakra.mdm.api.TelemetryCallLogJson
import com.sudarshanchakra.mdm.api.TelemetryLocationJson
import com.sudarshanchakra.mdm.api.TelemetryScreenTimeJson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@HiltWorker
class TelemetryUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val api: ApiService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!MdmConfig.isEnabled(applicationContext)) {
            return Result.success()
        }

        val today = LocalDate.now()
        val collector = TelemetryCollector(applicationContext)

        try {
            val usage = collector.collectAppUsage(today)
            if (usage.isNotEmpty()) {
                db.mdmAppUsageDao().upsertAll(usage)
            }

            val lastCallTs = db.mdmCallLogDao().getLastTimestamp()
            val calls = collector.collectCallLogs(lastCallTs)
            if (calls.isNotEmpty()) {
                db.mdmCallLogDao().insertAll(calls)
            }

            val screen = collector.collectScreenTime(today)
            db.mdmScreenTimeDao().upsert(screen)
        } catch (_: Exception) {
            // Permission or OS issue — still try to upload cached rows
        }

        val androidId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        if (androidId.isNullOrBlank()) {
            return Result.success()
        }

        return try {
            uploadAllPendingBatches(androidId)
            val cutoffDate = today.minusDays(7).toString()
            val callCutoff = Instant.now().minus(7, ChronoUnit.DAYS).toString()
            db.mdmAppUsageDao().cleanOldSynced(cutoffDate)
            db.mdmCallLogDao().cleanOldSynced(callCutoff)
            db.mdmScreenTimeDao().cleanOldSynced(cutoffDate)
            db.mdmLocationDao().cleanOldSynced(callCutoff)
            postHeartbeatBestEffort(androidId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Telemetry upload transient failure", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Telemetry upload failed (non-retryable)", e)
            Result.failure()
        }
    }

    private suspend fun postHeartbeatBestEffort(androidId: String) {
        try {
            api.postMdmTelemetryHeartbeat(mapOf("android_id" to androidId))
        } catch (e: Exception) {
            Log.w(TAG, "MDM heartbeat failed (ignored)", e)
        }
    }

    private suspend fun uploadAllPendingBatches(androidId: String) {
        while (true) {
            val usage = db.mdmAppUsageDao().getUnsynced()
            val calls = db.mdmCallLogDao().getUnsynced()
            val screens = db.mdmScreenTimeDao().getUnsynced()
            val locs = db.mdmLocationDao().getUnsynced()

            if (usage.isEmpty() && calls.isEmpty() && screens.isEmpty() && locs.isEmpty()) {
                break
            }

            val screenEntity = screens.firstOrNull()
            val body = TelemetryBatchRequestDto(
                androidId = androidId,
                appVersion = BuildConfig.VERSION_NAME,
                appUsage = if (usage.isNotEmpty()) {
                    usage.map {
                        TelemetryAppUsageJson(
                            date = it.date,
                            packageName = it.packageName,
                            appLabel = it.appLabel,
                            foregroundTimeSec = it.foregroundTimeSec,
                            launchCount = it.launchCount,
                            category = it.category,
                        )
                    }
                } else {
                    null
                },
                callLogs = if (calls.isNotEmpty()) {
                    calls.map {
                        TelemetryCallLogJson(
                            phoneNumberMasked = it.phoneNumberMasked.ifBlank { null },
                            callType = it.callType,
                            callTimestamp = it.callTimestamp,
                            durationSec = it.durationSec,
                            contactName = it.contactName.ifBlank { null },
                        )
                    }
                } else {
                    null
                },
                screenTime = screenEntity?.let {
                    TelemetryScreenTimeJson(
                        date = it.date,
                        totalScreenTimeSec = it.totalScreenTimeSec,
                        unlockCount = it.unlockCount,
                    )
                },
                locations = if (locs.isNotEmpty()) {
                    locs.map {
                        TelemetryLocationJson(
                            latitude = it.latitude,
                            longitude = it.longitude,
                            accuracyMeters = it.accuracyMeters,
                            altitudeMeters = it.altitudeMeters,
                            speedMps = it.speedMps,
                            bearing = it.bearing,
                            provider = it.provider,
                            batteryPercent = it.batteryPercent,
                            recordedAt = it.recordedAt,
                        )
                    }
                } else {
                    null
                },
            )

            val response = api.uploadMdmTelemetryBatch(body)
            if (!response.isSuccessful) {
                val code = response.code()
                if (code in 500..599 || code == 429 || code == 408) {
                    throw IOException("HTTP $code")
                }
                return
            }

            if (usage.isNotEmpty()) {
                db.mdmAppUsageDao().markSynced(usage.map { it.id })
            }
            if (calls.isNotEmpty()) {
                db.mdmCallLogDao().markSynced(calls.map { it.id })
            }
            if (locs.isNotEmpty()) {
                db.mdmLocationDao().markSynced(locs.map { it.id })
            }
            if (screenEntity != null) {
                db.mdmScreenTimeDao().markSynced(listOf(screenEntity.id))
            }
        }
    }

    companion object {
        private const val TAG = "TelemetryUploadWorker"
    }
}
