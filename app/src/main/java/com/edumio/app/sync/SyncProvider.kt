package com.edumio.app.sync

import android.content.Context
import com.edumio.app.firebase.FirebaseConfig

/**
 * Composition root for cloud sync. Returns [FirestoreSyncRepository] once Firebase is configured, else the
 * on-device [LocalMirrorSyncRepository] — the only place that choice is made, mirroring
 * [com.edumio.app.auth.AuthProvider]. [SyncEngine] is unchanged either way.
 */
object SyncProvider {

    @Volatile private var cached: SyncRepository? = null

    fun repository(context: Context): SyncRepository {
        return cached ?: synchronized(this) {
            cached ?: build(context.applicationContext).also { cached = it }
        }
    }

    private fun build(appContext: Context): SyncRepository =
        if (FirebaseConfig.isConfigured(appContext)) FirestoreSyncRepository()
        else LocalMirrorSyncRepository(appContext)
}
