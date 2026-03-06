package com.sudarshanchakra.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<AlertEntity>)

    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    suspend fun getAll(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getById(id: String): AlertEntity?

    @Query("DELETE FROM alerts WHERE cachedAt < :threshold")
    suspend fun deleteOld(threshold: Long)

    @Query("DELETE FROM alerts")
    suspend fun deleteAll()
}
