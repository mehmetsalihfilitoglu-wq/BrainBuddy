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
    version = 8,
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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions RENAME TO questions_old")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS questions (
                        id TEXT NOT NULL PRIMARY KEY,
                        subject TEXT NOT NULL,
                        difficulty INTEGER NOT NULL DEFAULT 1,
                        grade INTEGER NOT NULL DEFAULT 5,
                        text TEXT NOT NULL,
                        optionsJson TEXT NOT NULL DEFAULT '[]',
                        correctIndex INTEGER NOT NULL,
                        tagsJson TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        version INTEGER NOT NULL DEFAULT 1,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        levelGroup TEXT,
                        gradeTag TEXT,
                        hint TEXT,
                        imageAsset TEXT,
                        examType TEXT
                    )
                """.trimIndent())

                val oldCols = mutableSetOf<String>()
                val cur = db.query("PRAGMA table_info(questions_old)")
                try {
                    val nameIdx = cur.getColumnIndexOrThrow("name")
                    while (cur.moveToNext()) {
                        oldCols.add(cur.getString(nameIdx))
                    }
                } finally {
                    cur.close()
                }

                val textCol = when {
                    oldCols.contains("text") -> "text"
                    oldCols.contains("questionText") -> "questionText"
                    else -> "text"
                }

                val sel = db.query("SELECT * FROM questions_old")
                val idIdx = sel.getColumnIndexOrThrow("id")
                val subjectIdx = sel.getColumnIndexOrThrow("subject")
                val textIdx = sel.getColumnIndexOrThrow(textCol)
                val optionsIdx = sel.getColumnIndexOrThrow("optionsJson")
                val correctIdx = sel.getColumnIndexOrThrow("correctIndex")
                val diffIdx = if (sel.getColumnIndex("difficulty") >= 0) sel.getColumnIndex("difficulty") else -1
                val gradeIdx = if (sel.getColumnIndex("grade") >= 0) sel.getColumnIndex("grade") else -1
                val tagsIdx = if (sel.getColumnIndex("tagsJson") >= 0) sel.getColumnIndex("tagsJson") else -1
                val isActiveIdx = if (sel.getColumnIndex("isActive") >= 0) sel.getColumnIndex("isActive") else -1
                val versionIdx = if (sel.getColumnIndex("version") >= 0) sel.getColumnIndex("version") else -1
                val updatedIdx = if (sel.getColumnIndex("updatedAt") >= 0) sel.getColumnIndex("updatedAt") else -1
                val levelIdx = if (sel.getColumnIndex("levelGroup") >= 0) sel.getColumnIndex("levelGroup") else -1
                val gradeTagIdx = if (sel.getColumnIndex("gradeTag") >= 0) sel.getColumnIndex("gradeTag") else -1
                val hintIdx = if (sel.getColumnIndex("hint") >= 0) sel.getColumnIndex("hint") else -1
                val imgIdx = if (sel.getColumnIndex("imageAsset") >= 0) sel.getColumnIndex("imageAsset") else -1
                val examIdx = if (sel.getColumnIndex("examType") >= 0) sel.getColumnIndex("examType") else -1

                while (sel.moveToNext()) {
                    val id = sel.getString(idIdx)
                    val subject = sel.getString(subjectIdx)
                    val text = sel.getString(textIdx)
                    val options = sel.getString(optionsIdx)
                    val correctIndex = sel.getInt(correctIdx)
                    val difficulty = if (diffIdx >= 0) sel.getInt(diffIdx) else 1
                    val grade = if (gradeIdx >= 0) sel.getInt(gradeIdx) else 5
                    val tags = if (tagsIdx >= 0 && !sel.isNull(tagsIdx)) sel.getString(tagsIdx) else null
                    val isActive = if (isActiveIdx >= 0) sel.getInt(isActiveIdx) != 0 else true
                    val version = if (versionIdx >= 0) sel.getInt(versionIdx) else 1
                    val updatedAt = if (updatedIdx >= 0) sel.getLong(updatedIdx) else 0L
                    val levelGroup = if (levelIdx >= 0 && !sel.isNull(levelIdx)) sel.getString(levelIdx) else null
                    val gradeTag = if (gradeTagIdx >= 0 && !sel.isNull(gradeTagIdx)) sel.getString(gradeTagIdx) else null
                    val hint = if (hintIdx >= 0 && !sel.isNull(hintIdx)) sel.getString(hintIdx) else null
                    val imageAsset = if (imgIdx >= 0 && !sel.isNull(imgIdx)) sel.getString(imgIdx) else null
                    val examType = if (examIdx >= 0 && !sel.isNull(examIdx)) sel.getString(examIdx) else null

                    db.execSQL(
                        """INSERT INTO questions (id, subject, difficulty, grade, text, optionsJson, correctIndex, tagsJson, isActive, version, updatedAt, levelGroup, gradeTag, hint, imageAsset, examType)
                           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        arrayOf(id, subject, difficulty, grade, text, options, correctIndex, tags, if (isActive) 1 else 0, version, updatedAt, levelGroup, gradeTag, hint, imageAsset, examType)
                    )
                }
                sel.close()

                db.execSQL("DROP TABLE questions_old")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject ON questions(grade, subject)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_grade_subject_difficulty ON questions(grade, subject, difficulty)")
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
