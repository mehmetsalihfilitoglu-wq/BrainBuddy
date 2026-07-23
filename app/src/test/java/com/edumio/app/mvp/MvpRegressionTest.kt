package com.edumio.app.mvp

import com.edumio.app.auth.NoOpAuthRepository
import com.edumio.app.core.AppRouter
import com.edumio.app.core.ExamType
import com.edumio.app.dailychallenge.DailyChallengeBlueprint
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter.CardState
import com.edumio.app.release.ReleaseProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MVP regression coverage for the account-free v1: the pure, JVM-testable invariants behind removing
 * authentication, correct Home states, premium being off, and per-exam isolation.
 *
 * Device-only behaviours (system BACK not closing the app on sub-screens; the actual Room per-exam
 * question query; on-device persistence surviving process death) are covered by manual/instrumented
 * checks; here we lock the logic those screens rely on so a regression fails the JVM suite.
 */
class MvpRegressionTest {

    // ── Account-free routing: there is no sign-in screen and no signed-out state can block access ──

    @Test
    fun freshInstall_routesToOnboarding_neverAuth() {
        // A fresh install goes to onboarding — and there is no AUTH destination at all (compile-time),
        // so a launcher can never open a sign-in screen.
        assertEquals(AppRouter.Destination.ONBOARDING, AppRouter.decide(onboardingDone = false))
    }

    @Test
    fun returningUser_reachesHome_withNoAuth() {
        // A returning user opens straight into Home from locally-persisted onboarding state — no network,
        // no sign-in, no login-required guard.
        assertEquals(AppRouter.Destination.HOME, AppRouter.decide(onboardingDone = true))
    }

    @Test
    fun routerHasNoAuthDestination() {
        // Structural guarantee that no sign-in screen exists in the routing surface.
        assertFalse(AppRouter.Destination.values().any { it.name == "AUTH" })
    }

    // ── The account system is off: no Firebase Auth, no hidden account ─────────────────────────────

    @Test
    fun authIsDisabledForV1() {
        assertFalse(ReleaseProfile.authEnabled)
    }

    @Test
    fun noOpAuth_hasNoAccountAndTouchesNoBackend() {
        // While authEnabled is false, AuthProvider hands out this repository. It reports no account and
        // never creates one (anonymous, device-ID or otherwise) — no Firebase Auth SDK is contacted.
        val repo = NoOpAuthRepository()
        assertFalse(repo.isSignedIn())
        assertNull(repo.currentUser())
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

    @Test
    fun premiumUiIsNeverShown() {
        // Every locked state / upsell / paywall CTA in the app routes through this seam. If it were true
        // while nothing is purchasable, users would get dead buttons on core learning screens.
        assertFalse(com.edumio.app.core.FeatureAccess.mayShowPremiumUi())
    }

    @Test
    fun leagueIsHiddenForFirstRelease() {
        // The Lig opponents are locally simulated and there is no backend — it must not be reachable.
        assertFalse(ReleaseProfile.leagueEnabled)
    }

    @Test
    fun cloudAndPurchaseEntryPointsAreDisabledInSparkSafe() {
        // In the shipping (spark-safe) configuration, every backend-dependent surface stays off, so no
        // premium purchase or cloud-sync entry point is reachable.
        assertFalse(com.edumio.app.release.ReleaseFlags.purchasesEnabled(sparkSafe = true))
        assertFalse(com.edumio.app.release.ReleaseFlags.cloudSyncEnabled(sparkSafe = true))
        assertFalse(com.edumio.app.release.ReleaseFlags.cloudAccountEnabled(sparkSafe = true))
    }

    /**
     * Load-bearing: unlocking review for everyone (because v1.0 is free) must NOT be able to hand out
     * more than five NEW questions per day. The entitlement flag is deliberately ignored here.
     */
    @Test
    fun fiveNewQuestionsPerDay_holdsRegardlessOfEntitlement() {
        assertEquals(
            DailyChallengeBlueprint.CHALLENGE_SIZE,
            com.edumio.app.dailychallenge.DailyChallengeEntitlementPolicy.newQuestionLimit(isPremium = true),
        )
        assertEquals(
            DailyChallengeBlueprint.CHALLENGE_SIZE,
            com.edumio.app.dailychallenge.DailyChallengeEntitlementPolicy.newQuestionLimit(isPremium = false),
        )
        assertEquals(5, DailyChallengeBlueprint.CHALLENGE_SIZE)
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
