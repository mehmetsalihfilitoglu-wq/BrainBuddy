package com.mioacademy.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.mioacademy.app.core.PremiumStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Real Google Play Billing adapter for the [BillingRepository] contract.
 *
 * Play Billing lives ONLY here — the UI never touches BillingClient. Premium is
 * granted strictly after a real, acknowledged PURCHASED subscription (or a restore
 * that finds one); pending purchases don't grant. When Play Billing is unavailable
 * (no Play Store, products not yet configured in the Play Console, emulator), it
 * degrades gracefully: [offers] falls back to Remote-Config prices so the paywall
 * still shows the plans, and [purchase] returns [PurchaseResult.Unavailable] with no
 * faked entitlement.
 *
 * Server-side receipt validation is the source of truth once the backend is live: a
 * Cloud Function validates the purchase token against the Play Developer API and writes
 * the verified [SubscriptionStatus]. Until then this adapter grants a client-side
 * ACTIVE status (expiry unknown) after acknowledge — replaced by the verified value later.
 */
class PlayBillingRepository(context: Context) : BillingRepository, PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val store = SubscriptionStore(appContext)
    private val premium = PremiumStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var detailsCache: Map<String, ProductDetails> = emptyMap()
    @Volatile private var pendingPurchase: CompletableDeferred<PurchaseResult>? = null

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    // ── offers ─────────────────────────────────────────────────────────────────
    override suspend fun offers(): List<PlanOffer> {
        val fallback = BillingOffers.fromRemoteConfig()
        if (!ensureConnected()) return fallback

        val products = listOf(BillingProducts.PREMIUM_MONTHLY, BillingProducts.PREMIUM_YEARLY).map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val details = try {
            client.queryProductDetails(params).productDetailsList.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "queryProductDetails failed", e); emptyList()
        }
        if (details.isEmpty()) return fallback
        detailsCache = details.associateBy { it.productId }

        val byPlan = fallback.associateBy { it.plan }
        return details.mapNotNull { pd ->
            val plan = planFor(pd.productId) ?: return@mapNotNull null
            val price = pd.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                ?: byPlan[plan]?.priceLabel ?: ""
            PlanOffer(
                plan = plan,
                productId = pd.productId,
                priceLabel = price,
                periodLabel = if (plan == SubscriptionPlan.YEARLY) "yıllık" else "aylık",
                badgeLabel = if (plan == SubscriptionPlan.YEARLY) byPlan[plan]?.badgeLabel else null,
                savingLabel = if (plan == SubscriptionPlan.YEARLY) byPlan[plan]?.savingLabel else null
            )
        }.sortedBy { it.plan.ordinal }
    }

    // ── status / refresh ─────────────────────────────────────────────────────────
    override fun status(): SubscriptionStatus = store.get()

    override suspend fun refresh(): SubscriptionStatus {
        val current = store.get()
        val resolved = when {
            current.state == SubscriptionState.NONE || current.expiresAtMs == null -> current
            System.currentTimeMillis() > current.expiresAtMs -> current.copy(state = SubscriptionState.EXPIRED)
            else -> current
        }
        if (resolved != current) { store.save(resolved); premium.setPremium(resolved.isPremium) }
        return resolved
    }

    // ── purchase ─────────────────────────────────────────────────────────────────
    override suspend fun purchase(activity: Activity, plan: SubscriptionPlan): PurchaseResult {
        if (!ensureConnected()) return PurchaseResult.Unavailable
        val pd = detailsCache[BillingProducts.productId(plan)]
            ?: run { offers(); detailsCache[BillingProducts.productId(plan)] }
            ?: return PurchaseResult.Unavailable
        val offerToken = pd.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return PurchaseResult.Unavailable

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        val deferred = CompletableDeferred<PurchaseResult>()
        pendingPurchase = deferred
        val launch = client.launchBillingFlow(activity, flowParams)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingPurchase = null
            return PurchaseResult.Unavailable
        }
        return deferred.await()
    }

    // ── PurchasesUpdatedListener ─────────────────────────────────────────────────
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val deferred = pendingPurchase
        when {
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
                pendingPurchase = null; deferred?.complete(PurchaseResult.Cancelled)
            }
            result.responseCode == BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull()
                if (purchase == null) {
                    pendingPurchase = null; deferred?.complete(PurchaseResult.Error("no_purchase"))
                    return
                }
                scope.launch {
                    val outcome = grantIfPurchased(purchase)
                    pendingPurchase = null; deferred?.complete(outcome)
                }
            }
            else -> {
                pendingPurchase = null
                deferred?.complete(PurchaseResult.Error("billing_${result.responseCode}"))
            }
        }
    }

    // ── restore ──────────────────────────────────────────────────────────────────
    override suspend fun restore(): SubscriptionStatus {
        if (!ensureConnected()) return store.get()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val active = try {
            client.queryPurchasesAsync(params).purchasesList
                .firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        } catch (e: Exception) {
            Log.w(TAG, "queryPurchases failed", e); null
        }
        return if (active != null) {
            grantIfPurchased(active)
            store.get()
        } else {
            val none = SubscriptionStatus.NONE
            store.save(none); premium.setPremium(false)
            none
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────
    private suspend fun grantIfPurchased(purchase: Purchase): PurchaseResult {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            return PurchaseResult.Pending // do NOT grant entitlement for pending
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            return PurchaseResult.Error("not_purchased")
        }
        if (!purchase.isAcknowledged) {
            try {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                )
            } catch (e: Exception) {
                Log.w(TAG, "acknowledge failed", e)
            }
        }
        val plan = purchase.products.firstNotNullOfOrNull { planFor(it) } ?: SubscriptionPlan.MONTHLY
        // Client-side ACTIVE; server-side validation later fills a verified expiry.
        val status = SubscriptionStatus(
            state = SubscriptionState.ACTIVE,
            plan = plan,
            expiresAtMs = null,
            autoRenewing = purchase.isAutoRenewing
        )
        store.save(status)
        premium.setPremium(true)
        return PurchaseResult.Success(status)
    }

    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            try {
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resumeWith(
                            Result.success(result.responseCode == BillingClient.BillingResponseCode.OK)
                        )
                    }
                    override fun onBillingServiceDisconnected() {
                        if (cont.isActive) cont.resumeWith(Result.success(false))
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "startConnection failed", e)
                if (cont.isActive) cont.resumeWith(Result.success(false))
            }
        }
    }

    private fun planFor(productId: String): SubscriptionPlan? = when (productId) {
        BillingProducts.PREMIUM_MONTHLY -> SubscriptionPlan.MONTHLY
        BillingProducts.PREMIUM_YEARLY -> SubscriptionPlan.YEARLY
        else -> null
    }

    companion object {
        private const val TAG = "PlayBilling"
    }
}
