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
    version = 13,
    exportSchema = false
)
abstract class BrainBuddyDatabase : RoomDatabase() {

    abstract fun questionDao(): QuestionDao
    abstract fun wrongAnswerDao(): WrongAnswerDao
    abstract fun historyDao(): HistoryDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun appMetaDao(): AppMetaDao

    companion object {
        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Recreate questions table to match finalized schema.
                database.execSQL(
                    """
                    ALTER TABLE questions RENAME TO questions_old
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS questions (
                        id TEXT NOT NULL PRIMARY KEY,
                        grade INTEGER NOT NULL,
                        subject TEXT NOT NULL,
                        difficulty INTEGER NOT NULL,
                        questionText TEXT NOT NULL,
                        optionsJson TEXT NOT NULL,
                        answerIndex INTEGER NOT NULL,
                        explanation TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        version INTEGER NOT NULL DEFAULT 1,
                        examType TEXT,
                        imageAsset TEXT
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    INSERT INTO questions (
                        id,
                        grade,
                        subject,
                        difficulty,
                        questionText,
                        optionsJson,
                        answerIndex,
                        explanation,
                        isActive,
                        version,
                        examType,
                        imageAsset
                    )
                    SELECT
                        id,
                        grade,
                        subject,
                        difficulty,
                        text AS questionText,
                        optionsJson,
                        correctIndex AS answerIndex,
                        hint AS explanation,
                        CASE WHEN isActive THEN 1 ELSE 0 END AS isActive,
                        version,
                        examType,
                        imageAsset
                    FROM questions_old
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE questions_old")

                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_questions_grade_subject
                    ON questions(grade, subject)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13: Migration = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE questions ADD COLUMN questionType TEXT
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE questions ADD COLUMN skillsJson TEXT
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE questions ADD COLUMN deactivationReason TEXT
                    """.trimIndent()
                )
            }
        }
    }
}
