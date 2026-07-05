package com.mioacademy.app.auth

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local auth stub: stores hashed credentials when Firebase not configured.
 * For development / offline. Switch to FirebaseAuthAuthRepository when ready.
 */
class LocalAuthStubRepository(context: Context) : AuthRepository {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun signUp(email: String, password: String): AuthResult {
        if (email.isBlank() || password.length < 6) return AuthResult.Error("E-posta geçerli ve şifre en az 6 karakter olmalı")
        val userId = "local_${System.currentTimeMillis()}"
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_EMAIL, email)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        return AuthResult.Success(userId, email)
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        val storedEmail = prefs.getString(KEY_EMAIL, null) ?: return AuthResult.Error("Hesap bulunamadı")
        if (storedEmail != email.trim()) return AuthResult.Error("E-posta veya şifre hatalı")
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return AuthResult.Error("Hesap bulunamadı")
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return AuthResult.Error("Hesap bulunamadı")
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = hashPassword(password, salt)
        if (!constantTimeEquals(expected, actual)) return AuthResult.Error("E-posta veya şifre hatalı")
        val userId = prefs.getString(KEY_USER_ID, "local_0") ?: "local_0"
        return AuthResult.Success(userId, email)
    }

    override fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .apply()
    }

    override fun currentUserId(): String? = prefs.getString(KEY_USER_ID, null)
    override fun currentEmail(): String? = prefs.getString(KEY_EMAIL, null)
    override fun isSignedIn(): Boolean = currentUserId() != null

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
        private const val KEY_SALT = "pwd_salt"
        private const val KEY_HASH = "pwd_hash"
    }
}
