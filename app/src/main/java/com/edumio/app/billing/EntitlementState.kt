package com.edumio.app.billing

/**
 * Pure mapping from a Google Play subscription state (as written server-side by the purchase-verification
 * function / RTDN handler) to "is the user Premium right now?". Kept testable so the fail-safe rules are
 * verified without any billing/Firebase runtime.
 *
 * **Fail-safe:** anything unknown, expired, on-hold, paused, pending or revoked → **Free**. Premium is only
 * granted for an actively-entitled state. A cancelled-but-not-yet-expired subscription keeps access until
 * its period end.
 */
object EntitlementState {
    const val ACTIVE = "ACTIVE"
    const val IN_GRACE_PERIOD = "IN_GRACE_PERIOD"
    const val ON_HOLD = "ON_HOLD"
    const val PAUSED = "PAUSED"
    const val CANCELED = "CANCELED"     // cancelled auto-renew; still entitled until expiresAtMs
    const val EXPIRED = "EXPIRED"
    const val REVOKED = "REVOKED"       // refunded/chargeback → immediate loss
    const val PENDING = "PENDING"       // deferred/pending purchase → not yet entitled

    fun isPremium(state: String?, expiresAtMs: Long?, nowMs: Long): Boolean = when (state?.uppercase()) {
        ACTIVE, IN_GRACE_PERIOD -> true
        CANCELED -> expiresAtMs != null && expiresAtMs > nowMs
        else -> false // ON_HOLD, PAUSED, EXPIRED, REVOKED, PENDING, null, unknown → Free
    }
}
