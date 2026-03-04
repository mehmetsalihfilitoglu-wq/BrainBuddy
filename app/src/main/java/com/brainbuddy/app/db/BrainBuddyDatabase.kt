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
    version = 7,
    exportSchema = false
)
abstract class BrainBuddyDatabase : RoomDatabase() {

    companion object {
        private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            val c = db.query("PRAGMA table_info($table)")
            try {
                val nameIdx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) {
                    if (c.getString(nameIdx).equals(column, ignoreCase = true)) return true
                }
                return false
            } finally {
                c.close()
            }
        }

        private fun safeAddColumn(db: SupportSQLiteDatabase, table: String, columnDef: String) {
            val colName = columnDef.substringBefore(" ").trim()
            if (columnExists(db, table, colName)) return
            db.execSQL("ALTER TABLE $table ADD COLUMN $columnDef")
        }

        private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
            val c = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table)
            )
            try {
                return c.moveToFirst()
            } finally {
                c.close()
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // questions: tüm eksik kolonları güvenli ekle
                safeAddColumn(db, "questions", "grade INTEGER NOT NULL DEFAULT 2")
                safeAddColumn(db, "questions", "difficulty INTEGER NOT NULL DEFAULT 1")
                safeAddColumn(db, "questions", "optionsJson TEXT NOT NULL DEFAULT '[]'")
                safeAddColumn(db, "questions", "examType TEXT")
                safeAddColumn(db, "questions", "imageAsset TEXT")
                safeAddColumn(db, "questions", "isActive INTEGER NOT NULL DEFAULT 1")
                safeAddColumn(db, "questions", "version INTEGER NOT NULL DEFAULT 1")

                // wrong_answers: eksik kolonları ekle
                safeAddColumn(db, "wrong_answers", "isUnlocked INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "wrong_answers", "unlockedAt INTEGER")

                // question_history: yoksa oluştur
                if (!tableExists(db, "question_history")) {
                    db.execSQL("""
                        CREATE TABLE question_history (
                            userId TEXT NOT NULL,
                            questionId TEXT NOT NULL,
                            lastResult INTEGER NOT NULL DEFAULT 0,
                            lastAnsweredAt INTEGER NOT NULL DEFAULT 0,
                            correctCount INTEGER NOT NULL DEFAULT 0,
                            wrongCount INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY(userId, questionId)
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_question_history_user ON question_history(userId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_question_history_user_result ON question_history(userId, lastResult)")
                }

                // Performans indexleri
                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject ON questions(grade, subject)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_grade_subject_difficulty ON questions(grade, subject, difficulty)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE test_snapshots ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeAddColumn(db, "questions", "grade INTEGER NOT NULL DEFAULT 2")
                safeAddColumn(db, "questions", "difficulty INTEGER NOT NULL DEFAULT 1")
                safeAddColumn(db, "questions", "optionsJson TEXT NOT NULL DEFAULT '[]'")
                safeAddColumn(db, "questions", "examType TEXT")
                safeAddColumn(db, "questions", "imageAsset TEXT")
                safeAddColumn(db, "questions", "isActive INTEGER NOT NULL DEFAULT 1")
                safeAddColumn(db, "questions", "version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_grade_subject_difficulty ON questions(grade, subject, difficulty)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN grade INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_grade_subject_difficulty ON questions(grade, subject, difficulty)")
                // Backfill grade from gradeTag where possible
                val c = db.query("SELECT id, gradeTag FROM questions WHERE grade = 0 AND gradeTag IS NOT NULL AND gradeTag != ''")
                try {
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val tag = c.getString(1) ?: continue
                        val g = tag.toIntOrNull()?.coerceIn(2, 8) ?: 6
                        db.execSQL("UPDATE questions SET grade = ? WHERE id = ?", arrayOf(g, id))
                    }
                } finally {
                    c.close()
                }
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS question_history")
                db.execSQL("""
                    CREATE TABLE question_history (
                        userId TEXT NOT NULL,
                        questionId TEXT NOT NULL,
                        lastResult INTEGER NOT NULL,
                        lastAnsweredAt INTEGER NOT NULL,
                        correctCount INTEGER NOT NULL DEFAULT 0,
                        wrongCount INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(userId, questionId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_question_history_user ON question_history(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_question_history_user_result ON question_history(userId, lastResult)")
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
