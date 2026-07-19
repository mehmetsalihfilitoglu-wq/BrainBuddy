package com.edumio.app.core

/**
 * Pure decision logic for the tamper-evident local premium cache, extracted from [PremiumStore] so the
 * release-critical FAIL-SAFE rule can be unit-tested without the Android Keystore.
 *
 * The rule: a cached premium flag is trusted only when the flag is `true` AND a stored signature exists
 * AND it matches the freshly-computed signature (constant-time). Any other state — Free, unsigned,
 * tampered, or a null on either side — resolves to **not trusted (Free)**.
 */
object PremiumIntegrity {

    /** Fail-safe: unknown / unsigned / mismatched → false (Free). */
    fun isTrusted(value: Boolean, storedSig: String?, expectedSig: String?): Boolean {
        if (!value) return false
        if (storedSig == null || expectedSig == null) return false
        return constantTimeEquals(storedSig, expectedSig)
    }

    /** Length-independent equality check that does not early-exit on the first differing byte. */
    fun constantTimeEquals(a: String, b: String): Boolean {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        if (x.size != y.size) return false
        var r = 0
        for (i in x.indices) r = r or (x[i].toInt() xor y[i].toInt())
        return r == 0
    }
}
