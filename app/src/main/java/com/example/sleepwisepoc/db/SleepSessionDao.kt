package com.example.sleepwisepoc.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SleepSessionDao {

    @Insert
    suspend fun insert(session: SleepSessionEntity): Long

    @Query("UPDATE sleep_sessions SET stages = :stagesJson WHERE id = :id")
    suspend fun updateStages(id: Long, stagesJson: String)

    @Query("UPDATE sleep_sessions SET uploadStatus = 'UPLOADED', endedAt = :endedAt WHERE id = :id")
    suspend fun markUploaded(id: Long, endedAt: String)

    @Query("SELECT * FROM sleep_sessions WHERE uploadStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getById(id: Long): SleepSessionEntity?
}
