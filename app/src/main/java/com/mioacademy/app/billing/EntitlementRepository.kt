package com.mioacademy.app.billing

/**
 * The premium-verification boundary. Premium is an *entitlement* that must ultimately
 * be verified server-side (Play Billing purchase token validated by a Cloud Function),
 * never trusted from the client alone.
 *
 * [com.mioacademy.app.core.PremiumStore] remains the fast local cache used for gating
 * UI; this contract is the source of truth that refreshes it. A
 * `PlayBillingEntitlementRepository` implements the same interface once billing is wired.
 */
interface EntitlementRepository {
    /** The last known entitlement (from cache) without a network call. */
    fun current(): Entitlement

    /** Re-verifies against the server/store and updates the local cache. */
    suspend fun refresh(userId: String?): Entitlement
}

data class Entitlement(
    val isPremium: Boolean,
    val tier: PremiumTier,
    val source: EntitlementSource,
    val expiresAtMs: Long? = null
)

enum class PremiumTier { FREE, PREMIUM }

enum class EntitlementSource { LOCAL_CACHE, PLAY_BILLING, SERVER, PROMO }
