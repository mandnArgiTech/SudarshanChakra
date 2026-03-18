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

    // MQTT client ID
    @PATCH("users/me/mqtt-client-id")
    suspend fun updateMqttClientId(@Body body: Map<String, String>): Response<Unit>
}

    // ── Water Tanks ──────────────────────────────────────────────────────────
    @GET("water/tanks")
    suspend fun getWaterTanks(): Response<List<com.sudarshanchakra.domain.model.water.WaterTank>>

    @GET("water/tanks/{id}")
    suspend fun getWaterTank(@Path("id") id: String): Response<com.sudarshanchakra.domain.model.water.WaterTank>

    @GET("water/tanks/{id}/history")
    suspend fun getWaterHistory(
        @Path("id") id: String,
        @Query("hours") hours: Int = 24,
    ): Response<List<Map<String, Any>>>

    // ── Motor Controllers ────────────────────────────────────────────────────
    @GET("water/motors")
    suspend fun getMotors(): Response<List<com.sudarshanchakra.domain.model.water.WaterMotor>>

    @GET("water/motors/{id}")
    suspend fun getMotor(@Path("id") id: String): Response<com.sudarshanchakra.domain.model.water.WaterMotor>

    @POST("water/motors/{id}/command")
    suspend fun sendMotorCommand(
        @Path("id") id: String,
        @Body body: Map<String, String>,
    ): Response<Map<String, String>>

    @PUT("water/motors/{id}")
    suspend fun updateMotor(
        @Path("id") id: String,
        @Body body: Map<String, Any>,
    ): Response<com.sudarshanchakra.domain.model.water.WaterMotor>
