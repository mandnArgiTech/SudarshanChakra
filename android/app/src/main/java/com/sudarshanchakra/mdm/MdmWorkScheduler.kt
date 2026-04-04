package com.sudarshanchakra.mdm

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MdmWorkScheduler {

    private const val TELEMETRY_WORK_NAME = "mdm_telemetry_upload"
    private const val LOCATION_WORK_NAME = "mdm_location_tracking"
    private const val TELEMETRY_SYNC_NOW_NAME = "mdm_telemetry_sync_now"

    fun schedule(context: Context) {
        val telemetryRequest = PeriodicWorkRequestBuilder<TelemetryUploadWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TELEMETRY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            telemetryRequest,
        )

        val locationRequest = PeriodicWorkRequestBuilder<LocationTrackingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LOCATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            locationRequest,
        )
    }

    fun scheduleSyncNow(context: Context) {
        val syncRequest = OneTimeWorkRequestBuilder<TelemetryUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TELEMETRY_SYNC_NOW_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest,
        )
    }
}
