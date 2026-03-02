package com.brainbuddy.app.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuestionEntity::class,
        QuestionHistoryEntity::class,
        TestSnapshotEntity::class,
        AppMetaEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class BrainBuddyDatabase : RoomDatabase() {

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE test_snapshots ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default'")
            }
        }
    }
    abstract fun questionDao(): QuestionDao
    abstract fun historyDao(): HistoryDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun appMetaDao(): AppMetaDao
}
