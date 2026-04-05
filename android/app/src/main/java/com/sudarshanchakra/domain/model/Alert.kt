package com.sudarshanchakra.domain.model

import com.google.gson.annotations.SerializedName
import org.json.JSONObject

data class Alert(
    val id: String = "",
    val nodeId: String? = null,
    val cameraId: String? = null,
    val zoneId: String? = null,
    val zoneName: String? = null,
    val zoneType: String? = null,
    val priority: AlertPriority = AlertPriority.LOW,
    val detectionClass: String? = null,
    val confidence: Float? = null,
    val snapshotUrl: String? = null,
    /** JSON string from alert-service; may include `clip_path` for edge-recorded MP4 evidence. */
    val metadata: String? = null,
    val status: AlertStatus = AlertStatus.ACTIVE,
    val createdAt: String? = null,
)

/** Non-empty `clip_path` from [metadata] JSON (edge evidence), or null if absent/invalid. */
fun Alert.clipPathFromMetadata(): String? {
    if (metadata.isNullOrBlank()) return null
    return try {
        JSONObject(metadata).optString("clip_path").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

fun Alert.hasClipEvidence(): Boolean = clipPathFromMetadata() != null

enum class AlertPriority {
    @SerializedName("critical") CRITICAL,
    @SerializedName("high") HIGH,
    @SerializedName("warning") WARNING,
    @SerializedName("low") LOW,
}

/** Backend uses string values: new, acknowledged, resolved, false_positive */
enum class AlertStatus {
    @SerializedName("new") ACTIVE,
    @SerializedName("acknowledged") ACKNOWLEDGED,
    @SerializedName("resolved") RESOLVED,
    @SerializedName("false_positive") FALSE_POSITIVE,
}
