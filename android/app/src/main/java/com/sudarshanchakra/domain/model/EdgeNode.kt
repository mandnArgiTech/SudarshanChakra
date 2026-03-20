package com.sudarshanchakra.domain.model

import com.google.gson.annotations.SerializedName

/** Matches device-service [EdgeNode] JSON (displayName, vpnIp, …). */
data class EdgeNode(
    @SerializedName("id") val id: String = "",
    @SerializedName("displayName") val name: String = "",
    @SerializedName("vpnIp") val vpnIp: String? = null,
    @SerializedName("localIp") val localIp: String? = null,
    val status: NodeStatus = NodeStatus.UNKNOWN,
    @SerializedName("lastHeartbeat") val lastHeartbeat: String? = null,
    /** Not on entity; reserved for future API — default 0 */
    val cameraCount: Int = 0,
    val zoneCount: Int = 0,
) {
    val location: String get() = vpnIp?.takeIf { it.isNotBlank() } ?: localIp?.takeIf { it.isNotBlank() } ?: ""
}

enum class NodeStatus {
    @SerializedName("online") ONLINE,
    @SerializedName("offline") OFFLINE,
    @SerializedName("degraded") DEGRADED,
    @SerializedName("unknown") UNKNOWN,
}

data class Camera(
    val id: String,
    val nodeId: String,
    val name: String,
    val rtspUrl: String,
    val status: String,
    val resolution: String?
)

data class Zone(
    val id: String,
    val nodeId: String,
    val name: String,
    val type: String,
    val cameraId: String
)

data class WorkerTag(
    val id: String,
    val name: String,
    val description: String?
)
