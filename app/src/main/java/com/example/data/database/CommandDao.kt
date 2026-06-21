package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLog): Long

    @Query("DELETE FROM command_logs")
    suspend fun clearHistory()

    @Query("DELETE FROM command_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)
}
