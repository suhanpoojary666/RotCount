package com.suhan.reelcounter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// CRITICAL: Never bump the database version without adding an explicit Migration.
// Version 2 is the baseline schema. Destructive migration is only permitted from Version 1.
@Database(entities = [HourlyCountEntity::class], version = 2, exportSchema = true)
abstract class ReelDatabase : RoomDatabase() {

    abstract fun hourlyCountDao(): HourlyCountDao

    companion object {
        @Volatile
        private var INSTANCE: ReelDatabase? = null

        fun getInstance(context: Context): ReelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReelDatabase::class.java,
                    "rotcount.db"
                ).fallbackToDestructiveMigrationFrom(1)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
