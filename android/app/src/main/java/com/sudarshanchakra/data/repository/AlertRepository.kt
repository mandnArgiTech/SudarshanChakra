package com.sudarshanchakra.data.repository

import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.api.executeApi
import com.sudarshanchakra.data.db.AlertDao
import com.sudarshanchakra.data.db.AlertEntity
import com.sudarshanchakra.domain.model.Alert
import com.sudarshanchakra.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AlertRepository @Inject constructor(
    private val apiService: ApiService,
    private val alertDao: AlertDao,
) {
    suspend fun getAlerts(priority: String? = null, status: String? = null): Result<List<Alert>> {
        return executeApi { apiService.getAlerts(priority, status) }.fold(
            onSuccess = { page ->
                val alerts = page.content
                alertDao.insertAll(alerts.map { AlertEntity.fromAlert(it) })
                cleanOldCache()
                Result.success(alerts)
            },
            onFailure = {
                val cached = alertDao.getAll().map { it.toAlert() }
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(it)
                }
            },
        )
    }

    suspend fun getAlert(id: String): Result<Alert> {
        return executeApi { apiService.getAlert(id) }.fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                val cached = alertDao.getById(id)?.toAlert()
                if (cached != null) {
                    Result.success(cached)
                } else {
                    Result.failure(it)
                }
            },
        )
    }

    suspend fun acknowledgeAlert(id: String): Result<Alert> =
        executeApi { apiService.acknowledgeAlert(id) }

    suspend fun resolveAlert(id: String): Result<Alert> =
        executeApi { apiService.resolveAlert(id) }

    suspend fun markFalsePositive(id: String): Result<Alert> =
        executeApi { apiService.markFalsePositive(id) }

    private suspend fun cleanOldCache() {
        val threshold = System.currentTimeMillis() - (Constants.ALERT_CACHE_MAX_AGE_HOURS * 3600 * 1000)
        alertDao.deleteOld(threshold)
    }
}
