package com.sudarshanchakra.data.repository

import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.domain.model.SirenAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SirenRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun triggerSiren(nodeId: String, reason: String? = null): Result<SirenAction> {
        return try {
            val body = mutableMapOf("nodeId" to nodeId)
            if (reason != null) body["reason"] = reason
            val response = apiService.triggerSiren(body)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to trigger siren: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopSiren(nodeId: String): Result<SirenAction> {
        return try {
            val response = apiService.stopSiren(mapOf("nodeId" to nodeId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to stop siren: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(): Result<List<SirenAction>> {
        return try {
            val response = apiService.getSirenHistory()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch siren history: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
