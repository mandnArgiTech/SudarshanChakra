package com.sudarshanchakra.domain.model.water

import com.google.gson.annotations.SerializedName

data class WaterTank(
    @SerializedName("id")           val id: String           = "",
    @SerializedName("displayName")  val displayName: String  = "",
    @SerializedName("deviceTag")    val deviceTag: String?   = null,
    @SerializedName("location")     val location: String     = "",   // "farm" | "home"
    @SerializedName("status")       val status: String       = "unknown",
    @SerializedName("capacityLiters") val capacityLiters: Double? = null,
    @SerializedName("lowThresholdPercent")      val lowThresholdPercent: Double      = 20.0,
    @SerializedName("criticalThresholdPercent") val criticalThresholdPercent: Double = 10.0,
    @SerializedName("overflowThresholdPercent") val overflowThresholdPercent: Double = 95.0,
    @SerializedName("currentLevel") val currentLevel: CurrentLevel? = null,
    @SerializedName("linkedMotorId") val linkedMotorId: String?    = null,
) {
    data class CurrentLevel(
        @SerializedName("percentFilled")   val percentFilled: Double  = 0.0,
        @SerializedName("volumeLiters")    val volumeLiters: Double?  = null,
        @SerializedName("waterHeightCm")   val waterHeightCm: Double? = null,
        @SerializedName("temperatureC")    val temperatureC: Double?  = null,
        @SerializedName("state")           val state: String          = "unknown",
        @SerializedName("lastReadingAt")   val lastReadingAt: String? = null,
    )
    @SerializedName("battery") val battery: BatteryStatus?    = null,
) {
    data class BatteryStatus(
        @SerializedName("voltage") val voltage: Double = 0.0,
        @SerializedName("percent") val percent: Int    = 0,
        @SerializedName("state")   val state: String   = "unknown",
    )
    val isOnline get() = status == "online"
    val levelPercent get() = currentLevel?.percentFilled ?: 0.0
    val levelState get() = when {
        !isOnline                             -> LevelState.OFFLINE
        levelPercent <= criticalThresholdPercent -> LevelState.CRITICAL
        levelPercent <= lowThresholdPercent      -> LevelState.LOW
        levelPercent >= overflowThresholdPercent -> LevelState.OVERFLOW
        else                                  -> LevelState.NORMAL
    }
}

enum class LevelState { OFFLINE, CRITICAL, LOW, NORMAL, OVERFLOW }

data class WaterMotor(
    @SerializedName("id")            val id: String          = "",
    @SerializedName("displayName")   val displayName: String = "",
    @SerializedName("location")      val location: String    = "",
    @SerializedName("controlType")   val controlType: String = "relay", // "relay" | "sms"
    @SerializedName("state")         val state: String       = "stopped",
    @SerializedName("mode")          val mode: String        = "auto",
    @SerializedName("runSeconds")    val runSeconds: Int     = 0,
    @SerializedName("status")        val status: String      = "unknown",
    @SerializedName("autoMode")      val autoMode: Boolean   = true,
    @SerializedName("pumpOnPercent") val pumpOnPercent: Double  = 20.0,
    @SerializedName("pumpOffPercent")val pumpOffPercent: Double = 85.0,
    @SerializedName("maxRunMinutes") val maxRunMinutes: Int  = 30,
    @SerializedName("gsmTargetPhone") val gsmTargetPhone: String? = null,
    @SerializedName("gsmOnMessage")  val gsmOnMessage: String?  = null,
    @SerializedName("gsmOffMessage") val gsmOffMessage: String? = null,
) {
    val isRunning get() = state == "running"
    val isSms     get() = controlType == "sms"
}
