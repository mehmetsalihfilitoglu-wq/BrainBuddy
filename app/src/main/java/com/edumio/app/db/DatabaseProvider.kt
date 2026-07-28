package com.edumio.app.db

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.edumio.app.BuildConfig

object DatabaseProvider {
    private const val TAG = "DatabaseProvider"

    @Volatile
    private var instance: EdumioDatabase? = null

    fun get(context: Context): EdumioDatabase {
        return instance ?: synchronized(this) {
            instance ?: run {
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    EdumioDatabase::class.java,
                    "edumio.db"
                )
                    .addMigrations(
                        EdumioDatabase.MIGRATION_11_12,
                        EdumioDatabase.MIGRATION_12_13,
                        EdumioDatabase.MIGRATION_13_14,
                        EdumioDatabase.MIGRATION_14_15,
                        EdumioDatabase.MIGRATION_15_16,
                        EdumioDatabase.MIGRATION_16_17,
                        EdumioDatabase.MIGRATION_17_18,
                        EdumioDatabase.MIGRATION_18_19,
                        EdumioDatabase.MIGRATION_19_20,
                        EdumioDatabase.MIGRATION_20_21,
                        EdumioDatabase.MIGRATION_21_22,
                        EdumioDatabase.MIGRATION_22_23,
                        EdumioDatabase.MIGRATION_23_24
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
