package com.edumio.app.db

import com.edumio.app.dailychallenge.DailyChallengeBlueprint
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release safety for image questions: every withheld question is a REAL id in the shipped bank, is
 * actually stamped unservable by the seeder, and withholding them still leaves every exam with far
 * more than enough eligible questions to build a five-question challenge in every blueprint section.
 */
class UnservableQuestionsTest {

    private data class Bank(val exam: String, val json: String, val examType: String)

    private val banks = listOf(
        Bank("IMAT", "imat/imat_questions.json", "IMAT"),
        Bank("TIL-I", "til_i/questions.json", "TIL_I"),
        Bank("CEnT-S", "cents_s/questions.json", "CENT_S"),
    )

    private fun assetsRoot(): File = listOf(
        File("src/main/assets"), File("app/src/main/assets"), File("../app/src/main/assets"),
    ).firstOrNull { it.isDirectory } ?: error("assets root not found")

    private fun idsOf(bank: Bank): Set<String> {
        val arr = JSONArray(File(assetsRoot(), bank.json).readText(Charsets.UTF_8))
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }
            .filter { it.isNotBlank() }.toSet()
    }

    @Test
    fun everyWithheldIdActuallyExistsInAShippedBank() {
        val all = banks.flatMap { idsOf(it) }.toSet()
        val unknown = UnservableQuestions.ALL.filter { it !in all }
        assertTrue("withheld ids must be real questions, not typos: $unknown", unknown.isEmpty())
        println("[UNSERVABLE] ${UnservableQuestions.ALL.size} questions withheld, all resolved in the banks")
    }

    @Test
    fun theSeederStampsThemUnservable_andLeavesEveryoneElseServable() {
        val root = assetsRoot()
        for (bank in banks) {
            val json = File(root, bank.json).readText(Charsets.UTF_8)
            val rows = if (bank.examType == "IMAT") DbSeeder.parseImatQuestions(json)
            else DbSeeder.parseDailyChallengeQuestions(json, bank.examType)

            val stamped = rows.filter { it.unservableReason == UnservableQuestions.REASON }.map { it.id }.toSet()
            val expected = UnservableQuestions.ALL.filter { it in rows.map { r -> r.id }.toSet() }.toSet()
            assertEquals("${bank.exam}: exactly the withheld ids are stamped", expected, stamped)

            // Nothing else may be accidentally quarantined.
            val otherUnservable = rows.filter {
                !it.unservableReason.isNullOrBlank() && it.unservableReason != UnservableQuestions.REASON
            }
            assertTrue("${bank.exam}: no unexpected unservable stamps", otherUnservable.isEmpty())
            println("[UNSERVABLE ${bank.exam}] withheld=${stamped.size} of ${rows.size}")
        }
    }

    @Test
    fun allThreeExamsStillHavePlentyOfEligibleQuestionsInEverySection() {
        val root = assetsRoot()
        val examTypes = mapOf(
            "IMAT" to com.edumio.app.core.ExamType.IMAT,
            "TIL_I" to com.edumio.app.core.ExamType.TIL_I,
            "CENT_S" to com.edumio.app.core.ExamType.CENT_S,
        )
        for (bank in banks) {
            val json = File(root, bank.json).readText(Charsets.UTF_8)
            val rows = if (bank.examType == "IMAT") DbSeeder.parseImatQuestions(json)
            else DbSeeder.parseDailyChallengeQuestions(json, bank.examType)

            // Exactly what QuestionDao.getDailyCandidatePool admits.
            val servable = rows.filter { it.isActive && it.unservableReason.isNullOrBlank() }
            val exam = examTypes.getValue(bank.examType)
            val counts = LinkedHashMap<String, Int>()
            for (section in DailyChallengeBlueprint.sections(exam)) {
                val n = servable.count { it.subject == section }
                counts[section] = n
                assertTrue("${bank.exam}/$section must still have candidates after withholding", n > 0)
            }
            val total = servable.size
            assertTrue(
                "${bank.exam}: needs far more than 5 servable questions (had $total)",
                total >= DailyChallengeBlueprint.CHALLENGE_SIZE * 20,
            )
            println("[ELIGIBLE ${bank.exam}] servable=$total of ${rows.size} sections=$counts")
        }
    }

    @Test
    fun withheldQuestionsAreNotDeleted_onlyWithheld() {
        val root = assetsRoot()
        // They must still be present as records so a corrected asset can reactivate them later.
        for (bank in banks) {
            val json = File(root, bank.json).readText(Charsets.UTF_8)
            val rows = if (bank.examType == "IMAT") DbSeeder.parseImatQuestions(json)
            else DbSeeder.parseDailyChallengeQuestions(json, bank.examType)
            val ids = rows.map { it.id }.toSet()
            UnservableQuestions.ALL.filter { it in ids }.forEach { withheld ->
                val row = rows.first { it.id == withheld }
                assertNotNull("${bank.exam}: $withheld record retained", row)
                assertTrue("${bank.exam}: $withheld stays active (withheld, not deleted)", row.isActive)
                assertEquals(UnservableQuestions.REASON, row.unservableReason)
            }
        }
    }

    @Test
    fun withholdingIsDeliberatelySmall() {
        // A guard against over-blocking: this is a targeted safety list, not a content purge.
        assertTrue("withheld set should stay small (was ${UnservableQuestions.ALL.size})", UnservableQuestions.ALL.size < 40)
        assertFalse("a servable, merely-cosmetic figure must not be withheld",
            UnservableQuestions.isUnservable("imat_2023_past_paper_chemistry_041"))
        assertTrue(UnservableQuestions.isUnservable("imat_2023_past_paper_chemistry_047"))
    }
}
