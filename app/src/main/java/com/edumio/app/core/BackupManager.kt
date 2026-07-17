package com.edumio.app.core

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    fun exportBackup(context: Context) {
        val profile = ProfileStore(context)
        val analytics = AnalyticsStore(context)
        val gam = GamificationStore(context)
        val report = ReportStore(context)

        val obj = JSONObject().apply {
            put("version", 3)
            put("profiles", JSONArray(profile.getProfiles().map { JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("sharedBlockedApps", it.sharedBlockedApps)
                put("careerPath", it.careerPath)
            } }))
            put("xp", gam.xp())
            put("streakDays", gam.streakDays())
            put("freezeTokens", gam.freezeTokens())
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, obj.toString())
        }
        context.startActivity(Intent.createChooser(intent, context.getString(com.edumio.app.R.string.backup_export)))
    }

    fun validateImport(context: Context, json: String): ImportSummary? {
        return try {
            val obj = JSONObject(json)
            val profiles = obj.optJSONArray("profiles")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            } ?: emptyList()
            ImportSummary(
                profileCount = profiles.size,
                blockedAppCount = 0,
                hasSchedules = false,
                hasStats = obj.has("xp")
            )
        } catch (_: Exception) { null }
    }

    fun importBackup(context: Context, json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val profilesArr = obj.optJSONArray("profiles")
            if (profilesArr != null && profilesArr.length() > 0) {
                val profiles = (0 until profilesArr.length()).mapNotNull {
                    val o = profilesArr.optJSONObject(it) ?: return@mapNotNull null
                    ProfileStore.Profile(
                        o.optString("id", "default"),
                        o.optString("name", "Öğrenci"),
                        o.optBoolean("sharedBlockedApps", true),
                        o.optString("careerPath", CareerPath.OTHER.name)
                    )
                }
                if (profiles.isNotEmpty()) ProfileStore(context).setProfiles(profiles)
            }
            true
        } catch (_: Exception) { false }
    }

    data class ImportSummary(
        val profileCount: Int,
        val blockedAppCount: Int,
        val hasSchedules: Boolean,
        val hasStats: Boolean
    )
}
