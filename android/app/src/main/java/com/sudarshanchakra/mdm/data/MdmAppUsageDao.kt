package com.sudarshanchakra.mdm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MdmAppUsageDao {
    @Query("SELECT * FROM mdm_app_usage_cache WHERE synced = 0 ORDER BY date DESC LIMIT 500")
    suspend fun getUnsynced(): List<MdmAppUsageEntity>

    @Query("UPDATE mdm_app_usage_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MdmAppUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MdmAppUsageEntity>)

    @Query("DELETE FROM mdm_app_usage_cache WHERE synced = 1 AND date < :beforeDate")
    suspend fun cleanOldSynced(beforeDate: String)
}
