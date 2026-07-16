package com.mioacademy.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the Daily Challenge hard-invariant guardrails (no Android/DB deps). */
class DailyChallengeGuardrailsTest {

    @Test
    fun neverMoreThanFiveNewQuestions() {
        assertTrue(DailyChallengeGuardrails.checkNewQuestionCount(5).isEmpty())
        assertTrue(DailyChallengeGuardrails.checkNewQuestionCount(3).isEmpty())
        assertFalse("6 must violate", DailyChallengeGuardrails.checkNewQuestionCount(6).isEmpty())
    }

    @Test
    fun premiumMustNotChangeNewQuestionCount() {
        assertTrue(DailyChallengeGuardrails.checkPremiumNeutralCount(5, 5).isEmpty())
        assertFalse("premium adding questions must violate",
            DailyChallengeGuardrails.checkPremiumNeutralCount(5, 8).isEmpty())
    }

    @Test
    fun reviewNeverContainsNewQuestions() {
        val legal = listOf(
            QuestionLearnState.INCORRECT_ONCE,
            QuestionLearnState.INCORRECT_MULTIPLE,
            QuestionLearnState.NEEDS_REVISION,
            QuestionLearnState.FORGOTTEN,
        )
        assertTrue(DailyChallengeGuardrails.checkReviewHasNoNewQuestions(legal).isEmpty())
        // NEVER_SEEN / SEEN_ONCE / CORRECT / MASTERED are not review-eligible → violation if present
        assertFalse(DailyChallengeGuardrails.checkReviewHasNoNewQuestions(
            listOf(QuestionLearnState.NEVER_SEEN)).isEmpty())
        assertFalse(DailyChallengeGuardrails.checkReviewHasNoNewQuestions(
            listOf(QuestionLearnState.SEEN_ONCE)).isEmpty())
    }

    @Test
    fun retirementMeansServedLeavesUnseenPool() {
        assertTrue(DailyChallengeGuardrails.checkRetirement(
            servedIds = listOf("a", "b"), remainingUnseenIds = listOf("c", "d")).isEmpty())
        assertFalse("a still unseen after being served must violate",
            DailyChallengeGuardrails.checkRetirement(
                servedIds = listOf("a", "b"), remainingUnseenIds = listOf("a", "c")).isEmpty())
    }

    @Test
    fun noCrossExamContamination() {
        assertTrue(DailyChallengeGuardrails.checkExamIsolation("TIL_I", listOf("TIL_I", "TIL_I")).isEmpty())
        assertFalse("IMAT question inside a TIL_I challenge must violate",
            DailyChallengeGuardrails.checkExamIsolation("TIL_I", listOf("TIL_I", "IMAT")).isEmpty())
    }

    @Test
    fun noReminderFiresAfterCompletion() {
        assertTrue(DailyChallengeGuardrails.checkNoReminderAfterCompletion(completedToday = false, wouldFire = true).isEmpty())
        assertTrue(DailyChallengeGuardrails.checkNoReminderAfterCompletion(completedToday = true, wouldFire = false).isEmpty())
        assertFalse("firing after completion must violate",
            DailyChallengeGuardrails.checkNoReminderAfterCompletion(completedToday = true, wouldFire = true).isEmpty())
    }

    @Test
    fun evaluateChallengeAggregatesAllViolations() {
        val ok = DailyChallengeGuardrails.evaluateChallenge(
            newQuestionCount = 5, challengeExam = "CENT_S",
            questionExamTypes = listOf("CENT_S", "CENT_S", "CENT_S", "CENT_S", "CENT_S"),
            servedIds = listOf("1", "2", "3", "4", "5"), remainingUnseenIds = listOf("6", "7"),
        )
        assertTrue(ok.ok)
        assertEquals(0, ok.violations.size)

        val bad = DailyChallengeGuardrails.evaluateChallenge(
            newQuestionCount = 7, challengeExam = "CENT_S",
            questionExamTypes = listOf("CENT_S", "IMAT"),
            servedIds = listOf("1"), remainingUnseenIds = listOf("1"),
        )
        assertFalse(bad.ok)
        assertEquals("size + isolation + retirement", 3, bad.violations.size)
    }
}
