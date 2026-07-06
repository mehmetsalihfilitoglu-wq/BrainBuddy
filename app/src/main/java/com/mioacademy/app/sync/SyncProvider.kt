package com.mioacademy.app.sync

import android.content.Context

/**
 * Composition root for cloud sync. Returns the on-device mirror today; returns a
 * `FirestoreSyncRepository` once Firebase is configured — the only place that
 * choice is made, mirroring [com.mioacademy.app.auth.AuthProvider].
 */
object SyncProvider {

    @Volatile private var cached: SyncRepository? = null

    fun repository(context: Context): SyncRepository {
        return cached ?: synchronized(this) {
            cached ?: build(context.applicationContext).also { cached = it }
        }
    }

    private fun build(appContext: Context): SyncRepository {
        // return FirestoreSyncRepository(appContext)  // when google-services.json is added
        return LocalMirrorSyncRepository(appContext)
    }
}
