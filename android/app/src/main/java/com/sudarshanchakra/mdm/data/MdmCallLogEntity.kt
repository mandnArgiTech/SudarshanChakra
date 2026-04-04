package com.sudarshanchakra.mdm.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mdm_call_log_cache",
    indices = [
        Index(
            name = "idx_mdm_call_log_natural",
            value = ["call_timestamp", "phone_number_masked", "call_type"],
            unique = true,
        ),
    ],
)
data class MdmCallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "phone_number_masked") val phoneNumberMasked: String = "",
    @ColumnInfo(name = "call_type") val callType: String,
    @ColumnInfo(name = "call_timestamp") val callTimestamp: String,
    @ColumnInfo(name = "duration_sec") val durationSec: Int = 0,
    @ColumnInfo(name = "contact_name") val contactName: String = "",
    val synced: Boolean = false,
)
