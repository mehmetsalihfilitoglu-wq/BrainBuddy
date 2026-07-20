package com.edumio.app.core

/**
 * Pure launcher-routing decision — no Android deps, so every cold-start case is unit-testable.
 *
 * Authentication is mandatory (real account, no anonymous): the [Destination.HOME] target is reachable
 * ONLY when the user is signed in. A signed-out user is always sent to sign in ([Destination.AUTH]) or,
 * on a fresh install, through the onboarding wizard which itself gates on sign-in.
 */
object AppRouter {

    enum class Destination { ONBOARDING, AUTH, HOME }

    fun decide(signedIn: Boolean, onboardingDone: Boolean): Destination = when {
        signedIn && onboardingDone -> Destination.HOME
        signedIn && !onboardingDone -> Destination.ONBOARDING // signed in, still needs exam + name
        onboardingDone -> Destination.AUTH                    // returning user, signed out → sign in
        else -> Destination.ONBOARDING                        // fresh install → welcome → sign in → setup
    }
}
