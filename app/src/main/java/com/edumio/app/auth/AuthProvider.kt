package com.edumio.app.auth

import android.content.Context
import com.edumio.app.firebase.FirebaseConfig

/**
 * Single composition root for authentication. The rest of the app depends only on
 * these factory methods, never on a concrete implementation — so switching from the
 * local stub to Firebase is a one-file change here.
 *
 * When the owner supplies `google-services.json`, [FirebaseConfig.isConfigured] flips true and
 * [FirebaseAuthRepository] activates automatically; otherwise the fully-functional
 * [LocalAuthStubRepository] runs (offline, no account server). Callers never change.
 */
object AuthProvider {

    @Volatile private var cachedRepo: AuthRepository? = null

    fun repository(context: Context): AuthRepository {
        return cachedRepo ?: synchronized(this) {
            cachedRepo ?: buildRepository(context.applicationContext).also { cachedRepo = it }
        }
    }

    fun credentialProvider(): CredentialProvider {
        // Real Google/Apple credential providers plug in here once their SDKs exist.
        return StubCredentialProvider()
    }

    /** Convenience passthrough used widely across the UI. */
    fun currentUser(context: Context): AuthUser? = repository(context).currentUser()
    fun isSignedIn(context: Context): Boolean = repository(context).isSignedIn()

    private fun buildRepository(appContext: Context): AuthRepository = when {
        // v1 ships account-free: never touch a real auth backend at runtime. See ReleaseProfile.authEnabled.
        !com.edumio.app.release.ReleaseProfile.authEnabled -> NoOpAuthRepository()
        FirebaseConfig.isConfigured(appContext) -> FirebaseAuthRepository()
        else -> LocalAuthStubRepository(appContext)
    }
}
