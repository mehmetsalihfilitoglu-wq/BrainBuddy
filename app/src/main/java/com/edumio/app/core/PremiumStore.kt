package com.edumio.app.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Fast LOCAL premium gate — a tamper-EVIDENT cache, NOT the source of truth.
 *
 * Premium is ultimately an entitlement that must be verified server-side (the Play purchase token
 * validated by a Cloud Function — Phase 3). Until then this cache gates UI review/solution depth only.
 * Premium NEVER increases the five-new-questions-per-day count.
 *
 * Phase 0 hardening (§6): the stored value is signed with a hardware-backed Android Keystore HMAC key
 * (non-exportable). On read we recompute the signature; if it is missing or does not match we **fail SAFE
 * to Free**. This defeats the trivial attack of editing `is_premium=true` directly in SharedPreferences.
 *
 * Honesty (no fake security claims): this only raises the bar for CASUAL tampering. It does NOT stop a
 * determined attacker on a compromised device, nor replay of the user's own previously-valid signature —
 * those require the server verification planned for Phase 3. Treat this class as an untrusted cache.
 *
 * Backward-compatible: an old install with a plain unsigned boolean has no signature, so it reads as Free
 * until the billing layer re-grants via [setPremium] (which signs). Consistent with the fresh-install
 * posture (no production users).
 */
class PremiumStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Fail-safe: unknown / unsigned / tampered / error → NOT premium (Free). Decision logic is the pure,
     *  unit-tested [PremiumIntegrity.isTrusted]; only signing needs the Keystore. */
    fun isPremium(): Boolean {
        return try {
            val value = prefs.getBoolean(KEY_PREMIUM, false)
            if (!value) return false
            val sig = prefs.getString(KEY_SIG, null)
            PremiumIntegrity.isTrusted(value, sig, sign(payload(true)))
        } catch (_: Throwable) {
            false
        }
    }

    fun setPremium(premium: Boolean) {
        try {
            prefs.edit()
                .putBoolean(KEY_PREMIUM, premium)
                .putString(KEY_SIG, sign(payload(premium)))
                .apply()
        } catch (_: Throwable) {
            // If signing is unavailable we must NOT persist a trusted-looking flag: clear to fail-safe Free.
            prefs.edit().remove(KEY_PREMIUM).remove(KEY_SIG).apply()
        }
    }

    private fun payload(premium: Boolean) = "premium=$premium"

    private fun sign(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        return Base64.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun hmacKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            ).build(),
        )
        return gen.generateKey()
    }

    companion object {
        private const val PREFS = "edu_premium"
        private const val KEY_PREMIUM = "is_premium"
        private const val KEY_SIG = "is_premium_sig"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "edumio_premium_hmac_v1"
    }
}
