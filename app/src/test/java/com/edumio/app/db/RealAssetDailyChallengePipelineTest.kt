package com.edumio.app.db

import com.edumio.app.core.ExamType
import com.edumio.app.dailychallenge.ChallengeStatus
import com.edumio.app.dailychallenge.DailyChallengeBlueprint
import com.edumio.app.dailychallenge.DailyChallengeEngine
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter.CardState
import com.edumio.app.dailychallenge.DcContentSource
import com.edumio.app.dailychallenge.InMemoryDailyChallengeDao
import com.edumio.app.dailychallenge.testEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.TimeZone

/**
 * Release-blocker regression guard: verifies the FULL Daily Challenge pipeline for ALL THREE exams
 * (IMAT, TIL-I, CEnT-S) using the REAL production asset files — NO fakes — end to end:
 *
 *   asset file present + non-empty  →  real parser (DbSeeder.parse*)  →  examType-scoped candidate pool
 *   (the exact QuestionDao.getDailyCandidatePool filter, simulated in-memory)  →  every blueprint section
 *   populated  →  engine generates EXACTLY 5  →  Home opens AVAILABLE.
 *
 * If any exam's real bank fails to parse, records the wrong examType, or under-populates a blueprint
 * section, the matching assertion fails at build time instead of surfacing on a device as
 * "Sorular yüklenemedi" / "Tamamlandı". The problem was only ever *observed* on CEnT-S, but IMAT and
 * TIL-I run the identical pipeline, so all three are proven here.
 */
class RealAssetDailyChallengePipelineTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val t0 = 1_700_000_000_000L

    private data class Bank(
        val exam: ExamType,
        val asset: String,
        val expectedCount: Int,
        val parse: (String) -> List<QuestionEntity>,
    )

    // The real production banks, kept in sync with the shipped assets (content is frozen).
    private val banks = listOf(
        Bank(ExamType.IMAT, "imat/imat_questions.json", 940) { DbSeeder.parseImatQuestions(it) },
        Bank(ExamType.TIL_I, "til_i/questions.json", 1107) { DbSeeder.parseDailyChallengeQuestions(it, "TIL_I") },
        Bank(ExamType.CENT_S, "cents_s/questions.json", 1100) { DbSeeder.parseDailyChallengeQuestions(it, "CENT_S") },
    )

    /** Reads a real production asset from the module src tree (unit tests run with the module dir as cwd). */
    private fun readAsset(rel: String): Pair<File, String> {
        val f = listOf(
            File("src/main/assets/$rel"),
            File("app/src/main/assets/$rel"),
            File("../app/src/main/assets/$rel"),
        ).firstOrNull { it.exists() }
            ?: error("real production asset not found for '$rel' (cwd=${File(".").absolutePath})")
        return f to f.readText(Charsets.UTF_8)
    }

    /** In-memory content source that reproduces QuestionDao.getDailyCandidatePool EXACTLY. */
    private class RealAssetContent(private val all: List<QuestionEntity>) : DcContentSource {
        private val byId = all.associateBy { it.id }
        override suspend fun getDailyCandidatePool(examType: String, section: String): List<QuestionCandidateRow> =
            all.filter {
                (it.examType ?: "GENERAL") == examType &&
                    it.subject == section &&
                    it.isActive &&
                    (it.unservableReason == null || it.unservableReason == "")
            }.map {
                QuestionCandidateRow(
                    id = it.id, subject = it.subject, difficulty = it.difficulty, grade = it.grade,
                    stemHash = it.stemHash, stemNormalized = it.stemNormalized, type = it.type, skill = it.skill,
                    topic = it.topic ?: "OTHER", qualityTier = it.qualityTier, reasoningLevel = it.reasoningLevel,
                )
            }
        override suspend fun getQuestionsByIds(ids: List<String>): List<QuestionEntity> = ids.mapNotNull { byId[it] }
    }

    @Test
    fun realAssets_allThreeExams_produceAvailableFiveQuestionChallenge() = runBlocking {
        for (bank in banks) {
            val exam = bank.exam

            // A) asset present + non-empty
            val (file, json) = readAsset(bank.asset)
            assertTrue("${exam.name}: asset must be non-empty (${file.path})", file.length() > 0 && json.isNotBlank())

            // B) real parser succeeds with the expected count
            val rows = bank.parse(json)
            assertTrue("${exam.name}: parse produced questions", rows.isNotEmpty())
            assertEquals("${exam.name}: parsed count", bank.expectedCount, rows.size)

            // C) examType is EXACTLY the enum name; all rows active + servable (== what INSERT persists)
            assertEquals(
                "${exam.name}: every row carries examType='${exam.name}'",
                rows.size, rows.count { it.examType == exam.name },
            )
            val servable = rows.count { it.isActive && (it.unservableReason == null || it.unservableReason == "") }
            assertEquals("${exam.name}: all rows servable (isActive, no unservableReason)", rows.size, servable)

            // D) blueprint sections: every requested section is present with a positive count
            val content = RealAssetContent(rows)
            val sectionCounts = LinkedHashMap<String, Int>()
            for (section in DailyChallengeBlueprint.sections(exam)) {
                val n = content.getDailyCandidatePool(exam.name, section).size
                sectionCounts[section] = n
                assertTrue("${exam.name}: blueprint section '$section' must have candidates (had $n)", n > 0)
            }

            // E) the ENGINE generates exactly 5 from the real pool, drawn per blueprint
            val dao = InMemoryDailyChallengeDao()
            val engine = testEngine(dao, content)
            val r = engine.getOrCreateToday("real_${exam.name}", exam, utc, t0)
            assertNotNull("${exam.name}: must generate a challenge from the real asset", r)
            assertEquals("${exam.name}: exactly 5 questions", 5, r!!.questions.size)
            assertEquals("${exam.name}: fresh challenge answered=0", 0, r.answered)
            assertFalse("${exam.name}: fresh challenge is NOT completed", r.completed)
            assertEquals("${exam.name}: challenge examProfile", exam.name, r.challenge.examProfile)

            val servedIds = r.challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }.toSet()
            val servedRows = rows.filter { it.id in servedIds }
            assertEquals("${exam.name}: 5 served ids resolve to real rows", 5, servedRows.size)
            assertTrue("${exam.name}: served questions are all this exam", servedRows.all { it.examType == exam.name })
            assertTrue(
                "${exam.name}: served questions sit in blueprint sections",
                servedRows.all { it.subject in DailyChallengeBlueprint.sections(exam) },
            )

            // Home opens AVAILABLE (0/5) — never completed / empty / error.
            assertEquals(
                "${exam.name}: Home opens AVAILABLE",
                CardState.AVAILABLE,
                DailyChallengeHomePresenter.cardState(true, r.answered, r.total, r.completed),
            )

            println("[REAL-ASSET ${exam.name}] parsed=${rows.size} servable=$servable sections=$sectionCounts served=${servedRows.map { it.subject }}")
        }
    }

    /**
     * Full runtime lifecycle per exam with the REAL production assets — the automated stand-in for the
     * on-device fresh-install walkthrough (behaviours #2–#7): auto-generate today's challenge (exactly 5,
     * no "Sorular yüklenemedi" throw), answer all 5 through to completion + score, Home shows COMPLETED,
     * then close & reopen (a NEW engine on the SAME store) and confirm the SAME completed challenge is
     * preserved with no regeneration. Behaviour #1 (Activity launch / onboarding UI) and the pixel
     * rendering of #5 require a physical device and are covered by the manual checklist, not this test.
     */
    @Test
    fun realAssets_allThreeExams_fullLifecycle_generate_answer_complete_reopen() = runBlocking {
        for (bank in banks) {
            val exam = bank.exam
            val rows = bank.parse(readAsset(bank.asset).second)
            val content = RealAssetContent(rows)
            val dao = InMemoryDailyChallengeDao()
            val engine = testEngine(dao, content)
            val userId = "device_${exam.name}"

            // #2/#3/#4 — auto-generate: exactly 5, not completed, never null (no "Sorular yüklenemedi")
            val gen = engine.getOrCreateToday(userId, exam, utc, t0)
            assertNotNull("${exam.name}: Günün Görevi auto-generates (no 'Sorular yüklenemedi')", gen)
            assertEquals("${exam.name}: exactly 5 questions", 5, gen!!.questions.size)
            assertFalse("${exam.name}: starts NOT completed", gen.completed)
            assertEquals(
                "${exam.name}: Home opens AVAILABLE",
                CardState.AVAILABLE,
                DailyChallengeHomePresenter.cardState(true, gen.answered, gen.total, gen.completed),
            )

            // #5 — solve all 5 through to completion (each answer persisted, resume cursor advances)
            val qids = gen.challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
            assertEquals("${exam.name}: 5 question ids", 5, qids.size)
            var last: DailyChallengeEngine.Result = gen
            qids.forEachIndexed { i, qid ->
                last = engine.submitAnswer(
                    userId, gen.challenge.localDate, qid, chosenIndex = 0, isCorrect = i % 2 == 0, timeMs = 1000, nowMs = t0,
                )!!
                assertEquals("${exam.name}: progress after ${i + 1} answers", i + 1, last.answered)
            }

            // #6 — completed + scored; Home shows COMPLETED
            assertTrue("${exam.name}: completed after 5 answers", last.completed)
            assertEquals("${exam.name}: status COMPLETED", ChallengeStatus.COMPLETED.name, last.challenge.status)
            assertEquals("${exam.name}: score = number correct (3)", 3, last.challenge.score)
            assertEquals(
                "${exam.name}: Home shows COMPLETED (Tamamlandı)",
                CardState.COMPLETED,
                DailyChallengeHomePresenter.cardState(true, last.answered, last.total, last.completed),
            )
            val completion = engine.getCompletion(userId, gen.challenge.localDate)
            assertNotNull("${exam.name}: completion summary available", completion)
            assertEquals("${exam.name}: completion covers all 5", 5, completion!!.total)

            // #7 — close & reopen: a NEW engine on the SAME store returns the SAME, still-completed
            // challenge and generates nothing new (state preserved).
            val reopened = testEngine(dao, content).getOrCreateToday(userId, exam, utc, t0)
            assertNotNull("${exam.name}: reopen returns a challenge", reopened)
            assertEquals(
                "${exam.name}: reopen returns the SAME challenge",
                gen.challenge.questionIdsCsv, reopened!!.challenge.questionIdsCsv,
            )
            assertTrue("${exam.name}: still COMPLETED after reopen", reopened.completed)
            assertEquals("${exam.name}: exactly one challenge for the day", 1, dao.countChallengesForUser(userId))

            println("[LIFECYCLE ${exam.name}] PASS generate=5 answered=5 completed=true score=${last.challenge.score} reopen=same+completed")
        }
    }
}
