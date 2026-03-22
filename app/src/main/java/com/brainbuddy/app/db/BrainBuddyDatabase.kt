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
    version = 23,
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

        // 13 -> 14: Eski VERY_HARD (3) kayıtlarını HARD (2) olarak normalize et.
        val MIGRATION_13_14: Migration = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    UPDATE questions
                    SET difficulty = 2
                    WHERE difficulty = 3
                    """.trimIndent()
                )
            }
        }

        // 14 -> 15: Diversity alanları (type/skill) ekle, default UNKNOWN.
        val MIGRATION_14_15: Migration = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE questions
                    ADD COLUMN type TEXT NOT NULL DEFAULT 'UNKNOWN'
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE questions
                    ADD COLUMN skill TEXT NOT NULL DEFAULT 'UNKNOWN'
                    """.trimIndent()
                )
            }
        }

        // 15 -> 16: Indices + stem hash fields for scalable question bank (20k+).
        val MIGRATION_15_16: Migration = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE questions ADD COLUMN normalizedStemHash TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE questions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    ALTER TABLE questions ADD COLUMN sourcePack TEXT
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade ON questions(grade)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_subject ON questions(subject)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_difficulty ON questions(difficulty)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_isActive ON questions(isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_normalized_stem_hash ON questions(normalizedStemHash)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject ON questions(grade, subject)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject_difficulty ON questions(grade, subject, difficulty)")
            }
        }

        // 16 -> 17: Büyük soru havuzu güçlendirmesi – yeni alanlar, indeksler, UNIQUE(grade,subject,stemHash).
        // Safe approach: create_new_table + copy + drop + rename. No inline UNIQUE/ALTER constraints.
        val MIGRATION_16_17: Migration = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1) Create questions_new with EXACT final schema Room expects (all NOT NULL have DEFAULT).
                //    NO inline UNIQUE – we create indices after rename.
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS questions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        grade INTEGER NOT NULL DEFAULT 6,
                        subject TEXT NOT NULL DEFAULT '',
                        difficulty INTEGER NOT NULL DEFAULT 1,
                        questionText TEXT NOT NULL DEFAULT '',
                        optionsJson TEXT NOT NULL DEFAULT '[]',
                        answerIndex INTEGER NOT NULL DEFAULT 0,
                        explanation TEXT,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        questionType TEXT NOT NULL DEFAULT 'UNKNOWN',
                        skillsJson TEXT NOT NULL DEFAULT '[]',
                        deactivationReason TEXT,
                        version INTEGER NOT NULL DEFAULT 1,
                        examType TEXT,
                        imageAsset TEXT,
                        type TEXT NOT NULL DEFAULT 'UNKNOWN',
                        skill TEXT NOT NULL DEFAULT 'UNKNOWN',
                        stemNormalized TEXT NOT NULL DEFAULT '',
                        stemHash TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        sourcePack TEXT,
                        source TEXT,
                        sourceRef TEXT,
                        publisher TEXT,
                        year INTEGER,
                        topic TEXT
                    )
                    """.trimIndent()
                )

                // 2) Copy data from old questions. Dedupe by (grade, subject, stemHash) keeping min(id).
                //    stemHash = normalizedStemHash if non-empty else id. New columns get defaults.
                database.execSQL(
                    """
                    INSERT INTO questions_new (
                        id, grade, subject, difficulty, questionText, optionsJson, answerIndex,
                        explanation, isActive, questionType, skillsJson, deactivationReason,
                        version, examType, imageAsset, type, skill, stemNormalized, stemHash,
                        createdAt, sourcePack, source, sourceRef, publisher, year, topic
                    )
                    SELECT
                        q.id, q.grade, q.subject, q.difficulty, q.questionText, q.optionsJson, q.answerIndex,
                        q.explanation, q.isActive,
                        COALESCE(NULLIF(TRIM(q.questionType), ''), 'UNKNOWN'),
                        COALESCE(NULLIF(TRIM(q.skillsJson), ''), '[]'),
                        q.deactivationReason, q.version, q.examType, q.imageAsset,
                        COALESCE(q.type, 'UNKNOWN'), COALESCE(q.skill, 'UNKNOWN'),
                        '',
                        CASE WHEN COALESCE(q.normalizedStemHash, '') != '' THEN q.normalizedStemHash ELSE q.id END,
                        COALESCE(q.createdAt, 0), q.sourcePack,
                        NULL, NULL, NULL, NULL, NULL
                    FROM questions q
                    INNER JOIN (
                        SELECT grade, subject,
                            CASE WHEN COALESCE(normalizedStemHash, '') != '' THEN normalizedStemHash ELSE id END AS sh,
                            MIN(id) AS keep_id
                        FROM questions
                        GROUP BY grade, subject, sh
                    ) dedup
                        ON q.grade = dedup.grade AND q.subject = dedup.subject
                        AND (CASE WHEN COALESCE(q.normalizedStemHash, '') != '' THEN q.normalizedStemHash ELSE q.id END) = dedup.sh
                        AND q.id = dedup.keep_id
                    """.trimIndent()
                )

                database.execSQL("DROP TABLE questions")
                database.execSQL("ALTER TABLE questions_new RENAME TO questions")

                // 3) Recreate ALL indices including unique constraint (Room expects these names).
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade ON questions(grade)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_subject ON questions(subject)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_difficulty ON questions(difficulty)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_isActive ON questions(isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject ON questions(grade, subject)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject_difficulty ON questions(grade, subject, difficulty)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject_difficulty_active ON questions(grade, subject, difficulty, isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_stem_hash ON questions(stemHash)")
            }
        }

        // 19 -> 20: Relax dedup – drop UNIQUE(grade,subject,stemHash) so multiple variants per stem can coexist.
        // Do NOT create any new index: QuestionEntity declares only 8 indices; Room validates exact match.
        val MIGRATION_19_20: Migration = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS unique_questions_grade_subject_stem_hash")
            }
        }

        // 17 -> 18: Safe add missing columns so upgrades work without destructive.
        // PRAGMA table_info; for each expected column missing -> ADD COLUMN with DEFAULT for NOT NULL.
        val MIGRATION_17_18: Migration = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info('questions')")
                val existingColumns = mutableSetOf<String>()
                try {
                    val nameIdx = cursor.getColumnIndex("name").takeIf { it >= 0 } ?: 1
                    while (cursor.moveToNext()) {
                        existingColumns.add(cursor.getString(nameIdx))
                    }
                } finally {
                    cursor.close()
                }

                // (column name, full ADD COLUMN SQL) – only name is used for "if missing".
                val expectedColumns = listOf(
                    "grade" to "ALTER TABLE questions ADD COLUMN grade INTEGER NOT NULL DEFAULT 6",
                    "subject" to "ALTER TABLE questions ADD COLUMN subject TEXT NOT NULL DEFAULT ''",
                    "difficulty" to "ALTER TABLE questions ADD COLUMN difficulty INTEGER NOT NULL DEFAULT 1",
                    "questionText" to "ALTER TABLE questions ADD COLUMN questionText TEXT NOT NULL DEFAULT ''",
                    "optionsJson" to "ALTER TABLE questions ADD COLUMN optionsJson TEXT NOT NULL DEFAULT '[]'",
                    "answerIndex" to "ALTER TABLE questions ADD COLUMN answerIndex INTEGER NOT NULL DEFAULT 0",
                    "explanation" to "ALTER TABLE questions ADD COLUMN explanation TEXT",
                    "isActive" to "ALTER TABLE questions ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1",
                    "questionType" to "ALTER TABLE questions ADD COLUMN questionType TEXT NOT NULL DEFAULT 'UNKNOWN'",
                    "skillsJson" to "ALTER TABLE questions ADD COLUMN skillsJson TEXT NOT NULL DEFAULT '[]'",
                    "deactivationReason" to "ALTER TABLE questions ADD COLUMN deactivationReason TEXT",
                    "version" to "ALTER TABLE questions ADD COLUMN version INTEGER NOT NULL DEFAULT 1",
                    "examType" to "ALTER TABLE questions ADD COLUMN examType TEXT",
                    "imageAsset" to "ALTER TABLE questions ADD COLUMN imageAsset TEXT",
                    "type" to "ALTER TABLE questions ADD COLUMN type TEXT NOT NULL DEFAULT 'UNKNOWN'",
                    "skill" to "ALTER TABLE questions ADD COLUMN skill TEXT NOT NULL DEFAULT 'UNKNOWN'",
                    "stemNormalized" to "ALTER TABLE questions ADD COLUMN stemNormalized TEXT NOT NULL DEFAULT ''",
                    "stemHash" to "ALTER TABLE questions ADD COLUMN stemHash TEXT NOT NULL DEFAULT ''",
                    "createdAt" to "ALTER TABLE questions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0",
                    "sourcePack" to "ALTER TABLE questions ADD COLUMN sourcePack TEXT",
                    "source" to "ALTER TABLE questions ADD COLUMN source TEXT",
                    "sourceRef" to "ALTER TABLE questions ADD COLUMN sourceRef TEXT",
                    "publisher" to "ALTER TABLE questions ADD COLUMN publisher TEXT",
                    "year" to "ALTER TABLE questions ADD COLUMN year INTEGER",
                    "topic" to "ALTER TABLE questions ADD COLUMN topic TEXT"
                )
                for ((name, sql) in expectedColumns) {
                    if (!existingColumns.contains(name)) {
                        try {
                            database.execSQL(sql)
                        } catch (e: Exception) {
                            android.util.Log.w("BrainBuddyDatabase", "MIGRATION_17_18: add column $name failed: ${e.message}")
                        }
                    }
                }

                // Ensure indexes exist for pool pick performance.
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_grade_subject_difficulty_active ON questions(grade, subject, difficulty, isActive)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_stem_hash ON questions(stemHash)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_questions_isActive ON questions(isActive)")
            }
        }

        // 18 -> 19: LGS quality metadata (qualityScore, isNewGenerationLike)
        val MIGRATION_18_19: Migration = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN qualityScore INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN isNewGenerationLike INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** 20 -> 21: Global content quality classifier fields (tier, scores, flags, unservable). */
        val MIGRATION_20_21: Migration = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN qualityTier TEXT NOT NULL DEFAULT 'MEDIUM'"
                )
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN reasoningScore INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN distractorQualityScore INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN contextComplexityScore INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN qualityFlagsJson TEXT NOT NULL DEFAULT '[]'"
                )
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN unservableReason TEXT"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_questions_qualityTier ON questions(qualityTier)"
                )
            }
        }

        /**
         * One-time strict quality cleanup: mark rows that fail reasoning/distractor/tier rules as unservable.
         * Does not delete rows.
         */
        val MIGRATION_21_22: Migration = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    UPDATE questions SET unservableReason = 'TRIVIAL'
                    WHERE COALESCE(qualityTier, 'MEDIUM') = 'EASY'
                    AND (unservableReason IS NULL OR TRIM(unservableReason) = '')
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    UPDATE questions SET unservableReason = 'LOW_REASONING'
                    WHERE reasoningScore < 40
                    AND (unservableReason IS NULL OR TRIM(unservableReason) = '')
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    UPDATE questions SET unservableReason = 'WEAK_DISTRACTORS'
                    WHERE distractorQualityScore < 40
                    AND (unservableReason IS NULL OR TRIM(unservableReason) = '')
                    """.trimIndent()
                )
            }
        }

        /**
         * reasoningLevel 0..3, BORDERLINE tier, reopen EASY for adaptive serving (no data loss).
         */
        val MIGRATION_22_23: Migration = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE questions ADD COLUMN reasoningLevel INTEGER NOT NULL DEFAULT 2"
                )
                database.execSQL(
                    """
                    UPDATE questions SET reasoningLevel = CASE
                        WHEN COALESCE(qualityTier, 'MEDIUM') = 'HARD' THEN 3
                        WHEN COALESCE(qualityTier, 'MEDIUM') = 'MEDIUM' THEN 2
                        WHEN COALESCE(qualityTier, 'MEDIUM') = 'BORDERLINE' THEN 1
                        WHEN COALESCE(qualityTier, 'MEDIUM') = 'EASY' THEN 0
                        WHEN reasoningScore >= 68 THEN 3
                        WHEN reasoningScore >= 45 THEN 2
                        WHEN reasoningScore >= 20 THEN 1
                        ELSE 0
                    END
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    UPDATE questions SET qualityTier = 'BORDERLINE'
                    WHERE reasoningLevel = 1 AND (qualityTier IS NULL OR qualityTier NOT IN ('BORDERLINE','EASY','MEDIUM','HARD'))
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    UPDATE questions SET unservableReason = NULL
                    WHERE COALESCE(unservableReason, '') IN ('LOW_REASONING','WEAK_DISTRACTORS','QUALITY_TIER_EASY')
                    """.trimIndent()
                )
            }
        }
    }
}
