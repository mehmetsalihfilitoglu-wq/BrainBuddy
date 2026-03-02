package com.brainbuddy.app.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionEntity::class,
        QuestionHistoryEntity::class,
        TestSnapshotEntity::class,
        AppMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BrainBuddyDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun historyDao(): HistoryDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun appMetaDao(): AppMetaDao
}
