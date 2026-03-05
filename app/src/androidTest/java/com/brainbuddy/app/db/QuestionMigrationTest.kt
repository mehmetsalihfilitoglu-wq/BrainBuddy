package com.brainbuddy.app.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Migration 16 -> 17 test: safe create_new_table + copy + drop + rename approach.
 * Asserts migration runs and database opens without crash.
 * Note: exportSchema=false, so we manually create v16 and run migration via Room builder.
 */
@RunWith(AndroidJUnit4::class)
class QuestionMigrationTest {

    private val dbName = "migration_test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BrainBuddyDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate16To17_opensSuccessfully() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        // Create v16 db manually (exportSchema=false, so we cannot use helper.createDatabase).
        val dbPath = context.getDatabasePath(dbName).absolutePath
        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS question_history (userId TEXT, questionId TEXT, lastResult INTEGER, lastAnsweredAt INTEGER, correctCount INTEGER, wrongCount INTEGER, PRIMARY KEY(userId, questionId))")
            db.execSQL("CREATE TABLE IF NOT EXISTS test_snapshots (testId TEXT PRIMARY KEY, createdAt INTEGER, score INTEGER, total INTEGER, profileId TEXT, subjectBreakdownJson TEXT, questionIdsJson TEXT, userAnswersJson TEXT, wrongQuestionIdsJson TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS app_meta (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS wrong_answers (testId TEXT, questionId TEXT, isUnlocked INTEGER, unlockedAt INTEGER, PRIMARY KEY(testId, questionId))")
            db.execSQL(
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
                    questionType TEXT,
                    skillsJson TEXT,
                    deactivationReason TEXT,
                    version INTEGER NOT NULL DEFAULT 1,
                    examType TEXT,
                    imageAsset TEXT,
                    type TEXT NOT NULL DEFAULT 'UNKNOWN',
                    skill TEXT NOT NULL DEFAULT 'UNKNOWN',
                    normalizedStemHash TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    sourcePack TEXT
                )
                """.trimIndent()
            )
            db.execSQL("PRAGMA user_version = 16")
            db.execSQL(
                "INSERT INTO questions (id, grade, subject, difficulty, questionText, optionsJson, answerIndex) " +
                    "VALUES ('t1', 6, 'mat', 1, 'Test?', '[]', 0)"
            )
        }

        // Run migration via Room (registers all migrations so chain is correct).
        val roomDb = Room.databaseBuilder(context, BrainBuddyDatabase::class.java, dbName)
            .addMigrations(
                BrainBuddyDatabase.MIGRATION_11_12,
                BrainBuddyDatabase.MIGRATION_12_13,
                BrainBuddyDatabase.MIGRATION_13_14,
                BrainBuddyDatabase.MIGRATION_14_15,
                BrainBuddyDatabase.MIGRATION_15_16,
                BrainBuddyDatabase.MIGRATION_16_17
            )
            .build()
        helper.closeWhenFinished(roomDb)

        roomDb.openHelper.writableDatabase.use { wdb ->
            assertEquals(17, wdb.version)
            wdb.query("SELECT stemNormalized, stemHash, questionType, skillsJson FROM questions WHERE id='t1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("", c.getString(0))
                assertEquals("t1", c.getString(1))
                assertEquals("UNKNOWN", c.getString(2))
                assertEquals("[]", c.getString(3))
            }
        }
        context.deleteDatabase(dbName)
    }
}
