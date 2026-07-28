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
        // Spark-safe v1.0: cloud sync stays on the local mirror until the backend + rules are deployed.
        if (FirebaseConfig.isConfigured(appContext) &&
            com.edumio.app.release.ReleaseProfile.cloudSyncEnabled
        ) FirestoreSyncRepository()
        else LocalMirrorSyncRepository(appContext)
}
