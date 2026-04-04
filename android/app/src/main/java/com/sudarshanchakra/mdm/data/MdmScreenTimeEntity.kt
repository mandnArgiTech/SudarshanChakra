package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mdm_screen_time_cache",
    indices = [Index(name = "idx_mdm_screen_time_date", value = ["date"], unique = true)],
)
data class MdmScreenTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "total_screen_time_sec") val totalScreenTimeSec: Int = 0,
    @ColumnInfo(name = "unlock_count") val unlockCount: Int = 0,
    val synced: Boolean = false,
)
