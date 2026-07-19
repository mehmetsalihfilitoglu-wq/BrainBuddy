package com.edumio.app.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The verification gate must lock the Daily Challenge ONLY for a signed-in, unverified email account. */
class EmailVerificationPolicyTest {

    private fun user(provider: AuthProviderType, verified: Boolean) =
        AuthUser("u1", "a@b.com", "A", isEmailVerified = verified, provider = provider)

    @Test fun anonymousIsNeverGated() {
        assertTrue(EmailVerificationPolicy.dailyChallengeUnlocked(null))
        assertFalse(EmailVerificationPolicy.requiresVerification(null))
    }

    @Test fun unverifiedEmailAccountIsLocked() {
        val u = user(AuthProviderType.EMAIL, verified = false)
        assertTrue(EmailVerificationPolicy.requiresVerification(u))
        assertFalse(EmailVerificationPolicy.dailyChallengeUnlocked(u))
    }

    @Test fun verifiedEmailAccountIsUnlocked() {
        val u = user(AuthProviderType.EMAIL, verified = true)
        assertFalse(EmailVerificationPolicy.requiresVerification(u))
        assertTrue(EmailVerificationPolicy.dailyChallengeUnlocked(u))
    }

    @Test fun googleAccountIsNeverGated() {
        // Google arrives pre-verified; even if the flag were somehow false, the provider is not gated.
        assertTrue(EmailVerificationPolicy.dailyChallengeUnlocked(user(AuthProviderType.GOOGLE, verified = true)))
        assertFalse(EmailVerificationPolicy.requiresVerification(user(AuthProviderType.GOOGLE, verified = false)))
    }

    @Test fun appShellAlwaysBrowsable() {
        assertTrue(EmailVerificationPolicy.canBrowseAppShell(null))
        assertTrue(EmailVerificationPolicy.canBrowseAppShell(user(AuthProviderType.EMAIL, verified = false)))
    }
}
