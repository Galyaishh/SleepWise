package com.example.sleepwisepoc.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SleepSessionEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SleepWiseDatabase : RoomDatabase() {

    abstract fun sessionDao(): SleepSessionDao

    companion object {
        @Volatile private var INSTANCE: SleepWiseDatabase? = null

        fun get(context: Context): SleepWiseDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                SleepWiseDatabase::class.java,
                "sleepwise.db",
            ).build().also { INSTANCE = it }
        }
    }
}
