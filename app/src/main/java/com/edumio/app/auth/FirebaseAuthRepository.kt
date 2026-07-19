package com.edumio.app.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase-backed [AuthRepository]. Activated by [com.edumio.app.auth.AuthProvider] ONLY when
 * [com.edumio.app.firebase.FirebaseConfig.isConfigured] is true, so `FirebaseAuth.getInstance()` is always
 * safe here. Maps `FirebaseUser` → [AuthUser] and all failures → the stable [AuthErrorCode] via the pure,
 * unit-tested [AuthErrorMapper]. Callers are unchanged from the local stub.
 *
 * Runtime behaviour requires a real Firebase project (owner-supplied `google-services.json`); the mapping
 * and error/verification logic are covered by JVM tests.
 */
class FirebaseAuthRepository : AuthRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    override fun currentUser(): AuthUser? = auth.currentUser?.toAuthUser()
    override fun isSignedIn(): Boolean = auth.currentUser != null
    override fun signOut() = auth.signOut()

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): AuthResult =
        guarded {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
            val user = result.user ?: return@guarded AuthResult.Error(AuthErrorCode.UNKNOWN, message(AuthErrorCode.UNKNOWN))
            if (!displayName.isNullOrBlank()) {
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(displayName.trim()).build(),
                ).awaitResult()
            }
            // Send the verification email immediately; ignore a transient failure (user can resend).
            runCatching { user.sendEmailVerification().awaitResult() }
            AuthResult.Success((auth.currentUser ?: user).toAuthUser())
        }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult =
        guarded {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
            val user = result.user ?: return@guarded AuthResult.Error(AuthErrorCode.UNKNOWN, message(AuthErrorCode.UNKNOWN))
            AuthResult.Success(user.toAuthUser())
        }

    override suspend fun sendPasswordReset(email: String): OpResult =
        guardedOp { auth.sendPasswordResetEmail(email.trim()).awaitResult(); OpResult.Success }

    override suspend fun signInWithProvider(provider: AuthProviderType, token: FederatedToken): AuthResult {
        if (provider != AuthProviderType.GOOGLE) {
            return AuthResult.Error(AuthErrorCode.PROVIDER_UNAVAILABLE, message(AuthErrorCode.PROVIDER_UNAVAILABLE))
        }
        if (token.idToken.isBlank()) {
            return AuthResult.Error(AuthErrorCode.PROVIDER_UNAVAILABLE, message(AuthErrorCode.PROVIDER_UNAVAILABLE))
        }
        return guarded {
            val credential = GoogleAuthProvider.getCredential(token.idToken, null)
            val result = auth.signInWithCredential(credential).awaitResult()
            val user = result.user ?: return@guarded AuthResult.Error(AuthErrorCode.UNKNOWN, message(AuthErrorCode.UNKNOWN))
            AuthResult.Success(user.toAuthUser())
        }
    }

    override suspend fun sendEmailVerification(): OpResult {
        val user = auth.currentUser ?: return OpResult.Error(AuthErrorCode.USER_NOT_FOUND, message(AuthErrorCode.USER_NOT_FOUND))
        return guardedOp { user.sendEmailVerification().awaitResult(); OpResult.Success }
    }

    override suspend fun reloadUser(): AuthUser? {
        val user = auth.currentUser ?: return null
        runCatching { user.reload().awaitResult() }
        return auth.currentUser?.toAuthUser()
    }

    override suspend fun deleteAccount(): OpResult {
        val user = auth.currentUser ?: return OpResult.Error(AuthErrorCode.USER_NOT_FOUND, message(AuthErrorCode.USER_NOT_FOUND))
        return guardedOp { user.delete().awaitResult(); OpResult.Success }
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private inline fun guarded(block: () -> AuthResult): AuthResult =
        try { block() } catch (e: Throwable) { val c = errorCode(e); AuthResult.Error(c, message(c)) }

    private inline fun guardedOp(block: () -> OpResult): OpResult =
        try { block() } catch (e: Throwable) { val c = errorCode(e); OpResult.Error(c, message(c)) }

    private fun errorCode(e: Throwable): AuthErrorCode =
        AuthErrorMapper.map((e as? FirebaseAuthException)?.errorCode, e is FirebaseNetworkException)

    private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
        userId = uid,
        email = email,
        displayName = displayName,
        isEmailVerified = isEmailVerified,
        provider = when {
            providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } -> AuthProviderType.GOOGLE
            else -> AuthProviderType.EMAIL
        },
        photoUrl = photoUrl?.toString(),
    )

    /** Turkish, user-facing messages keyed by the stable code (UI may further localize). */
    private fun message(code: AuthErrorCode): String = when (code) {
        AuthErrorCode.INVALID_CREDENTIALS -> "E-posta veya şifre hatalı"
        AuthErrorCode.EMAIL_ALREADY_IN_USE -> "Bu e-posta ile bir hesap zaten var"
        AuthErrorCode.WEAK_PASSWORD -> "Şifre en az 6 karakter olmalı"
        AuthErrorCode.INVALID_EMAIL -> "Geçerli bir e-posta gir"
        AuthErrorCode.USER_NOT_FOUND -> "Bu e-posta ile hesap bulunamadı"
        AuthErrorCode.EMAIL_NOT_VERIFIED -> "Lütfen önce e-postanı doğrula"
        AuthErrorCode.REQUIRES_RECENT_LOGIN -> "Güvenlik için lütfen tekrar giriş yap"
        AuthErrorCode.PROVIDER_UNAVAILABLE -> "Sağlayıcı şu anda kullanılamıyor"
        AuthErrorCode.NETWORK -> "İnternet bağlantını kontrol et"
        AuthErrorCode.UNKNOWN -> "Bir şeyler ters gitti, tekrar dene"
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
