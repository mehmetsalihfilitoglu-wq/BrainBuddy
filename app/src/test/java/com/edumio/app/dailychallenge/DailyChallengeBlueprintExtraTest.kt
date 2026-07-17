package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Extra blueprint/selection coverage the release audit calls out: long-run drift, nested rotation,
 * and answer-letter unpredictability. Pure. */
class DailyChallengeBlueprintExtraTest {

    private val exams = listOf(ExamType.IMAT, ExamType.TIL_I, ExamType.CENT_S)

    @Test
    fun convergesOver1000DaysWithBoundedDrift() {
        val days = 1000
        for (exam in exams) {
            val exp = HashMap<String, Double>(); val act = HashMap<String, Double>()
            val cum = HashMap<String, Int>()
            var totalSlots = 0
            repeat(days) {
                val a = DailyChallengeBlueprint.allocate(exam, exp, act)
                assertEquals("every day allocates exactly 5 for $exam", 5, a.values.sum())
                a.forEach { (s, n) -> cum[s] = (cum[s] ?: 0) + n }
                totalSlots += a.values.sum()
            }
            assertEquals(days * 5, totalSlots)
            for (s in DailyChallengeBlueprint.sections(exam)) {
                val ideal = DailyChallengeBlueprint.perChallengeTarget(exam, s) * days
                val drift = abs((cum[s] ?: 0) - ideal)
                assertTrue("1000-day drift must stay < 1 for $exam/$s (was $drift)", drift < 1.0)
            }
        }
    }

    @Test
    fun tilBasicTechnicalRotatesComputerScienceAndRepresentation() {
        // basic_technical bundles {computer_science 3, representation 2}: both must recur long-term.
        val exp = HashMap<String, Double>(); val act = HashMap<String, Double>()
        var cs = 0; var repn = 0
        repeat(300) {
            val a = DailyChallengeBlueprint.allocateSubSections("basic_technical", 2, exp, act)
            cs += a["computer_science"] ?: 0
            repn += a["representation"] ?: 0
        }
        assertTrue("Computer Science must appear long-term", cs > 0)
        assertTrue("Representation must appear long-term", repn > 0)
        assertEquals("sub-allocation conserves fed slots", 600, cs + repn)
        // 3:2 weighting → CS should get the larger share.
        assertTrue("CS should dominate per 3:2 weight", cs > repn)
    }

    @Test
    fun tilLogicReadingKeepsBothPresentAt6To4() {
        val exp = HashMap<String, Double>(); val act = HashMap<String, Double>()
        var logic = 0; var reading = 0
        repeat(300) {
            val a = DailyChallengeBlueprint.allocateSubSections("logic_reading", 2, exp, act)
            logic += a["logic"] ?: 0
            reading += a["reading"] ?: 0
        }
        assertTrue(reading > 0 && logic > 0)
        assertEquals(600, logic + reading)
        assertTrue("logic dominates per 6:4 weight", logic > reading)
    }

    @Test
    fun answerLetterIsNotAlwaysTheSamePosition() {
        // The correct answer's display slot must vary across questions (no predictable letter bias).
        val positions = IntArray(5)
        for (i in 0 until 400) {
            val id = "q_bio_$i"
            val answerOriginalIndex = i % 5           // simulate answers spread across original indices
            val pos = DailyChallengeOptions.displayIndexOfAnswer(id, 5, answerOriginalIndex)
            positions[pos]++
        }
        // Every slot A..E should be used at least sometimes; none should dominate absurdly.
        assertTrue("all five display slots are used", positions.all { it > 0 })
        assertTrue("no single slot holds >45% of correct answers", positions.all { it < 400 * 0.45 })
    }
}
