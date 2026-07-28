package com.edumio.app.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/** Backend error strings must map to stable, UI-actionable [AuthErrorCode]s. */
class AuthErrorMapperTest {

    @Test fun mapsKnownFirebaseCodes() {
        assertEquals(AuthErrorCode.INVALID_EMAIL, AuthErrorMapper.fromFirebaseCode("ERROR_INVALID_EMAIL"))
        assertEquals(AuthErrorCode.EMAIL_ALREADY_IN_USE, AuthErrorMapper.fromFirebaseCode("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthErrorCode.WEAK_PASSWORD, AuthErrorMapper.fromFirebaseCode("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, AuthErrorMapper.fromFirebaseCode("ERROR_WRONG_PASSWORD"))
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, AuthErrorMapper.fromFirebaseCode("ERROR_INVALID_CREDENTIAL"))
        assertEquals(AuthErrorCode.USER_NOT_FOUND, AuthErrorMapper.fromFirebaseCode("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthErrorCode.USER_NOT_FOUND, AuthErrorMapper.fromFirebaseCode("ERROR_USER_DISABLED"))
        assertEquals(AuthErrorCode.REQUIRES_RECENT_LOGIN, AuthErrorMapper.fromFirebaseCode("ERROR_REQUIRES_RECENT_LOGIN"))
        assertEquals(AuthErrorCode.PROVIDER_UNAVAILABLE, AuthErrorMapper.fromFirebaseCode("ERROR_OPERATION_NOT_ALLOWED"))
    }

    @Test fun unknownOrNullMapsToUnknown() {
        assertEquals(AuthErrorCode.UNKNOWN, AuthErrorMapper.fromFirebaseCode(null))
        assertEquals(AuthErrorCode.UNKNOWN, AuthErrorMapper.fromFirebaseCode("ERROR_SOMETHING_NEW"))
    }

    @Test fun caseInsensitive() {
        assertEquals(AuthErrorCode.WEAK_PASSWORD, AuthErrorMapper.fromFirebaseCode("error_weak_password"))
    }

    @Test fun networkFailureOverridesCode() {
        assertEquals(AuthErrorCode.NETWORK, AuthErrorMapper.map("ERROR_WRONG_PASSWORD", isNetworkFailure = true))
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, AuthErrorMapper.map("ERROR_WRONG_PASSWORD", isNetworkFailure = false))
    }
}
