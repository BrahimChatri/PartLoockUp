package com.example.partlookup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Part::class], version = 2, exportSchema = false)
abstract class PartDatabase : RoomDatabase() {
    abstract fun partDao(): PartDao

    companion object {
        @Volatile
        private var INSTANCE: PartDatabase? = null

        fun getDatabase(context: Context): PartDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PartDatabase::class.java,
                    "part_database"
                )
                .fallbackToDestructiveMigration() // This will recreate tables if schema changes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 