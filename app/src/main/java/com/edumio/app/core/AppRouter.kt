package com.edumio.app.core

/**
 * Pure launcher-routing decision — no Android deps, so every cold-start case is unit-testable.
 *
 * EDUmio v1 is account-free: routing depends ONLY on locally-persisted onboarding state. There is no
 * sign-in screen and no login-required guard — a user (whether "signed out" or never-signed-in) is never
 * blocked from the app, and a returning user opens straight into Home with no network required.
 */
object AppRouter {

    enum class Destination { ONBOARDING, HOME }

    fun decide(onboardingDone: Boolean): Destination =
        if (onboardingDone) Destination.HOME else Destination.ONBOARDING
}
