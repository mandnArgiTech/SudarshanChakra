package com.sudarshanchakra.mdm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MdmLocationDao {
    @Query("SELECT * FROM mdm_location_cache WHERE synced = 0 ORDER BY recorded_at ASC LIMIT 500")
    suspend fun getUnsynced(): List<MdmLocationEntity>

    @Query("UPDATE mdm_location_cache SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MdmLocationEntity)

    @Query("DELETE FROM mdm_location_cache WHERE synced = 1 AND recorded_at < :before")
    suspend fun cleanOldSynced(before: String)

    @Query("SELECT COUNT(*) FROM mdm_location_cache WHERE synced = 0")
    suspend fun unsyncedCount(): Int
}
