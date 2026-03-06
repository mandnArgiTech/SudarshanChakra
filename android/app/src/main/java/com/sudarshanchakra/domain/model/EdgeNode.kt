package com.sudarshanchakra.domain.model

data class EdgeNode(
    val id: String,
    val name: String,
    val location: String,
    val status: NodeStatus,
    val lastHeartbeat: String?,
    val cameraCount: Int,
    val zoneCount: Int
)

enum class NodeStatus {
    ONLINE, OFFLINE, DEGRADED
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
