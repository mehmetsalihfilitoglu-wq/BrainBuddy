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
                .addMigrations(BrainBuddyDatabase.MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
