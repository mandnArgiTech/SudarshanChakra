package com.sudarshanchakra.data.api

import com.sudarshanchakra.domain.model.Alert
import com.sudarshanchakra.domain.model.AuthResponse
import com.sudarshanchakra.domain.model.Camera
import com.sudarshanchakra.domain.model.EdgeNode
import com.sudarshanchakra.domain.model.LoginRequest
import com.sudarshanchakra.domain.model.RegisterRequest
import com.sudarshanchakra.domain.model.SirenAction
import com.sudarshanchakra.domain.model.WorkerTag
import com.sudarshanchakra.domain.model.Zone
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    // Alerts
    @GET("alerts")
    suspend fun getAlerts(
        @Query("priority") priority: String? = null,
        @Query("status") status: String? = null
    ): Response<List<Alert>>

    @GET("alerts/{id}")
    suspend fun getAlert(@Path("id") id: String): Response<Alert>

    @PATCH("alerts/{id}/acknowledge")
    suspend fun acknowledgeAlert(@Path("id") id: String): Response<Alert>

    @PATCH("alerts/{id}/resolve")
    suspend fun resolveAlert(@Path("id") id: String): Response<Alert>

    @PATCH("alerts/{id}/false-positive")
    suspend fun markFalsePositive(@Path("id") id: String): Response<Alert>

    // Nodes / Cameras / Zones / Tags
    @GET("nodes")
    suspend fun getNodes(): Response<List<EdgeNode>>

    @GET("cameras")
    suspend fun getCameras(): Response<List<Camera>>

    @GET("zones")
    suspend fun getZones(): Response<List<Zone>>

    @GET("tags")
    suspend fun getTags(): Response<List<WorkerTag>>

    // Siren
    @POST("siren/trigger")
    suspend fun triggerSiren(@Body body: Map<String, String>): Response<SirenAction>

    @POST("siren/stop")
    suspend fun stopSiren(@Body body: Map<String, String>): Response<SirenAction>

    @GET("siren/history")
    suspend fun getSirenHistory(): Response<List<SirenAction>>

    // FCM
    @PATCH("users/me/fcm-token")
    suspend fun updateFcmToken(@Body body: Map<String, String>): Response<Unit>
}
