package com.edumio.app.auth

/**
 * Account-free [AuthRepository] for the v1 Google Play release.
 *
 * EDUmio v1 genuinely operates WITHOUT a user account: there is no sign-in/up UI, no login-required
 * navigation guard, and — critically — no Firebase Authentication is ever contacted at runtime. This
 * implementation is what [AuthProvider] hands out while [com.edumio.app.release.ReleaseProfile.authEnabled]
 * is false, so every caller sees "no account" as the normal, permanent state.
 *
 * It creates NO account of any kind (no anonymous Firebase account, no device-ID account, no silent
 * account). [currentUser] is always null; local user state (onboarding, display name, exam, Daily
 * Challenge, wrong-question history, stats) lives entirely in the on-device persistence layer.
 *
 * The email/federated operations are never invoked from the UI in v1; they fail-closed with a stable
 * PROVIDER_UNAVAILABLE code purely to satisfy the interface, and touch no SDK.
 */
class NoOpAuthRepository : AuthRepository {

    override fun currentUser(): AuthUser? = null
    override fun isSignedIn(): Boolean = false
    override fun signOut() { /* no account to end */ }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): AuthResult = unavailable()
    override suspend fun signInWithEmail(email: String, password: String): AuthResult = unavailable()
    override suspend fun sendPasswordReset(email: String): OpResult = unavailableOp()
    override suspend fun signInWithProvider(provider: AuthProviderType, token: FederatedToken): AuthResult = unavailable()
    override suspend fun sendEmailVerification(): OpResult = unavailableOp()
    override suspend fun reloadUser(): AuthUser? = null
    override suspend fun deleteAccount(): OpResult = OpResult.Success // no remote account exists to delete

    private fun unavailable(): AuthResult =
        AuthResult.Error(AuthErrorCode.PROVIDER_UNAVAILABLE, "")

    private fun unavailableOp(): OpResult =
        OpResult.Error(AuthErrorCode.PROVIDER_UNAVAILABLE, "")
}
