package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val inputText: String,
    val actionDetected: String,
    val parameterExtracted: String,
    val responseReply: String,
    val status: String, // "SUCCESS", "FAILED", "PENDING", "PROCESSING"
    val timestamp: Long = System.currentTimeMillis()
)
