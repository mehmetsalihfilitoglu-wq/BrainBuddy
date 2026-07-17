package com.edumio.app.billing

import android.app.Activity
import android.content.Context
import com.edumio.app.core.PremiumStore
import com.edumio.app.remote.RemoteConfigKeys
import com.edumio.app.remote.RemoteConfigProvider
import org.json.JSONObject

/**
 * On-device billing model used until Google Play Billing is wired in. It surfaces the
 * real monthly/yearly offers (priced from Remote Config) and tracks subscription
 * status deterministically, but it never fakes a charge: [purchase] returns
 * [PurchaseResult.Unavailable] until the Play Billing adapter is connected.
 *
 * A `PlayBillingRepository` implements the same contract, writing the *verified*
 * status here (and into [PremiumStore]) after a Cloud Function validates the receipt.
 */
class LocalBillingRepository(context: Context) : BillingRepository {

    private val appContext = context.applicationContext
    private val store = SubscriptionStore(appContext)
    private val premium = PremiumStore(appContext)

    override suspend fun offers(): List<PlanOffer> = BillingOffers.fromRemoteConfig()

    override fun status(): SubscriptionStatus = store.get()

    override suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseResult {
        // No real charge without Play Billing — honest, never faked.
        return PurchaseResult.Unavailable
    }

    override suspend fun restore(): SubscriptionStatus = refresh()

    override suspend fun refresh(): SubscriptionStatus {
        val current = store.get()
        val resolved = when {
            current.state == SubscriptionState.NONE || current.expiresAtMs == null -> current
            System.currentTimeMillis() > current.expiresAtMs -> current.copy(state = SubscriptionState.EXPIRED)
            else -> current
        }
        if (resolved != current) store.save(resolved)
        // Keep the fast local Premium cache aligned with entitlement.
        premium.setPremium(resolved.isPremium)
        return resolved
    }
}

/**
 * Persists the current subscription status (the verified result of billing). Separated
 * so the future Play Billing adapter writes the same store the UI reads.
 */
class SubscriptionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): SubscriptionStatus {
        val raw = prefs.getString(KEY, null) ?: return SubscriptionStatus.NONE
        return try {
            val o = JSONObject(raw)
            SubscriptionStatus(
                state = runCatching { SubscriptionState.valueOf(o.optString("state")) }
                    .getOrDefault(SubscriptionState.NONE),
                plan = o.optString("plan").takeIf { it.isNotEmpty() }
                    ?.let { runCatching { SubscriptionPlan.valueOf(it) }.getOrNull() },
                expiresAtMs = if (o.has("expiresAtMs") && !o.isNull("expiresAtMs")) o.getLong("expiresAtMs") else null,
                autoRenewing = o.optBoolean("autoRenewing", false)
            )
        } catch (_: Exception) {
            SubscriptionStatus.NONE
        }
    }

    fun save(status: SubscriptionStatus) {
        val o = JSONObject()
            .put("state", status.state.name)
            .put("plan", status.plan?.name ?: "")
            .put("expiresAtMs", status.expiresAtMs ?: JSONObject.NULL)
            .put("autoRenewing", status.autoRenewing)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    companion object {
        private const val PREFS = "bb_subscription"
        private const val KEY = "subscription_status"
    }
}

/**
 * Remote-Config-priced offers — the single source of the plan list, used directly by
 * [LocalBillingRepository] and as the fallback when Play Billing can't return products.
 */
object BillingOffers {
    fun fromRemoteConfig(): List<PlanOffer> {
        val config = RemoteConfigProvider.get()
        return listOf(
            PlanOffer(
                plan = SubscriptionPlan.MONTHLY,
                productId = BillingProducts.PREMIUM_MONTHLY,
                priceLabel = config.getString(RemoteConfigKeys.PREMIUM_MONTHLY_PRICE, "₺199"),
                periodLabel = "aylık"
            ),
            PlanOffer(
                plan = SubscriptionPlan.YEARLY,
                productId = BillingProducts.PREMIUM_YEARLY,
                priceLabel = config.getString(RemoteConfigKeys.PREMIUM_YEARLY_PRICE, "₺1.699"),
                periodLabel = "yıllık",
                badgeLabel = config.getString(RemoteConfigKeys.PREMIUM_YEARLY_BADGE, "En Avantajlı"),
                savingLabel = config.getString(RemoteConfigKeys.PREMIUM_YEARLY_SAVING,
                    "Aylık ödemeye göre yaklaşık %29 tasarruf")
            )
        )
    }
}

/**
 * Composition root for billing. Prefers the real Play Billing adapter (which gracefully
 * falls back to Remote-Config offers + honest errors when the store isn't available yet);
 * set [forceLocal] true for tests/dev to use the on-device stub.
 */
object BillingProvider {
    @Volatile private var cached: BillingRepository? = null
    @Volatile var forceLocal: Boolean = false

    fun repository(context: Context): BillingRepository =
        cached ?: synchronized(this) {
            cached ?: build(context.applicationContext).also { cached = it }
        }

    private fun build(appContext: Context): BillingRepository =
        if (forceLocal) LocalBillingRepository(appContext)
        else PlayBillingRepository(appContext)
}
