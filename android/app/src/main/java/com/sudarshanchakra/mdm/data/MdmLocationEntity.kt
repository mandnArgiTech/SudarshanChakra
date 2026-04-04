package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mdm_location_cache")
data class MdmLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_meters") val accuracyMeters: Float? = null,
    @ColumnInfo(name = "altitude_meters") val altitudeMeters: Float? = null,
    @ColumnInfo(name = "speed_mps") val speedMps: Float? = null,
    val bearing: Float? = null,
    val provider: String? = null,
    @ColumnInfo(name = "battery_percent") val batteryPercent: Int? = null,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    val synced: Boolean = false,
)
