package com.edumio.app.auth

/**
 * Pure product-policy for the email-verification gate (Phase 1). Kept as a testable unit so the rule is
 * unambiguous and covered before it is wired into any screen.
 *
 * **Documented product decision:** an authenticated **email/password** account must verify its email before
 * the Daily Challenge unlocks. Federated accounts (Google) arrive pre-verified and are never gated. The
 * **anonymous / local-fallback** experience (no signed-in account) is intentionally **NOT** gated — EDUmio
 * remains fully usable offline without an account, preserving the current experience. This gate therefore
 * only ever tightens access for a *signed-in but unverified email account*, never for anonymous users.
 */
object EmailVerificationPolicy {

    /** True only for a signed-in EMAIL account that has not verified its address yet. */
    fun requiresVerification(user: AuthUser?): Boolean =
        user != null && user.provider == AuthProviderType.EMAIL && !user.isEmailVerified

    /**
     * Whether the Daily Challenge is unlocked for [user]. Anonymous (null) → unlocked; verified email or a
     * federated provider → unlocked; unverified email account → locked.
     */
    fun dailyChallengeUnlocked(user: AuthUser?): Boolean = !requiresVerification(user)

    /** Account existence, onboarding and settings are always visible (only the challenge is gated). */
    fun canBrowseAppShell(@Suppress("UNUSED_PARAMETER") user: AuthUser?): Boolean = true
}
