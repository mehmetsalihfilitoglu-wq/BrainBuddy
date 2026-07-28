package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import com.edumio.app.db.QuestionCandidateRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Pure JVM tests for the Daily Challenge blueprint balancer + selector (no Android/DB deps). */
class DailyChallengeBlueprintTest {

    private val exams = listOf(ExamType.IMAT, ExamType.TIL_I, ExamType.CENT_S)

    @Test
    fun everyChallengeAllocatesExactlyFive() {
        for (exam in exams) {
            val exp = HashMap<String, Double>(); val act = HashMap<String, Double>()
            repeat(400) {
                val a = DailyChallengeBlueprint.allocate(exam, exp, act)
                assertEquals("sum must be exactly 5 for $exam", 5, a.values.sum())
                assertTrue("no negative allocations", a.values.all { it >= 0 })
            }
        }
    }

    @Test
    fun convergesToOfficialProportionsWithoutAccumulatingDrift() {
        val days = 365
        for (exam in exams) {
            val exp = HashMap<String, Double>(); val act = HashMap<String, Double>()
            val cum = HashMap<String, Int>()
            repeat(days) {
                val a = DailyChallengeBlueprint.allocate(exam, exp, act)
                a.forEach { (s, n) -> cum[s] = (cum[s] ?: 0) + n }
            }
            for (s in DailyChallengeBlueprint.sections(exam)) {
                val ideal = DailyChallengeBlueprint.perChallengeTarget(exam, s) * days
                val drift = abs((cum[s] ?: 0) - ideal)
                assertTrue("drift must stay < 1 for $exam/$s (was $drift)", drift < 1.0)
            }
        }
    }

    @Test
    fun subSectionRotationKeepsReadingAndLogicPresent() {
        val exp = HashMap<String, Double>(); val act = HashMap<String, Double>()
        var reading = 0; var logic = 0
        repeat(200) {
            val a = DailyChallengeBlueprint.allocateSubSections("logic_reading", 2, exp, act)
            reading += a["reading"] ?: 0; logic += a["logic"] ?: 0
        }
        assertTrue("Reading must appear long-term", reading > 0)
        assertTrue("Logic must appear long-term", logic > 0)
        assertEquals("sub-allocation sums to fed slots", 400, reading + logic)
    }

    private fun row(i: Int, tier: String, topic: String) =
        QuestionCandidateRow("q$i", "math", 1, 0, "h$i", "n$i", "DAILY", "math", topic, tier, 2)

    @Test
    fun selectorReturnsDistinctSelectivePicks() {
        val rng = Random(1)
        val pool = (1..20).map { row(it, if (it <= 5) "ELITE" else if (it <= 12) "HARD" else "MEDIUM", "t${it % 5}") }
        val picked = DailyChallengeSelection.pickN(pool, 5, HashSet(), HashSet(), rng)
        assertEquals(5, picked.size)
        assertEquals("distinct ids", 5, picked.map { it.id }.toSet().size)
        assertEquals("distinct stems", 5, picked.map { it.stemHash }.toSet().size)
        assertTrue("prefers selective tiers", picked.count { it.qualityTier == "ELITE" || it.qualityTier == "HARD" } >= 4)
    }

    @Test
    fun selectorExcludesAlreadyUsedStems_retirementBehaviour() {
        val rng = Random(2)
        val pool = (1..3).map { row(it, "HARD", "t$it") }
        val usedStems = hashSetOf("h1", "h2")
        val picked = DailyChallengeSelection.pickN(pool, 5, HashSet(), usedStems, rng)
        assertEquals("only the unused stem remains", 1, picked.size)
        assertEquals("q3", picked[0].id)
    }
}
