package com.edumio.app.sync

import com.edumio.app.firebase.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONObject

/**
 * Firestore-backed [SyncRepository] — fulfils the exact contract [SyncEngine] already drives against the
 * on-device [LocalMirrorSyncRepository], so swapping backends is a provider change only. Activated by
 * [SyncProvider] when [com.edumio.app.firebase.FirebaseConfig.isConfigured] is true.
 *
 * Storage: each [SyncRecord] is one document at `users/{uid}/syncRecords/{documentId}`. Study-area isolation
 * is preserved by the record's own `documentId` (`area__{areaId}__{store}`), never mixed. The opaque store
 * snapshot is stored as a JSON string (`payloadJson`) for lossless round-trips. **No question/solution
 * content is ever written** — only the user's own learning-state stores from [SyncRegistry].
 *
 * Last-write-wins is enforced per document in a transaction (incoming wins on `updatedAt` ties), matching
 * the local mirror. Trust-critical state (canonical challenge identity, Premium entitlement) is NOT written
 * here — those become server-authoritative (server-write-only) in Phases 3–4.
 *
 * Runtime behaviour requires a real Firebase project; the merge rules it relies on are covered by
 * [SyncConflictResolverTest].
 */
class FirestoreSyncRepository : SyncRepository {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun records(uid: String) =
        db.collection("users").document(uid).collection(COLLECTION)

    override suspend fun serverTimeMs(): Long {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return System.currentTimeMillis()
        return try {
            val clock = db.collection("users").document(uid).collection("meta").document("clock")
            clock.set(mapOf("t" to FieldValue.serverTimestamp())).await()
            val snap = clock.get(Source.SERVER).await()
            snap.getTimestamp("t")?.toDate()?.time ?: System.currentTimeMillis()
        } catch (_: Throwable) {
            System.currentTimeMillis()
        }
    }

    override suspend fun pull(userId: String, since: Long): List<SyncRecord> {
        val snaps = records(userId).whereGreaterThan(FIELD_UPDATED_AT, since).get().await()
        return snaps.documents.mapNotNull { doc ->
            val scope = runCatching { SyncScope.valueOf(doc.getString(FIELD_SCOPE) ?: "") }.getOrNull()
                ?: return@mapNotNull null
            val payload = runCatching { JSONObject(doc.getString(FIELD_PAYLOAD) ?: "{}") }.getOrDefault(JSONObject())
            SyncRecord(
                scope = scope,
                areaId = doc.getString(FIELD_AREA_ID) ?: "",
                storeKey = doc.getString(FIELD_STORE_KEY) ?: return@mapNotNull null,
                updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L,
                schemaVersion = (doc.getLong(FIELD_SCHEMA) ?: 1L).toInt(),
                payload = payload,
            )
        }
    }

    override suspend fun push(userId: String, records: List<SyncRecord>): PushOutcome {
        var accepted = 0
        for (r in records) {
            val ref = records(userId).document(r.documentId)
            val didWrite = db.runTransaction { txn ->
                val existing = txn.get(ref).getLong(FIELD_UPDATED_AT) ?: Long.MIN_VALUE
                if (r.updatedAt >= existing) {
                    txn.set(ref, toMap(r)); true
                } else {
                    false
                }
            }.await()
            if (didWrite) accepted++
        }
        return PushOutcome(serverTimeMs(), accepted)
    }

    private fun toMap(r: SyncRecord): Map<String, Any?> = mapOf(
        FIELD_SCOPE to r.scope.name,
        FIELD_AREA_ID to r.areaId,
        FIELD_STORE_KEY to r.storeKey,
        FIELD_UPDATED_AT to r.updatedAt,
        FIELD_SCHEMA to r.schemaVersion,
        FIELD_PAYLOAD to r.payload.toString(),
        FIELD_SERVER_AT to FieldValue.serverTimestamp(),
    )

    companion object {
        private const val COLLECTION = "syncRecords"
        private const val FIELD_SCOPE = "scope"
        private const val FIELD_AREA_ID = "areaId"
        private const val FIELD_STORE_KEY = "storeKey"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_SCHEMA = "schemaVersion"
        private const val FIELD_PAYLOAD = "payloadJson"
        private const val FIELD_SERVER_AT = "serverAt"
    }
}
