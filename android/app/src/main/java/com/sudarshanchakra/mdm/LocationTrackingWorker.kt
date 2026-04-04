package com.sudarshanchakra.mdm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sudarshanchakra.data.db.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LocationTrackingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!MdmConfig.isEnabled(applicationContext)) {
            return Result.success()
        }
        val collector = TelemetryCollector(applicationContext)
        val location = collector.collectCurrentLocation() ?: return Result.success()
        db.mdmLocationDao().insert(location)
        return Result.success()
    }
}
