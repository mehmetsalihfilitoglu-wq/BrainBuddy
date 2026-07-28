package com.edumio.app.billing

import android.app.Activity

/**
 * The subscription/billing boundary. EdumioOriginal is ad-free: revenue is Free vs
 * Premium (monthly / yearly). This contract is what the paywall and entitlement
 * layer talk to — never Google Play Billing directly.
 *
 * [LocalBillingRepository] models offers + status on-device so the paywall builds
 * and behaves honestly today (it does not fake a charge). A `PlayBillingRepository`
 * will implement the same interface: query products, launch the purchase flow,
 * acknowledge, and hand the purchase token to a Cloud Function for receipt
 * validation before flipping the verified entitlement (see docs/BACKEND_ARCHITECTURE.md).
 */
interface BillingRepository {
    /** Available subscription offers (monthly + yearly), priced via Remote Config / Play. */
    suspend fun offers(): List<PlanOffer>

    /** Current cached subscription status (server-authoritative once billing is live). */
    fun status(): SubscriptionStatus

    /** Launch the purchase flow for [plan]. */
    suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseResult

    /** Restore an existing subscription (Play "restore purchases"). */
    suspend fun restore(): SubscriptionStatus

    /** Re-query status (e.g. on resume); recomputes expiry/grace deterministically. */
    suspend fun refresh(): SubscriptionStatus
}

enum class SubscriptionPlan { MONTHLY, YEARLY }

/** Lifecycle states a subscription can be in — deterministic, server-verifiable. */
enum class SubscriptionState { NONE, ACTIVE, IN_GRACE_PERIOD, CANCELLED, EXPIRED }

data class PlanOffer(
    val plan: SubscriptionPlan,
    val productId: String,        // Play base-plan / product id
    val priceLabel: String,       // Play localized price when live, else Remote Config copy
    val periodLabel: String,      // e.g. "aylık" / "yıllık"
    val badgeLabel: String? = null,   // e.g. "En Avantajlı" (yearly)
    val savingLabel: String? = null   // e.g. "Aylık ödemeye göre yaklaşık %29 tasarruf"
)

data class SubscriptionStatus(
    val state: SubscriptionState,
    val plan: SubscriptionPlan?,
    val expiresAtMs: Long?,
    val autoRenewing: Boolean
) {
    /**
     * Entitled to Premium features: actively paying, in the payment grace period, or
     * cancelled-but-still-inside-the-paid-period. Only EXPIRED/NONE lose access.
     */
    val isPremium: Boolean
        get() = state == SubscriptionState.ACTIVE ||
            state == SubscriptionState.IN_GRACE_PERIOD ||
            state == SubscriptionState.CANCELLED

    companion object {
        val NONE = SubscriptionStatus(SubscriptionState.NONE, null, null, false)
    }
}

sealed class PurchaseResult {
    data class Success(val status: SubscriptionStatus) : PurchaseResult()
    object Cancelled : PurchaseResult()
    object Pending : PurchaseResult()
    /** Billing back end not connected yet — honest, never a faked charge. */
    object Unavailable : PurchaseResult()
    data class Error(val message: String) : PurchaseResult()
}

/** Stable Play product ids (single source of truth for both client and console). */
object BillingProducts {
    const val PREMIUM_MONTHLY = "edumio_premium_monthly"
    const val PREMIUM_YEARLY = "edumio_premium_yearly"

    fun productId(plan: SubscriptionPlan): String = when (plan) {
        SubscriptionPlan.MONTHLY -> PREMIUM_MONTHLY
        SubscriptionPlan.YEARLY -> PREMIUM_YEARLY
    }
}
