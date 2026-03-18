package com.brainbuddy.app.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeSeedingInstrumentedTest {

    @Test
    fun runtimeSeed_producesLargeQuestionTable_and_pickerCanSeeIt() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // Trigger the real production seed path. If DB is already seeded but incomplete,
        // DbSeeder has a safe top-up recovery path (no wipe).
        DbSeeder.seedIfNeeded(ctx)

        val db = DatabaseProvider.get(ctx)
        val dao = db.questionDao()
        val total = dao.countAll()
        val active = dao.countAllActive()

        // Basic "UI can use it" proxy: candidate pool queries should return non-empty results.
        val g6Mat = dao.getCandidatePoolByGradeSubject(6, "mat").size
        val g4Ing = dao.getCandidatePoolByGradeSubject(4, "ing").size
        val lgsMat = dao.getCandidatePoolByLgsSubject("mat").size

        println("RUNTIME_SEED_DB total=$total active=$active g6_mat_candidates=$g6Mat g4_ing_candidates=$g4Ing lgs_mat_candidates=$lgsMat")

        assertTrue("Expected seeded DB to be large; total=$total", total >= 12000)
        assertTrue("Expected active questions to exist; active=$active", active > 0)
        assertTrue("Expected grade pool candidates; g6Mat=$g6Mat", g6Mat > 0)
        assertTrue("Expected grade pool candidates; g4Ing=$g4Ing", g4Ing > 0)
        assertTrue("Expected LGS candidates; lgsMat=$lgsMat", lgsMat > 0)
    }
}

