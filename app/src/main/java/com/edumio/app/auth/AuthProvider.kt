package com.edumio.app.auth

import android.content.Context

/**
 * Single composition root for authentication. The rest of the app depends only on
 * these factory methods, never on a concrete implementation — so switching from the
 * local stub to Firebase is a one-file change here.
 *
 * To go live with Firebase:
 *  1. Add `google-services.json` + the Firebase Auth SDK.
 *  2. Implement `FirebaseAuthRepository` (same [AuthRepository] contract) and
 *     `GoogleCredentialProvider` (same [CredentialProvider] contract).
 *  3. Return them below when [isFirebaseConfigured] is true.
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

    private fun buildRepository(appContext: Context): AuthRepository {
        return if (isFirebaseConfigured(appContext)) {
            // return FirebaseAuthRepository(appContext)   // wired when google-services.json is added
            LocalAuthStubRepository(appContext)
        } else {
            LocalAuthStubRepository(appContext)
        }
    }

    /**
     * True once the Firebase config resource exists. Kept resource-based so no
     * Firebase SDK is referenced until it's actually added to the build.
     */
    private fun isFirebaseConfigured(context: Context): Boolean {
        val id = context.resources.getIdentifier(
            "google_app_id", "string", context.packageName
        )
        return id != 0
    }
}
