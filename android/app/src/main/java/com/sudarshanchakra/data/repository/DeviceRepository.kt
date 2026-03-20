package com.sudarshanchakra.data.repository

import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.data.api.executeApi
import com.sudarshanchakra.domain.model.Camera
import com.sudarshanchakra.domain.model.EdgeNode
import com.sudarshanchakra.domain.model.WorkerTag
import com.sudarshanchakra.domain.model.Zone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun getNodes(): Result<List<EdgeNode>> =
        executeApi { apiService.getNodes() }

    suspend fun getCameras(): Result<List<Camera>> =
        executeApi { apiService.getCameras() }

    suspend fun getZones(): Result<List<Zone>> =
        executeApi { apiService.getZones() }

    suspend fun getTags(): Result<List<WorkerTag>> =
        executeApi { apiService.getTags() }
}
