package com.edumio.app.sync

import org.json.JSONObject

/** Account-level data vs. per-study-area learning data. Isolation is enforced here. */
enum class SyncScope { GLOBAL, STUDY_AREA }

/**
 * One synchronizable unit: a single store's snapshot for one scope.
 *
 * [storeKey] identifies the store ("gamification", …); [areaId] is the study-area
 * id for STUDY_AREA scope (or "" for GLOBAL). [updatedAt] drives last-write-wins
 * merges. [payload] is the typed key/value snapshot of that store.
 */
data class SyncRecord(
    val scope: SyncScope,
    val areaId: String,
    val storeKey: String,
    val updatedAt: Long,
    val schemaVersion: Int,
    val payload: JSONObject
) {
    /** Stable composite id — the remote document key. Study-area id is embedded, never mixed. */
    val documentId: String
        get() = if (scope == SyncScope.GLOBAL) "global__$storeKey" else "area__${areaId}__$storeKey"

    /**
     * The intended Firestore path for this record (see docs/BACKEND_ARCHITECTURE.md).
     * GLOBAL → users/{uid}/<remotePath>; STUDY_AREA → users/{uid}/areas/{areaId}/<remotePath>.
     */
    fun firestorePath(uid: String): String {
        val segment = SyncRegistry.specFor(storeKey)?.remotePath ?: storeKey
        return if (scope == SyncScope.GLOBAL) "users/$uid/$segment"
        else "users/$uid/areas/$areaId/$segment"
    }
}

sealed class SyncResult {
    data class Success(val pushed: Int, val pulled: Int) : SyncResult()
    /** Not signed in, no remote configured, or offline — a normal, non-error outcome. */
    object Skipped : SyncResult()
    data class Error(val message: String) : SyncResult()
}
