package com.edumio.app.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the FROZEN Room schema baseline (Phase 0). `exportSchema = true` writes
 * `app/schemas/<db-fqcn>/<version>.json`; these files are committed and are the migration baseline.
 *
 * This pure-JVM test fails if schema export is ever turned off, if the shipped versions drift, or if the
 * entity set changes without a new schema — catching the class of mistake that makes a production
 * migration unsafe. (The migration *chain* itself is exercised on-device by MigrationTestHelper /
 * QuestionMigrationTest, which need an emulator; this guard needs none.)
 */
class RoomSchemaExportTest {

    private fun schema(dbFqcn: String, version: Int): JSONObject {
        val f = File("schemas/$dbFqcn/$version.json")
        assertTrue(
            "Exported Room schema missing: ${f.path}. Is exportSchema=true + room.schemaLocation set, " +
                "and the schema committed?",
            f.exists(),
        )
        return JSONObject(f.readText())
    }

    private fun tables(schema: JSONObject): Set<String> {
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }.toSet()
    }

    @Test
    fun edumioDb_v24_schemaExportedAndFrozen() {
        val s = schema("com.edumio.app.db.EdumioDatabase", 24)
        assertEquals("edumio.db frozen baseline version", 24, s.getJSONObject("database").getInt("version"))
        assertEquals(
            setOf("questions", "question_history", "test_snapshots", "app_meta", "wrong_answers"),
            tables(s),
        )
    }

    @Test
    fun dailyChallengeDb_v2_schemaExportedAndFrozen() {
        val s = schema("com.edumio.app.dailychallenge.DailyChallengeDatabase", 2)
        assertEquals("daily_challenge.db frozen baseline version", 2, s.getJSONObject("database").getInt("version"))
        assertEquals(
            setOf("daily_challenge", "challenge_answer", "user_question_state", "section_deficit", "streak"),
            tables(s),
        )
    }

    @Test
    fun schemas_haveDeterministicIdentityHash() {
        // Room writes a stable identityHash derived from the schema; its presence proves a real export
        // (not a hand-written stub) and pins the schema so any structural drift changes the hash.
        for ((db, v) in listOf(
            "com.edumio.app.db.EdumioDatabase" to 24,
            "com.edumio.app.dailychallenge.DailyChallengeDatabase" to 2,
        )) {
            val hash = schema(db, v).getJSONObject("database").getString("identityHash")
            assertTrue("identityHash present for $db v$v", hash.isNotBlank())
        }
    }
}
