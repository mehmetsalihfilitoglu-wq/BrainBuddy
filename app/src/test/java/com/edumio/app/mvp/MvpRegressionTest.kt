package com.edumio.app.mvp

import com.edumio.app.core.AppRouter
import com.edumio.app.core.ExamType
import com.edumio.app.dailychallenge.DailyChallengeBlueprint
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter.CardState
import com.edumio.app.release.ReleaseProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MVP regression coverage for the product-hardening round: the pure, JVM-testable invariants behind
 * mandatory authentication, correct Home states, premium being off, and per-exam isolation.
 *
 * Device-only behaviours (system BACK not closing the app on sub-screens; the actual Room per-exam
 * question query) are covered by manual/instrumented checks; here we lock the logic those screens rely
 * on so a regression fails the JVM suite.
 */
class MvpRegressionTest {

    // ── Authentication gate: Home is unreachable while signed out (no anonymous auth) ──────────────

    @Test
    fun unauthenticatedUser_neverRoutesToHome() {
        // Whatever the onboarding state, a signed-out user must NOT land on Home.
        assertNotEquals(AppRouter.Destination.HOME, AppRouter.decide(signedIn = false, onboardingDone = true))
        assertNotEquals(AppRouter.Destination.HOME, AppRouter.decide(signedIn = false, onboardingDone = false))
    }

    @Test
    fun returningSignedOutUser_routesToAuth() {
        assertEquals(AppRouter.Destination.AUTH, AppRouter.decide(signedIn = false, onboardingDone = true))
    }

    @Test
    fun freshInstall_routesToOnboarding() {
        assertEquals(AppRouter.Destination.ONBOARDING, AppRouter.decide(signedIn = false, onboardingDone = false))
    }

    @Test
    fun signedInButNotSetUp_routesToOnboarding() {
        assertEquals(AppRouter.Destination.ONBOARDING, AppRouter.decide(signedIn = true, onboardingDone = false))
    }

    @Test
    fun signedInAndSetUp_routesToHome() {
        assertEquals(AppRouter.Destination.HOME, AppRouter.decide(signedIn = true, onboardingDone = true))
    }

    // ── Home states: a brand-new user is never shown "completed" with zero progress ────────────────

    @Test
    fun newUser_dailyCard_isAvailableNotCompleted() {
        val state = DailyChallengeHomePresenter.cardState(supported = true, answered = 0, total = 5, completed = false)
        assertEquals(CardState.AVAILABLE, state)
        assertNotEquals(CardState.COMPLETED, state)
    }

    @Test
    fun unsupportedExam_isDistinctFromCompleted() {
        // An active area with no daily-challenge blueprint must read as UNAVAILABLE (→ practice invite),
        // never COMPLETED — the exact "0 progress but says done" bug we removed.
        val state = DailyChallengeHomePresenter.cardState(supported = false, answered = 0, total = 5, completed = false)
        assertEquals(CardState.UNAVAILABLE, state)
        assertNotEquals(CardState.COMPLETED, state)
    }

    @Test
    fun completedOnlyWhenActuallyAnsweredAll() {
        assertEquals(CardState.COMPLETED,
            DailyChallengeHomePresenter.cardState(supported = true, answered = 5, total = 5, completed = false))
        assertEquals(CardState.IN_PROGRESS,
            DailyChallengeHomePresenter.cardState(supported = true, answered = 2, total = 5, completed = false))
    }

    // ── Premium is entirely off in v1.0 (no premium route should open) ─────────────────────────────

    @Test
    fun premiumIsDisabled() {
        assertFalse(ReleaseProfile.premiumEnabled)
    }

    // ── Per-exam isolation: only the three shipped exams have a challenge blueprint; a non-shipped ──
    //    exam is unsupported (→ no challenge is ever formed, so it can never fall back to a legacy pool).

    @Test
    fun onlyShippedExamsAreSupported() {
        assertTrue(DailyChallengeBlueprint.isSupported(ExamType.IMAT))
        assertTrue(DailyChallengeBlueprint.isSupported(ExamType.TIL_I))
        assertTrue(DailyChallengeBlueprint.isSupported(ExamType.CENT_S))
        // Legacy / not-yet-shipped exams have no blueprint → unsupported.
        assertFalse(DailyChallengeBlueprint.isSupported(ExamType.UNKNOWN))
        assertFalse(DailyChallengeBlueprint.isSupported(ExamType.TOLC_I))
    }

    @Test
    fun eachExamBlueprintUsesOnlyItsOwnSections() {
        // The three exams' section vocabularies must not silently share the SAME full section set,
        // which would let one exam's slots be filled from another's questions.
        val imat = DailyChallengeBlueprint.sections(ExamType.IMAT).toSet()
        val til = DailyChallengeBlueprint.sections(ExamType.TIL_I).toSet()
        val cent = DailyChallengeBlueprint.sections(ExamType.CENT_S).toSet()
        assertNotEquals(imat, til)
        assertNotEquals(imat, cent)
        assertNotEquals(til, cent)
        // IMAT's biology/chemistry emphasis must not be identical to TIL-I's engineering sections.
        assertTrue(imat.contains("biology"))
        assertFalse(til.contains("biology"))
    }
}
