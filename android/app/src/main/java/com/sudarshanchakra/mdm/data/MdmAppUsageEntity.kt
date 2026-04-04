package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mdm_app_usage_cache",
    indices = [
        Index(
            name = "idx_mdm_app_usage_date_pkg",
            value = ["date", "package_name"],
            unique = true,
        ),
    ],
)
data class MdmAppUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "app_label") val appLabel: String = "",
    @ColumnInfo(name = "foreground_time_sec") val foregroundTimeSec: Int = 0,
    @ColumnInfo(name = "launch_count") val launchCount: Int = 0,
    val category: String = "",
    val synced: Boolean = false,
)
