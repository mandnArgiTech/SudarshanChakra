package com.sudarshanchakra.domain.model

data class Alert(
    val id: String,
    val nodeId: String,
    val cameraId: String,
    val zoneId: String,
    val zoneName: String,
    val zoneType: String,
    val priority: AlertPriority,
    val detectionClass: String,
    val confidence: Float,
    val snapshotUrl: String?,
    val status: AlertStatus,
    val createdAt: String
)

enum class AlertPriority {
    CRITICAL, HIGH, WARNING, LOW
}

enum class AlertStatus {
    ACTIVE, ACKNOWLEDGED, RESOLVED, FALSE_POSITIVE
}
