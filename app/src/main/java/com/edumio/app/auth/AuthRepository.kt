package com.edumio.app.auth

/**
 * The complete authentication contract for Mioitalia — provider-agnostic and
 * backend-agnostic. [LocalAuthStubRepository] implements it fully on-device so
 * the app builds and works today; a `FirebaseAuthRepository` will implement the
 * same contract once `google-services.json` is added, with no changes to callers.
 *
 * Federated sign-in (Google now, Apple later) is split in two: the UI obtains a
 * provider ID token via a [CredentialProvider] (the SDK seam), then hands it here
 * to [signInWithProvider]. This keeps Play Services / Sign-In SDKs out of the
 * business layer.
 *
 * Every operation is study-area-independent: auth identifies the *account*;
 * per-study-area data isolation lives entirely in the sync/prefs layer.
 */
interface AuthRepository {

    // ── Session ────────────────────────────────────────────────────────────
    fun currentUser(): AuthUser?
    fun isSignedIn(): Boolean
    fun signOut()

    // ── Email & password ───────────────────────────────────────────────────
    suspend fun signUpWithEmail(email: String, password: String, displayName: String?): AuthResult
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun sendPasswordReset(email: String): OpResult

    // ── Federated (Google now, Apple later) ──────────────────────────────────
    /** [token] is the provider ID token obtained by a [CredentialProvider]. */
    suspend fun signInWithProvider(provider: AuthProviderType, token: FederatedToken): AuthResult

    // ── Email verification ───────────────────────────────────────────────────
    suspend fun sendEmailVerification(): OpResult
    /** Re-fetches the user from the source of truth (e.g. after verifying email). */
    suspend fun reloadUser(): AuthUser?

    // ── Account lifecycle (GDPR-ready) ───────────────────────────────────────
    /** Permanently deletes the account. May require a recent login on real backends. */
    suspend fun deleteAccount(): OpResult
}

/** An opaque provider credential (e.g. Google ID token + optional nonce for Apple). */
data class FederatedToken(val idToken: String, val nonce: String? = null, val accessToken: String? = null)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Error(val code: AuthErrorCode, val message: String) : AuthResult()
}

sealed class OpResult {
    object Success : OpResult()
    data class Error(val code: AuthErrorCode, val message: String) : OpResult()
}

/**
 * Stable, UI-mappable error codes so the presentation layer can show a helpful,
 * recoverable message (see error-state guidance) regardless of backend wording.
 */
enum class AuthErrorCode {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_IN_USE,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    USER_NOT_FOUND,
    EMAIL_NOT_VERIFIED,
    REQUIRES_RECENT_LOGIN,
    PROVIDER_UNAVAILABLE,
    NETWORK,
    UNKNOWN
}
