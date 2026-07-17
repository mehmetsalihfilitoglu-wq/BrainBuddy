package com.edumio.app.sync

import android.content.Context
import android.util.Log
import com.edumio.app.auth.AuthProvider

/**
 * The single entry point the app uses to trigger a sync. It gates on auth (sync is
 * per-account) and delegates to the [SyncEngine] over whichever [SyncRepository] is
 * configured. Safe to call from anywhere; it never throws.
 *
 * When a real (network) backend is wired in, add a connectivity check here so an
 * offline call cleanly returns [SyncResult.Skipped] and reschedules — the local
 * mirror needs no such gate.
 */
object SyncManager {

    private const val TAG = "SyncManager"

    suspend fun syncNow(context: Context, reason: String): SyncResult {
        val user = AuthProvider.currentUser(context) ?: return SyncResult.Skipped
        return try {
            val engine = SyncEngine(
                context.applicationContext,
                SyncProvider.repository(context),
                SyncStateStore(context)
            )
            engine.sync(user.userId).also { Log.d(TAG, "sync($reason): $it") }
        } catch (e: Exception) {
            Log.w(TAG, "sync($reason) failed", e)
            SyncResult.Error(e.message ?: "sync error")
        }
    }
}
