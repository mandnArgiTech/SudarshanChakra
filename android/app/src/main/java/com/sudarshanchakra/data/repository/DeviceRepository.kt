package com.sudarshanchakra.data.repository

import com.sudarshanchakra.data.api.ApiService
import com.sudarshanchakra.domain.model.Camera
import com.sudarshanchakra.domain.model.EdgeNode
import com.sudarshanchakra.domain.model.WorkerTag
import com.sudarshanchakra.domain.model.Zone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getNodes(): Result<List<EdgeNode>> {
        return try {
            val response = apiService.getNodes()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch nodes: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCameras(): Result<List<Camera>> {
        return try {
            val response = apiService.getCameras()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch cameras: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getZones(): Result<List<Zone>> {
        return try {
            val response = apiService.getZones()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch zones: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTags(): Result<List<WorkerTag>> {
        return try {
            val response = apiService.getTags()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch tags: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
