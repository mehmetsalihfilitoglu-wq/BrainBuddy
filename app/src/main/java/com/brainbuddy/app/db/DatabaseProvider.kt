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
                    BrainBuddyDatabase.MIGRATION_5_6
                )
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
