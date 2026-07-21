package com.edumio.app.privacy

import android.content.Context
import com.edumio.app.R
import com.edumio.app.auth.AuthErrorCode
import com.edumio.app.auth.AuthProvider
import com.edumio.app.auth.OpResult
import com.edumio.app.core.ProfileStore
import com.edumio.app.sync.PrefsSnapshotCodec
import com.edumio.app.sync.SyncRegistry
import org.json.JSONObject
import java.io.File

/**
 * Real on-device data rights. Export and local deletion are fully functional now;
 * cloud deletion is honestly reported as pending until the backend is connected
 * (see docs/BACKEND_ARCHITECTURE.md §GDPR). Nothing here fakes a server operation.
 */
class LocalDataRightsService(context: Context) : DataRightsService {

    private val ctx = context.applicationContext

    override suspend fun exportData(): ExportResult {
        return try {
            val root = JSONObject()
            root.put("app", "EDUmio")
            root.put("schemaVersion", 1)
            root.put("exportedAt", System.currentTimeMillis())
            AuthProvider.currentUser(ctx)?.let {
                root.put("userId", it.userId)
                root.put("email", it.email ?: JSONObject.NULL)
            }

            val account = JSONObject()
            for (spec in SyncRegistry.globalStores) {
                val p = ctx.getSharedPreferences(spec.baseName, Context.MODE_PRIVATE)
                if (p.all.isNotEmpty()) account.put(spec.storeKey, PrefsSnapshotCodec.encode(p.all))
            }
            root.put("account", account)

            val areas = JSONObject()
            for (area in ProfileStore(ctx).getProfiles()) {
                val data = JSONObject()
                for (spec in SyncRegistry.areaStores) {
                    val p = ctx.getSharedPreferences("${spec.baseName}_${area.id}", Context.MODE_PRIVATE)
                    if (p.all.isNotEmpty()) data.put(spec.storeKey, PrefsSnapshotCodec.encode(p.all))
                }
                areas.put(area.id, JSONObject()
                    .put("name", area.name)
                    .put("careerPath", area.careerPath)
                    .put("data", data))
            }
            root.put("areas", areas)

            val json = root.toString(2)
            val file = File(ctx.filesDir, "edumio_original_export_${System.currentTimeMillis()}.json")
            file.writeText(json)
            ExportResult.Success(file.absolutePath, json.toByteArray().size)
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "export error")
        }
    }

    override suspend fun deleteLocalData(): DeletionResult {
        return try {
            // Clear every SharedPreferences file (all user progress/settings/session).
            // Bundled question content in the DB is left intact.
            val prefsDir = File(ctx.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles()?.forEach { f ->
                if (f.name.endsWith(".xml")) {
                    val name = f.name.removeSuffix(".xml")
                    ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                }
            }
            DeletionResult.LocalDone
        } catch (e: Exception) {
            DeletionResult.Error(e.message ?: "local delete error")
        }
    }

    override suspend fun deleteCloudData(userId: String?): DeletionResult =
        DeletionResult.LocalDoneCloudPending  // backend only — queued until connected

    override suspend fun deleteAccount(userId: String?): DeletionResult {
        return try {
            // Google Play requires a WORKING in-app deletion path for apps that require an account.
            // The remote result must NOT be swallowed: Firebase rejects user.delete() with
            // REQUIRES_RECENT_LOGIN for any session older than a few minutes — which is every returning
            // user, since a persistent session is an MVP feature. Reporting success while the account
            // still exists would be a false claim to the user and to Play.
            val repo = AuthProvider.repository(ctx)
            if (repo.isSignedIn()) {
                when (val remote = repo.deleteAccount()) {
                    is OpResult.Error -> return when (remote.code) {
                        AuthErrorCode.REQUIRES_RECENT_LOGIN ->
                            DeletionResult.Error(ctx.getString(R.string.data_rights_delete_reauth))
                        AuthErrorCode.NETWORK ->
                            DeletionResult.Error(ctx.getString(R.string.data_rights_delete_network))
                        else -> DeletionResult.Error(remote.message)
                    }
                    OpResult.Success -> Unit
                }
            }
            // Remote account is gone (or there was none) — now wipe the device and end the session.
            when (val local = deleteLocalData()) {
                is DeletionResult.Error -> local
                else -> {
                    runCatching { repo.signOut() }
                    DeletionResult.LocalDoneCloudPending // cloud erase runs server-side later
                }
            }
        } catch (e: Exception) {
            DeletionResult.Error(e.message ?: "account delete error")
        }
    }

    override fun revokeSessions() {
        AuthProvider.repository(ctx).signOut()
    }
}

/** Composition root for data-rights — returns a Firebase-backed service once connected. */
object DataRightsProvider {
    fun service(context: Context): DataRightsService =
        LocalDataRightsService(context.applicationContext)
}
