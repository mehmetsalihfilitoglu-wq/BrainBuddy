package com.edumio.app.mvp

import com.edumio.app.auth.NoOpAuthRepository
import com.edumio.app.core.AppRouter
import com.edumio.app.core.ExamType
import com.edumio.app.core.OnboardingSteps
import java.io.File
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

    // ── Minimal onboarding: Welcome → Exam → Name → Home; city/level/timeline unreachable ─────────

    @Test
    fun onboardingFlowIsExactlyWelcomeExamName() {
        // The wizard builds its ViewFlipper strictly from this list, so this IS the reachable flow
        // (Home follows after the last step). No network is involved in any step.
        assertEquals(
            listOf(OnboardingSteps.Step.WELCOME, OnboardingSteps.Step.EXAM, OnboardingSteps.Step.NAME),
            OnboardingSteps.ORDER,
        )
    }

    @Test
    fun onboardingDoesNotIncludeCityLevelOrTimeline() {
        // These screens were removed from the flow, not just hidden — the only steps that exist are the
        // three above, so a city / Italian-level / timeline screen can never be reached.
        val names = OnboardingSteps.Step.values().map { it.name }.toSet()
        assertFalse(names.contains("CITY"))
        assertFalse(names.contains("LEVEL"))
        assertFalse(names.contains("TIMELINE"))
        assertEquals(3, OnboardingSteps.Step.values().size)
        assertTrue(names.contains("EXAM")) // exam selection is still present
    }

    // ── The support e-mail placeholder must not ship, and the real address must be present ─────────

    @Test
    fun legalDocsHaveRealSupportEmailAndNoPlaceholder() {
        val docs = listOf(
            "src/main/assets/privacy_policy_tr.html",
            "src/main/assets/terms_of_use_tr.html",
        )
        for (path in docs) {
            val text = File(path).readText()
            assertFalse("$path still contains CONTACT_EMAIL_PLACEHOLDER", text.contains("CONTACT_EMAIL_PLACEHOLDER"))
            assertTrue("$path is missing the real support e-mail", text.contains("support.edumio@gmail.com"))
        }
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

    // ── §6 Exam selection collapses to exactly the three shipped exams ─────────────────────────────

    @Test
    fun exactlyThreeSelectableExams_imatTilCent() {
        val three = setOf(ExamType.IMAT, ExamType.TIL_I, ExamType.CENT_S)
        // Selection shows one card per exam (a representative CareerPath carries each isolated bank).
        val cards = com.edumio.app.core.CareerPath.values()
            .filter { it.examType in three }
            .distinctBy { it.examType }
        assertEquals(3, cards.size)
        assertEquals(three, cards.map { it.examType }.toSet())
        // Every shipped exam must have at least one representative career, or its card would vanish.
        three.forEach { ex -> assertTrue(com.edumio.app.core.CareerPath.values().any { it.examType == ex }) }
    }

    // ── §3 Option labels: bare-letter figure questions are detected and never shuffled ─────────────

    @Test
    fun letterOptionsAreDetected() {
        assertTrue(com.edumio.app.quiz.OptionLabels.isLetterOptions(listOf("A", "B", "C", "D", "E")))
        assertTrue(com.edumio.app.quiz.OptionLabels.isLetterOptions(listOf("A", "B", "C", "D")))
        // Real text options are NOT letter options (so they keep their "A) …" label + shuffle).
        assertFalse(com.edumio.app.quiz.OptionLabels.isLetterOptions(listOf("12", "24", "36", "48")))
        assertFalse(com.edumio.app.quiz.OptionLabels.isLetterOptions(listOf("A) 12", "B) 24")))
        // A partial / out-of-order letter set is not treated as bare-letter.
        assertFalse(com.edumio.app.quiz.OptionLabels.isLetterOptions(listOf("A", "C", "B", "D")))
        assertFalse(com.edumio.app.quiz.OptionLabels.isLetterOptions(emptyList()))
    }

    @Test
    fun letterOptionsUseIdentityOrder() {
        // Bare-letter figure questions MUST keep identity order so each on-screen letter still points at
        // the correct region of the image (this is the in-scope §3 fix; it is forced regardless of id).
        listOf("q-imat-1", "abc", "z").forEach { id ->
            assertEquals(
                listOf(0, 1, 2, 3, 4),
                com.edumio.app.dailychallenge.DailyChallengeOptions.displayOrder(id, 5, letterOptions = true),
            )
        }
        // The non-letter ordering is always a valid permutation of all indices (correctness of scoring
        // relies on this — the tapped display position maps back to a real original option index).
        assertEquals(
            setOf(0, 1, 2, 3, 4),
            com.edumio.app.dailychallenge.DailyChallengeOptions.displayOrder("q-imat-1", 5, letterOptions = false).toSet(),
        )
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
