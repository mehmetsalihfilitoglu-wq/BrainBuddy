package com.edumio.app.auth

/**
 * Pure mapping from Firebase Auth error strings to EDUmio's stable [AuthErrorCode], kept separate from
 * [FirebaseAuthRepository] so the (backend-specific, easy-to-get-wrong) mapping is unit-tested without the
 * Firebase SDK. The UI only ever sees [AuthErrorCode] + a localized message.
 */
object AuthErrorMapper {

    /** Map a `FirebaseAuthException.errorCode` (e.g. "ERROR_WRONG_PASSWORD"). Null/unknown → UNKNOWN. */
    fun fromFirebaseCode(code: String?): AuthErrorCode = when (code?.uppercase()) {
        "ERROR_INVALID_EMAIL" -> AuthErrorCode.INVALID_EMAIL
        "ERROR_EMAIL_ALREADY_IN_USE",
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AuthErrorCode.EMAIL_ALREADY_IN_USE
        "ERROR_WEAK_PASSWORD" -> AuthErrorCode.WEAK_PASSWORD
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS" -> AuthErrorCode.INVALID_CREDENTIALS
        "ERROR_USER_NOT_FOUND",
        "ERROR_USER_DISABLED" -> AuthErrorCode.USER_NOT_FOUND
        "ERROR_REQUIRES_RECENT_LOGIN" -> AuthErrorCode.REQUIRES_RECENT_LOGIN
        "ERROR_NETWORK_REQUEST_FAILED" -> AuthErrorCode.NETWORK
        "ERROR_OPERATION_NOT_ALLOWED",
        "ERROR_APP_NOT_AUTHORIZED" -> AuthErrorCode.PROVIDER_UNAVAILABLE
        else -> AuthErrorCode.UNKNOWN
    }

    /** A network failure (e.g. FirebaseNetworkException) overrides any code-based mapping. */
    fun map(code: String?, isNetworkFailure: Boolean): AuthErrorCode =
        if (isNetworkFailure) AuthErrorCode.NETWORK else fromFirebaseCode(code)
}
