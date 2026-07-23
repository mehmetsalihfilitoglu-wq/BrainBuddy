package com.edumio.app.core

/**
 * The exact v1 onboarding flow, in order — the single source of truth used by
 * [com.edumio.app.OnboardingWizardActivity] and by regression tests.
 *
 * v1 collects only what the first release needs: Welcome → Exam selection → Name. The destination-city,
 * timeline/application-year and Italian-level steps were removed from the flow (their layouts remain in
 * the repo, unused). Because the wizard builds its ViewFlipper strictly from [ORDER], those screens are
 * genuinely unreachable, not merely hidden — a test asserting [ORDER] guarantees it.
 */
object OnboardingSteps {

    enum class Step { WELCOME, EXAM, NAME }

    val ORDER: List<Step> = listOf(Step.WELCOME, Step.EXAM, Step.NAME)
}
