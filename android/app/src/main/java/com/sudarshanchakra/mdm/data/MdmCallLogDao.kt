package com.sudarshanchakra.mdm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MdmCallLogDao {
    @Query("SELECT * FROM mdm_call_log_cache WHERE synced = 0 ORDER BY call_timestamp DESC LIMIT 500")
    suspend fun getUnsynced(): List<MdmCallLogEntity>

    @Query("UPDATE mdm_call_log_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: MdmCallLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MdmCallLogEntity>)

    @Query("SELECT MAX(call_timestamp) FROM mdm_call_log_cache")
    suspend fun getLastTimestamp(): String?

    @Query("DELETE FROM mdm_call_log_cache WHERE synced = 1 AND call_timestamp < :before")
    suspend fun cleanOldSynced(before: String)
}
