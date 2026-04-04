package com.sudarshanchakra.mdm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MdmScreenTimeDao {
    @Query("SELECT * FROM mdm_screen_time_cache WHERE synced = 0 LIMIT 30")
    suspend fun getUnsynced(): List<MdmScreenTimeEntity>

    @Query("UPDATE mdm_screen_time_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MdmScreenTimeEntity)

    @Query("DELETE FROM mdm_screen_time_cache WHERE synced = 1 AND date < :beforeDate")
    suspend fun cleanOldSynced(beforeDate: String)
}
