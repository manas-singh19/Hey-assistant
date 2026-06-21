package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CommandLog::class], version = 1, exportSchema = false)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun commandDao(): CommandDao

    companion object {
        @Volatile
        private var INSTANCE: AssistantDatabase? = null

        fun getDatabase(context: Context): AssistantDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AssistantDatabase::class.java,
                    "assistant_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
