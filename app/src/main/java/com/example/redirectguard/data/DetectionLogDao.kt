package com.example.redirectguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionLogDao {
    @Insert
    suspend fun insert(log: DetectionLog): Long

    @Update
    suspend fun update(log: DetectionLog)

    @Query("SELECT * FROM detection_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DetectionLog>>

    @Query("SELECT COUNT(*) FROM detection_logs")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM detection_logs WHERE falsePositive = 1")
    suspend fun countFalsePositives(): Int

    @Query("DELETE FROM detection_logs")
    suspend fun clearAll()
}
