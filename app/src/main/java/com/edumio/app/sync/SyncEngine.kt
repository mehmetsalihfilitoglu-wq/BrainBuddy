package com.edumio.app.sync

import android.content.Context
import com.edumio.app.core.ProfileStore

/**
 * The heart of cloud sync: a two-way, last-write-wins reconciliation between local
 * scoped stores and a [SyncRepository]. It is backend-agnostic — the local mirror
 * and a future Firestore adapter run through the exact same logic.
 *
 * Flow per run:
 *  1. Collect a typed snapshot of every registered store (global + per study area).
 *  2. Detect which changed locally since last sync (content hash) and push them.
 *  3. Pull records changed on the server since the cursor; apply those newer than
 *     the local copy. GLOBAL records (incl. the study-area list) apply first so a
 *     newly-synced area exists before its data lands — isolation preserved throughout.
 */
class SyncEngine(
    private val context: Context,
    private val repository: SyncRepository,
    private val state: SyncStateStore
) {

    suspend fun sync(userId: String): SyncResult {
        val local = collectLocal()

        // 1) Push locally-changed stores.
        val dirty = local.filter { !state.isPushed(it.documentId) }
        var pushed = 0
        if (dirty.isNotEmpty()) {
            val outcome = repository.push(userId, dirty)
            dirty.forEach { state.markPushed(it.documentId) }
            pushed = outcome.accepted
        }

        // 2) Pull and apply remote changes (GLOBAL first).
        val cursor = state.pullCursor(userId)
        val serverNow = repository.serverTimeMs()
        val remote = repository.pull(userId, cursor)
            .sortedBy { if (it.scope == SyncScope.GLOBAL) 0 else 1 }

        var applied = 0
        for (r in remote) {
            if (r.updatedAt > state.updatedAt(r.documentId)) {
                if (apply(r)) applied++
            }
        }
        state.setPullCursor(userId, serverNow)
        return SyncResult.Success(pushed, applied)
    }

    /** Snapshot every registered store into records, tagging local change state. */
    private fun collectLocal(): List<SyncRecord> {
        val records = ArrayList<SyncRecord>()

        for (spec in SyncRegistry.globalStores) {
            val prefs = context.getSharedPreferences(spec.baseName, Context.MODE_PRIVATE)
            if (prefs.all.isEmpty()) continue
            records.add(buildRecord(SyncScope.GLOBAL, "", spec, prefs.all))
        }

        val areaIds = ProfileStore(context).getProfiles().map { it.id }
        for (areaId in areaIds) {
            for (spec in SyncRegistry.areaStores) {
                val prefs = context.getSharedPreferences("${spec.baseName}_$areaId", Context.MODE_PRIVATE)
                if (prefs.all.isEmpty()) continue
                records.add(buildRecord(SyncScope.STUDY_AREA, areaId, spec, prefs.all))
            }
        }
        return records
    }

    private fun buildRecord(scope: SyncScope, areaId: String, spec: StoreSpec, all: Map<String, *>): SyncRecord {
        val payload = PrefsSnapshotCodec.encode(all)
        val hash = PrefsSnapshotCodec.contentHash(payload)
        val docId = if (scope == SyncScope.GLOBAL) "global__${spec.storeKey}" else "area__${areaId}__${spec.storeKey}"

        val changed = state.hash(docId) != hash
        val updatedAt = if (changed) System.currentTimeMillis() else state.updatedAt(docId)
        if (changed) state.recordLocalState(docId, hash, updatedAt, pushed = false)

        return SyncRecord(scope, areaId, spec.storeKey, updatedAt, spec.schemaVersion, payload)
    }

    /** Apply one remote record into its (isolated) local store. Returns true if applied. */
    private fun apply(r: SyncRecord): Boolean {
        val spec = SyncRegistry.specFor(r.storeKey) ?: return false
        val prefsName = if (r.scope == SyncScope.GLOBAL) spec.baseName else "${spec.baseName}_${r.areaId}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        PrefsSnapshotCodec.applyInto(prefs, r.payload)
        // Mark local state as in-sync with what we just applied so we don't echo it back.
        state.recordLocalState(r.documentId, PrefsSnapshotCodec.contentHash(r.payload), r.updatedAt, pushed = true)
        return true
    }
}
