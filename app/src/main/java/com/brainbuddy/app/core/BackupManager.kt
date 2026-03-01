package com.brainbuddy.app.core

import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** Export settings and stats as JSON. */
object BackupManager {

    fun exportBackup(context: Context) {
        val prefs = ProtectionPrefs(context)
        val blocked = BlockedAppsStore(context)
        val schedule = ScheduleStore(context)
        val profile = ProfileStore(context)
        val analytics = AnalyticsStore(context)
        val gam = GamificationStore(context)

        val obj = JSONObject().apply {
            put("version", 1)
            put("blockedPackages", org.json.JSONArray(blocked.getBlockedPackages().toList()))
            put("profiles", org.json.JSONArray(profile.getProfiles().map { JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("sharedBlockedApps", it.sharedBlockedApps)
            } }))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, obj.toString())
        }
        context.startActivity(Intent.createChooser(intent, context.getString(com.brainbuddy.app.R.string.backup_export)))
    }

    fun importBackup(context: Context, json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val blockedArr = obj.optJSONArray("blockedPackages")
            if (blockedArr != null) {
                val pkgs = (0 until blockedArr.length()).map { blockedArr.getString(it) }.toSet()
                BlockedAppsStore(context).setBlockedPackages(pkgs)
            }
            true
        } catch (_: Exception) { false }
    }
}
