package com.mioacademy.app.auth

import android.app.Activity

/**
 * The SDK seam for federated sign-in. The UI asks a [CredentialProvider] for a
 * provider ID token; the real implementation (Google Identity / Credential
 * Manager, later Sign in with Apple) lives behind this interface so no vendor
 * SDK leaks into Activities or the auth business layer.
 *
 * Until the Google Sign-In SDK is added, [StubCredentialProvider] reports the
 * provider as unavailable — the email/password path remains fully functional,
 * and the Google button can surface an honest "coming soon" state.
 */
interface CredentialProvider {
    /**
     * Which federated providers this build can actually complete a sign-in with.
     * The UI renders a provider button only when its type is in this set, so no
     * broken/fake buttons ever appear. Empty until a real SDK is wired in.
     */
    fun supportedProviders(): Set<AuthProviderType> = emptySet()

    /** Launches the provider's account-picker/consent UI and returns a token. */
    suspend fun requestToken(activity: Activity, provider: AuthProviderType): TokenResult
}

sealed class TokenResult {
    data class Success(val token: FederatedToken) : TokenResult()
    object Cancelled : TokenResult()
    data class Unavailable(val provider: AuthProviderType) : TokenResult()
    data class Error(val message: String) : TokenResult()
}

/** No-op provider used until the real Google/Apple SDKs are wired in. */
class StubCredentialProvider : CredentialProvider {
    override suspend fun requestToken(activity: Activity, provider: AuthProviderType): TokenResult =
        TokenResult.Unavailable(provider)
}
