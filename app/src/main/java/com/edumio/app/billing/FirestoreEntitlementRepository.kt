package com.edumio.app.billing

import android.content.Context
import com.edumio.app.core.PremiumStore
import com.edumio.app.firebase.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Server-verified entitlement source. Reads the **server-write-only** document
 * `users/{uid}/entitlements/premium` (written by the purchase-verification Cloud Function), applies the pure
 * [EntitlementState] + [PurchaseAccountBinding] rules, and refreshes the local [PremiumStore] cache. The
 * client can never forge this document (see `firestore.rules`).
 *
 * Fail-safe: signed out, unbound, unreadable, or any error → the cached value (which itself fails safe to
 * Free). Activated by [EntitlementProvider] when Firebase is configured.
 */
class FirestoreEntitlementRepository(context: Context) : EntitlementRepository {

    private val store = PremiumStore(context.applicationContext)
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    override fun current(): Entitlement {
        val premium = store.isPremium()
        return Entitlement(premium, if (premium) PremiumTier.PREMIUM else PremiumTier.FREE, EntitlementSource.LOCAL_CACHE)
    }

    override suspend fun refresh(userId: String?): Entitlement {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: userId
        if (uid.isNullOrBlank()) {
            store.setPremium(false)
            return Entitlement(false, PremiumTier.FREE, EntitlementSource.SERVER)
        }
        return try {
            val doc = db.collection("users").document(uid)
                .collection("entitlements").document("premium").get().await()
            val state = doc.getString("state")
            val expiresAtMs = doc.getLong("expiresAtMs")
            val entitlementUid = doc.getString("uid")
            val serverPremium = EntitlementState.isPremium(state, expiresAtMs, System.currentTimeMillis())
            val premium = PurchaseAccountBinding.effectivePremium(
                currentUid = uid, serverReachable = true, serverPremium = serverPremium,
                entitlementUid = entitlementUid, cachedPremium = store.isPremium(),
            )
            store.setPremium(premium)
            Entitlement(premium, if (premium) PremiumTier.PREMIUM else PremiumTier.FREE, EntitlementSource.SERVER, expiresAtMs)
        } catch (_: Throwable) {
            current() // offline / error → cached (which is itself fail-safe)
        }
    }
}
