package com.brainbuddy.app.db

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.brainbuddy.app.BuildConfig

object DatabaseProvider {
    private const val TAG = "DatabaseProvider"

    @Volatile
    private var instance: BrainBuddyDatabase? = null

    fun get(context: Context): BrainBuddyDatabase {
        return instance ?: synchronized(this) {
            instance ?: run {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    BrainBuddyDatabase::class.java,
                    "brainbuddy.db"
                )
                    .addMigrations(
                        BrainBuddyDatabase.MIGRATION_11_12,
                        BrainBuddyDatabase.MIGRATION_12_13,
                        BrainBuddyDatabase.MIGRATION_13_14,
                        BrainBuddyDatabase.MIGRATION_14_15,
                        BrainBuddyDatabase.MIGRATION_15_16,
                        BrainBuddyDatabase.MIGRATION_16_17,
                        BrainBuddyDatabase.MIGRATION_17_18,
                        BrainBuddyDatabase.MIGRATION_18_19
                    )
                if (BuildConfig.DEBUG) {
                    builder.fallbackToDestructiveMigration()
                    Log.i(TAG, "DEBUG: fallbackToDestructiveMigration is ENABLED – schema changes will wipe DB on mismatch.")
                }
                builder.build().also { db ->
                    instance = db
                }
            }
        }
    }
}
