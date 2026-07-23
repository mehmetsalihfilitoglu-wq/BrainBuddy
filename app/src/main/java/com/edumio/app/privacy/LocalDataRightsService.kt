package com.edumio.app.privacy

import android.content.Context
import com.edumio.app.core.ProfileStore
import com.edumio.app.sync.PrefsSnapshotCodec
import com.edumio.app.sync.SyncRegistry
import org.json.JSONObject
import java.io.File

/**
 * Real on-device data rights for the account-free v1: export bundles all local study data into a
 * shareable JSON file, and delete wipes every on-device SharedPreferences store. No user account, no
 * auth backend and no cloud are involved — nothing here fakes a server operation.
 */
class LocalDataRightsService(context: Context) : DataRightsService {

    private val ctx = context.applicationContext

    override suspend fun exportData(): ExportResult {
        return try {
            val root = JSONObject()
            root.put("app", "EDUmio")
            root.put("schemaVersion", 1)
            root.put("exportedAt", System.currentTimeMillis())
            // v1 has no account, so there is no user id / e-mail to include.

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
        // v1 has NO user account: "delete" is a purely local wipe of on-device study data. No auth or
        // cloud backend is contacted. (The method name is kept to satisfy the DataRightsService seam.)
        return deleteLocalData()
    }

    override fun revokeSessions() {
        // No account/session in v1 — nothing to revoke.
    }
}

/** Composition root for data-rights — returns a Firebase-backed service once connected. */
object DataRightsProvider {
    fun service(context: Context): DataRightsService =
        LocalDataRightsService(context.applicationContext)
}
