package com.sudarshanchakra.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sudarshanchakra.domain.model.Alert
import com.sudarshanchakra.domain.model.AlertPriority
import com.sudarshanchakra.domain.model.AlertStatus

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey val id: String,
    val nodeId: String,
    val cameraId: String,
    val zoneId: String,
    val zoneName: String,
    val zoneType: String,
    val priority: String,
    val detectionClass: String,
    val confidence: Float,
    val snapshotUrl: String?,
    val status: String,
    val createdAt: String,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toAlert(): Alert = Alert(
        id = id,
        nodeId = nodeId.ifBlank { null },
        cameraId = cameraId.ifBlank { null },
        zoneId = zoneId.ifBlank { null },
        zoneName = zoneName.ifBlank { null },
        zoneType = zoneType.ifBlank { null },
        priority = try { AlertPriority.valueOf(priority) } catch (_: Exception) { AlertPriority.LOW },
        detectionClass = detectionClass.ifBlank { null },
        confidence = confidence,
        snapshotUrl = snapshotUrl,
        status = try { AlertStatus.valueOf(status) } catch (_: Exception) { AlertStatus.ACTIVE },
        createdAt = createdAt.ifBlank { null },
    )

    companion object {
        fun fromAlert(alert: Alert): AlertEntity = AlertEntity(
            id = alert.id,
            nodeId = alert.nodeId.orEmpty(),
            cameraId = alert.cameraId.orEmpty(),
            zoneId = alert.zoneId.orEmpty(),
            zoneName = alert.zoneName.orEmpty(),
            zoneType = alert.zoneType.orEmpty(),
            priority = alert.priority.name,
            detectionClass = alert.detectionClass.orEmpty(),
            confidence = alert.confidence ?: 0f,
            snapshotUrl = alert.snapshotUrl,
            status = alert.status.name,
            createdAt = alert.createdAt.orEmpty(),
        )
    }
}
