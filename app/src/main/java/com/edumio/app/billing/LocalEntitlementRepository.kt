package com.edumio.app.billing

import android.content.Context
import com.edumio.app.core.PremiumStore
import com.edumio.app.firebase.FirebaseConfig

/**
 * Reads/writes the local premium cache ([PremiumStore]) and, for now, treats the
 * cache as authoritative (no store to verify against yet). When Play Billing + a
 * verification Cloud Function are live, [PlayBillingEntitlementRepository] will
 * validate purchase tokens and write the verified result back into the same cache.
 */
class LocalEntitlementRepository(context: Context) : EntitlementRepository {

    private val store = PremiumStore(context)

    override fun current(): Entitlement = Entitlement(
        isPremium = store.isPremium(),
        tier = if (store.isPremium()) PremiumTier.PREMIUM else PremiumTier.FREE,
        source = EntitlementSource.LOCAL_CACHE
    )

    override suspend fun refresh(userId: String?): Entitlement = current()
}

/** Composition root for entitlement verification. Returns the server-verified repository once Firebase is
 *  configured, else the local cache. Callers are unchanged; both fail safe to Free. */
object EntitlementProvider {
    fun repository(context: Context): EntitlementRepository {
        val app = context.applicationContext
        return if (FirebaseConfig.isConfigured(app)) FirestoreEntitlementRepository(app)
        else LocalEntitlementRepository(app)
    }
}
