package com.edumio.app.billing

/**
 * Pure rules binding a verified Premium entitlement to the authenticated account, so a purchase made under
 * one account can never grant Premium to a different account on the same device, and any doubt fails safe to
 * Free. Verified without a billing/Firebase runtime.
 */
object PurchaseAccountBinding {

    /** An entitlement applies only to the exact account it was verified for. */
    fun appliesTo(entitlementUid: String?, currentUid: String?): Boolean =
        !entitlementUid.isNullOrBlank() && !currentUid.isNullOrBlank() && entitlementUid == currentUid

    /**
     * The effective Premium decision.
     *  - Signed out → never Premium (Premium is an account entitlement).
     *  - Server reachable → trust the server's verdict, but only if it is bound to this account.
     *  - Server unreachable → trust the local cache only if it belongs to this account (offline grace).
     * Any mismatch or missing binding → Free.
     */
    fun effectivePremium(
        currentUid: String?,
        serverReachable: Boolean,
        serverPremium: Boolean,
        entitlementUid: String?,
        cachedPremium: Boolean,
    ): Boolean {
        if (currentUid.isNullOrBlank()) return false
        val bound = appliesTo(entitlementUid, currentUid)
        return if (serverReachable) serverPremium && bound else cachedPremium && bound
    }
}
