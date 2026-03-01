package com.brainbuddy.app.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPinSet(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun setPin(pin: CharArray) {
        require(pin.size == 4 || pin.size == 6) { "PIN must be 4 or 6 digits" }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)

        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()

        pin.fill('\u0000')
    }

    fun verifyPin(pin: CharArray): Boolean {
        return try {
            val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
            val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
            if (saltB64.isBlank() || hashB64.isBlank()) return false

            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val expected = Base64.decode(hashB64, Base64.NO_WRAP)
            if (salt.isEmpty() || expected.isEmpty()) return false
            val actual = pbkdf2(pin, salt)

            pin.fill('\u0000')
            constantTimeEquals(expected, actual)
        } catch (_: Exception) {
            pin.fill('\u0000')
            false
        }
    }

    private fun pbkdf2(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_LEN_BITS)
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
        private const val PREFS = "bb_pin_prefs"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"

        private const val ITERATIONS = 120_000
        private const val KEY_LEN_BITS = 256
    }
}