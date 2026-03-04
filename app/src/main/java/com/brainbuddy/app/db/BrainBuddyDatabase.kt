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
        AppMetaEntity::class,
        WrongAnswerEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BrainBuddyDatabase : RoomDatabase() {

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE test_snapshots ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS wrong_answers (
                        testId TEXT NOT NULL,
                        questionId TEXT NOT NULL,
                        isUnlocked INTEGER NOT NULL DEFAULT 0,
                        unlockedAt INTEGER,
                        PRIMARY KEY (testId, questionId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_wrong_answers_test ON wrong_answers(testId)")
                // Backfill from existing test_snapshots
                val c = db.query("SELECT testId, wrongQuestionIdsJson FROM test_snapshots WHERE wrongQuestionIdsJson IS NOT NULL AND wrongQuestionIdsJson != '[]'")
                try {
                    while (c.moveToNext()) {
                        val testId = c.getString(0) ?: continue
                        val json = c.getString(1) ?: continue
                        try {
                            val arr = org.json.JSONArray(json)
                            for (i in 0 until arr.length()) {
                                val qId = arr.optString(i, "")
                                if (qId.isNotBlank()) {
                                    db.execSQL(
                                        "INSERT OR IGNORE INTO wrong_answers (testId, questionId, isUnlocked, unlockedAt) VALUES (?, ?, 0, NULL)",
                                        arrayOf(testId, qId)
                                    )
                                }
                            }
                        } catch (_: Exception) { }
                    }
                } finally {
                    c.close()
                }
            }
        }
    }
    abstract fun questionDao(): QuestionDao
    abstract fun wrongAnswerDao(): WrongAnswerDao
    abstract fun historyDao(): HistoryDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun appMetaDao(): AppMetaDao
}
