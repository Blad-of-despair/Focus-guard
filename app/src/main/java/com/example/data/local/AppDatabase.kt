package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AppRestriction
import com.example.data.model.BlockEvent
import com.example.data.model.DetoxPlan

@Database(
    entities = [AppRestriction::class, DetoxPlan::class, BlockEvent::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun restrictionDao(): RestrictionDao
    abstract fun detoxPlanDao(): DetoxPlanDao
    abstract fun blockEventDao(): BlockEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusguard_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
