package com.brainbuddy.app.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Emergency unlock code (different from PIN) for fail-safe recovery. */
class EmergencyCodeManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEmergencyCodeSet(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun setEmergencyCode(code: CharArray) {
        require(code.size >= 6) { "Emergency code must be at least 6 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(code, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
        code.fill('\u0000')
    }

    fun verifyEmergencyCode(code: CharArray): Boolean {
        return try {
            val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
            val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
            if (saltB64.isBlank() || hashB64.isBlank()) return false
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(hashB64, Base64.NO_WRAP)
            if (salt.isEmpty() || expected.isEmpty()) return false
            val actual = pbkdf2(code, salt)
            code.fill('\u0000')
            constantTimeEquals(expected, actual)
        } catch (_: Exception) {
            code.fill('\u0000')
            false
        }
    }

    private fun pbkdf2(code: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(code, salt, ITERATIONS, KEY_LEN_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    companion object {
        private const val PREFS = "bb_emergency_code_prefs"
        private const val KEY_SALT = "emergency_salt"
        private const val KEY_HASH = "emergency_hash"
        private const val ITERATIONS = 120_000
        private const val KEY_LEN_BITS = 256
    }
}
