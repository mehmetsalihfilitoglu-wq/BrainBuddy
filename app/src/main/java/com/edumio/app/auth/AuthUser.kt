package com.edumio.app.auth

/**
 * The identity providers EdumioOriginal supports. EMAIL and GOOGLE are wired now
 * (Google via a [CredentialProvider] token seam); APPLE is a first-class future
 * provider — the architecture is provider-agnostic so it slots in when the iOS
 * app exists, without touching UI or business logic.
 */
enum class AuthProviderType { EMAIL, GOOGLE, APPLE, ANONYMOUS }

/**
 * A signed-in user, independent of any backend. A Firebase adapter maps
 * FirebaseUser → AuthUser; the local stub builds it from stored credentials.
 * Business/UI code only ever sees this model, never a vendor type.
 */
data class AuthUser(
    val userId: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
    val provider: AuthProviderType,
    val photoUrl: String? = null
)
