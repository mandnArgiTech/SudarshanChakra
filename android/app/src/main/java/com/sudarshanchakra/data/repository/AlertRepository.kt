package com.sudarshanchakra.data.repository

import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.db.AlertDao
import com.sudarshanchakra.data.db.AlertEntity
import com.sudarshanchakra.domain.model.Alert
import com.sudarshanchakra.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val apiService: ApiService,
    private val alertDao: AlertDao
) {
    suspend fun getAlerts(priority: String? = null, status: String? = null): Result<List<Alert>> {
        return try {
            val response = apiService.getAlerts(priority, status)
            if (response.isSuccessful && response.body() != null) {
                val alerts = response.body()!!
                alertDao.insertAll(alerts.map { AlertEntity.fromAlert(it) })
                cleanOldCache()
                Result.success(alerts)
            } else {
                val cached = alertDao.getAll().map { it.toAlert() }
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(Exception("Failed to fetch alerts: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            val cached = alertDao.getAll().map { it.toAlert() }
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getAlert(id: String): Result<Alert> {
        return try {
            val response = apiService.getAlert(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val cached = alertDao.getById(id)?.toAlert()
                if (cached != null) Result.success(cached)
                else Result.failure(Exception("Alert not found"))
            }
        } catch (e: Exception) {
            val cached = alertDao.getById(id)?.toAlert()
            if (cached != null) Result.success(cached)
            else Result.failure(e)
        }
    }

    suspend fun acknowledgeAlert(id: String): Result<Alert> {
        return try {
            val response = apiService.acknowledgeAlert(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to acknowledge alert"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveAlert(id: String): Result<Alert> {
        return try {
            val response = apiService.resolveAlert(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to resolve alert"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markFalsePositive(id: String): Result<Alert> {
        return try {
            val response = apiService.markFalsePositive(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to mark as false positive"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun cleanOldCache() {
        val threshold = System.currentTimeMillis() - (Constants.ALERT_CACHE_MAX_AGE_HOURS * 3600 * 1000)
        alertDao.deleteOld(threshold)
    }
}
