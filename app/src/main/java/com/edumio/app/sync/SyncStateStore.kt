package com.edumio.app.sync

import android.content.Context

/**
 * Local sync bookkeeping: per-document content hash + local "updated at", whether a
 * change has been pushed yet, and the per-user pull cursor. This is what lets the
 * engine detect real local changes (no need to instrument every writer) and merge by
 * last-write-wins without a server round-trip on unchanged data.
 */
class SyncStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hash(documentId: String): String? = prefs.getString("hash_$documentId", null)
    fun updatedAt(documentId: String): Long = prefs.getLong("upd_$documentId", 0L)
    fun isPushed(documentId: String): Boolean = prefs.getBoolean("pushed_$documentId", false)

    fun recordLocalState(documentId: String, hash: String, updatedAt: Long, pushed: Boolean) {
        prefs.edit()
            .putString("hash_$documentId", hash)
            .putLong("upd_$documentId", updatedAt)
            .putBoolean("pushed_$documentId", pushed)
            .apply()
    }

    fun markPushed(documentId: String) {
        prefs.edit().putBoolean("pushed_$documentId", true).apply()
    }

    fun pullCursor(userId: String): Long = prefs.getLong("cursor_$userId", 0L)
    fun setPullCursor(userId: String, ts: Long) {
        prefs.edit().putLong("cursor_$userId", ts).apply()
    }

    companion object {
        private const val PREFS = "bb_sync_state"
    }
}
