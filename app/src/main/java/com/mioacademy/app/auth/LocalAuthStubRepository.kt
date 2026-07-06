package com.mioacademy.app.auth

import android.content.Context
import android.util.Base64
import android.util.Patterns
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * On-device implementation of [AuthRepository] used until Firebase is configured.
 *
 * It is a real, self-contained account store — not a fake: passwords are salted +
 * PBKDF2-hashed, sessions are opaque tokens, and every contract method behaves
 * sensibly offline. Federated sign-in is simulated locally (a Google/Apple token
 * creates/looks up a local account) so the whole auth UX can be built and tested
 * now; swapping in [AuthProvider] to a Firebase adapter changes nothing for callers.
 *
 * Single-account by design (one student per device); a real backend lifts that.
 */
class LocalAuthStubRepository(context: Context) : AuthRepository {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun currentUser(): AuthUser? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        // A session token must be present for the user to be considered signed in.
        if (prefs.getString(KEY_SESSION, null) == null) return null
        return AuthUser(
            userId = userId,
            email = prefs.getString(KEY_EMAIL, null),
            displayName = prefs.getString(KEY_DISPLAY_NAME, null),
            isEmailVerified = prefs.getBoolean(KEY_VERIFIED, false),
            provider = runCatching { AuthProviderType.valueOf(prefs.getString(KEY_PROVIDER, AuthProviderType.EMAIL.name)!!) }
                .getOrDefault(AuthProviderType.EMAIL),
            photoUrl = prefs.getString(KEY_PHOTO, null)
        )
    }

    override fun isSignedIn(): Boolean = currentUser() != null

    override fun signOut() {
        // Keep the credential record; only end the session.
        prefs.edit().remove(KEY_SESSION).apply()
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String?): AuthResult {
        val cleanEmail = email.trim()
        if (!isEmailValid(cleanEmail)) return AuthResult.Error(AuthErrorCode.INVALID_EMAIL, "Geçerli bir e-posta gir")
        if (password.length < 6) return AuthResult.Error(AuthErrorCode.WEAK_PASSWORD, "Şifre en az 6 karakter olmalı")
        val existing = prefs.getString(KEY_EMAIL, null)
        if (existing != null && existing.equals(cleanEmail, ignoreCase = true) && prefs.contains(KEY_HASH)) {
            return AuthResult.Error(AuthErrorCode.EMAIL_ALREADY_IN_USE, "Bu e-posta ile bir hesap zaten var")
        }
        val userId = "local_${System.currentTimeMillis()}"
        val salt = randomBytes(16)
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, cleanEmail)
            .putString(KEY_DISPLAY_NAME, displayName?.trim().takeUnless { it.isNullOrBlank() })
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PROVIDER, AuthProviderType.EMAIL.name)
            .putBoolean(KEY_VERIFIED, false)
            .putString(KEY_SESSION, newSessionToken())
            .apply()
        return AuthResult.Success(currentUser()!!)
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val cleanEmail = email.trim()
        val storedEmail = prefs.getString(KEY_EMAIL, null)
            ?: return AuthResult.Error(AuthErrorCode.USER_NOT_FOUND, "Bu e-posta ile hesap bulunamadı")
        if (!storedEmail.equals(cleanEmail, ignoreCase = true) || !prefs.contains(KEY_HASH)) {
            return AuthResult.Error(AuthErrorCode.INVALID_CREDENTIALS, "E-posta veya şifre hatalı")
        }
        val salt = Base64.decode(prefs.getString(KEY_SALT, "")!!, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_HASH, "")!!, Base64.NO_WRAP)
        if (!constantTimeEquals(expected, hashPassword(password, salt))) {
            return AuthResult.Error(AuthErrorCode.INVALID_CREDENTIALS, "E-posta veya şifre hatalı")
        }
        prefs.edit().putString(KEY_SESSION, newSessionToken()).apply()
        return AuthResult.Success(currentUser()!!)
    }

    override suspend fun sendPasswordReset(email: String): OpResult {
        // No mail server on-device: succeed silently so the UX flow is complete.
        // The Firebase adapter sends a real reset email.
        return OpResult.Success
    }

    override suspend fun signInWithProvider(provider: AuthProviderType, token: FederatedToken): AuthResult {
        if (token.idToken.isBlank()) return AuthResult.Error(AuthErrorCode.PROVIDER_UNAVAILABLE, "Sağlayıcı kullanılamıyor")
        // Simulate a federated account locally: providers are pre-verified.
        val userId = prefs.getString(KEY_USER_ID, null) ?: "local_${provider.name.lowercase()}_${System.currentTimeMillis()}"
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PROVIDER, provider.name)
            .putBoolean(KEY_VERIFIED, true)
            .putString(KEY_SESSION, newSessionToken())
            .apply()
        return AuthResult.Success(currentUser()!!)
    }

    override suspend fun sendEmailVerification(): OpResult {
        if (currentUser() == null) return OpResult.Error(AuthErrorCode.USER_NOT_FOUND, "Önce giriş yap")
        // Locally we mark the address verified; the Firebase adapter emails a link.
        prefs.edit().putBoolean(KEY_VERIFIED, true).apply()
        return OpResult.Success
    }

    override suspend fun reloadUser(): AuthUser? = currentUser()

    override suspend fun deleteAccount(): OpResult {
        prefs.edit().clear().apply()
        return OpResult.Success
    }

    // ── crypto helpers ────────────────────────────────────────────────────────
    private fun isEmailValid(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    private fun newSessionToken(): String = Base64.encodeToString(randomBytes(24), Base64.NO_WRAP)

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    companion object {
        private const val PREFS = "bb_auth_stub"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_SALT = "pwd_salt"
        private const val KEY_HASH = "pwd_hash"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_VERIFIED = "email_verified"
        private const val KEY_PHOTO = "photo_url"
        private const val KEY_SESSION = "session_token"
    }
}
