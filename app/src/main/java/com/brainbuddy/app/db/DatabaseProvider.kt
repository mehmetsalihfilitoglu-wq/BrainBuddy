package com.brainbuddy.app.db

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: BrainBuddyDatabase? = null

    fun get(context: Context): BrainBuddyDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BrainBuddyDatabase::class.java,
                "brainbuddy.db"
            )
                .addMigrations(
                    BrainBuddyDatabase.MIGRATION_1_2,
                    BrainBuddyDatabase.MIGRATION_2_3,
                    BrainBuddyDatabase.MIGRATION_3_4,
                    BrainBuddyDatabase.MIGRATION_4_5,
                    BrainBuddyDatabase.MIGRATION_5_6,
                    BrainBuddyDatabase.MIGRATION_6_7,
                    BrainBuddyDatabase.MIGRATION_7_8,
                    BrainBuddyDatabase.MIGRATION_8_9,
                    BrainBuddyDatabase.MIGRATION_9_10,
                    BrainBuddyDatabase.MIGRATION_10_11
                )
                .build().also { instance = it }
        }
    }
}
