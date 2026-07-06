package com.mioacademy.app.sync

import android.content.Context
import org.json.JSONObject

/**
 * On-device stand-in for the cloud (Firestore). It stores a per-user mirror of all
 * synced documents in a private prefs file, so the full [SyncEngine] round-trip —
 * push, last-write-wins upsert, pull-since-cursor — runs and is verifiable offline.
 *
 * It does not provide real cross-device sync (there is no server), but it exercises
 * the exact contract a `FirestoreSyncRepository` will fulfil, so swapping backends
 * is a provider change only.
 */
class LocalMirrorSyncRepository(context: Context) : SyncRepository {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun serverTimeMs(): Long = System.currentTimeMillis()

    override suspend fun pull(userId: String, since: Long): List<SyncRecord> {
        val root = readRoot(userId)
        val out = ArrayList<SyncRecord>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val o = root.optJSONObject(keys.next()) ?: continue
            if (o.optLong("updatedAt") > since) recordFrom(o)?.let(out::add)
        }
        return out
    }

    override suspend fun push(userId: String, records: List<SyncRecord>): PushOutcome {
        val root = readRoot(userId)
        var accepted = 0
        for (r in records) {
            val existing = root.optJSONObject(r.documentId)
            if (existing == null || r.updatedAt >= existing.optLong("updatedAt")) {
                root.put(r.documentId, jsonFrom(r))
                accepted++
            }
        }
        prefs.edit().putString(key(userId), root.toString()).apply()
        return PushOutcome(System.currentTimeMillis(), accepted)
    }

    private fun readRoot(userId: String): JSONObject =
        try { JSONObject(prefs.getString(key(userId), "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    private fun jsonFrom(r: SyncRecord): JSONObject = JSONObject().apply {
        put("scope", r.scope.name)
        put("areaId", r.areaId)
        put("storeKey", r.storeKey)
        put("updatedAt", r.updatedAt)
        put("schemaVersion", r.schemaVersion)
        put("payload", r.payload)
    }

    private fun recordFrom(o: JSONObject): SyncRecord? {
        val scope = runCatching { SyncScope.valueOf(o.optString("scope")) }.getOrNull() ?: return null
        return SyncRecord(
            scope = scope,
            areaId = o.optString("areaId", ""),
            storeKey = o.optString("storeKey"),
            updatedAt = o.optLong("updatedAt"),
            schemaVersion = o.optInt("schemaVersion", 1),
            payload = o.optJSONObject("payload") ?: JSONObject()
        )
    }

    private fun key(userId: String) = "mirror_$userId"

    companion object {
        private const val PREFS = "bb_sync_mirror"
    }
}
