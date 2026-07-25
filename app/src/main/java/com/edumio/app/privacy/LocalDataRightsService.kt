package com.edumio.app.privacy

import android.content.Context
import com.edumio.app.core.ProfileStore
import com.edumio.app.sync.PrefsSnapshotCodec
import com.edumio.app.sync.SyncRegistry
import org.json.JSONObject
import java.io.File

/**
 * Real on-device data rights for the account-free v1: export bundles all local study data into a
 * shareable JSON file. No user account, no auth backend and no cloud are involved — nothing here
 * fakes a server operation. There is deliberately no delete operation: v1 stores nothing off-device,
 * and uninstalling the app removes the on-device data.
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

}

/** Composition root for data-rights — returns a Firebase-backed service once connected. */
object DataRightsProvider {
    fun service(context: Context): DataRightsService =
        LocalDataRightsService(context.applicationContext)
}
