package com.sudarshanchakra.data.repository

import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.api.executeApi
import com.sudarshanchakra.domain.model.SirenAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SirenRepository @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun triggerSiren(nodeId: String, reason: String? = null): Result<SirenAction> {
        val body = buildMap {
            put("nodeId", nodeId)
            if (reason != null) put("reason", reason)
        }
        return executeApi { apiService.triggerSiren(body) }
    }

    suspend fun stopSiren(nodeId: String): Result<SirenAction> =
        executeApi { apiService.stopSiren(mapOf("nodeId" to nodeId)) }

    suspend fun getHistory(): Result<List<SirenAction>> =
        executeApi { apiService.getSirenHistory() }
}
