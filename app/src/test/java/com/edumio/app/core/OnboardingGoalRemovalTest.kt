package com.edumio.app.core

import com.edumio.app.dailychallenge.DailyChallengeBlueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for two rc4 fixes:
 *  1) the daily-goal picker (5 / 15 / 30 soru) is gone from onboarding — the flow is exactly
 *     Welcome → Exam → Name, the daily target is a FIXED 5 and is not a stored user preference; and
 *  2) the app is exam-framed — the active study area is presented as its exam (IMAT / TIL-I / CEnT-S),
 *     never as a career journey or degree ("Mühendislik Yolculuğu" / "Ingegneria").
 */
class OnboardingGoalRemovalTest {

    // ── onboarding has NO goal step; the flow is Welcome → Exam → Name ─────────────────────────────
    @Test
    fun onboardingFlow_isWelcomeExamName_withNoGoalStep() {
        assertEquals(
            listOf(
                OnboardingSteps.Step.WELCOME,
                OnboardingSteps.Step.EXAM,
                OnboardingSteps.Step.NAME,
            ),
            OnboardingSteps.ORDER,
        )
        assertEquals("exactly three onboarding steps (no goal step)", 3, OnboardingSteps.ORDER.size)
    }

    // ── the daily limit is fixed at 5 — it is NOT chosen in onboarding ─────────────────────────────
    @Test
    fun dailyLimitIsFixedFive_notAUserChoice() {
        assertEquals(5, DailyChallengeBlueprint.CHALLENGE_SIZE)
    }

    // ── UserGoal no longer carries a daily-goal target (compile-time proof it was removed) ─────────
    @Test
    fun userGoal_hasNoDailyGoalTarget() {
        val goal = UserGoal(
            careerPath = CareerPath.ENGINEERING,
            destinationCities = emptyList(),
            applicationYear = 2026,
            italianLevel = ItalianLevel.A0,
            studentName = "Öğrenci",
        )
        // The goal maps straight to an exam; there is no per-user question-count field to read.
        assertEquals(ExamType.TIL_I, goal.examType)
    }

    // ── exam-framed identity: the three offered exams, never a career/degree label ─────────────────
    @Test
    fun examCodes_areExamFramed_notCareerLabels() {
        assertEquals("IMAT", ExamType.IMAT.code)
        assertEquals("TIL-I", ExamType.TIL_I.code)
        assertEquals("CEnT-S", ExamType.CENT_S.code)

        // The representative careers onboarding uses map 1:1 to the three exams.
        assertEquals(ExamType.IMAT, CareerPath.MEDICINE.examType)
        assertEquals(ExamType.TIL_I, CareerPath.ENGINEERING.examType)
        assertEquals(ExamType.CENT_S, CareerPath.ECONOMICS.examType)

        // What Home/Profile now render (exam.code) must never contain a career-journey / degree word.
        for (code in listOf(ExamType.IMAT.code, ExamType.TIL_I.code, ExamType.CENT_S.code)) {
            assertFalse("exam code must not read as a career journey", code.contains("Yolculuğu"))
            assertFalse("exam code must not be a degree name", code.contains("Ingegneria"))
            assertTrue("exam code is short (a code, not a sentence)", code.length <= 6)
        }
    }
}
