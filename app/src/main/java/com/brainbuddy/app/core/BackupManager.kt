package com.brainbuddy.app.core

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/** Full export/import: profiles, settings, blocked apps, schedules, stats, mastery, gate status. */
object BackupManager {

    fun exportBackup(context: Context) {
        val prefs = ProtectionPrefs(context)
        val blocked = BlockedAppsStore(context)
        val schedule = ScheduleStore(context)
        val profile = ProfileStore(context)
        val analytics = AnalyticsStore(context)
        val gam = GamificationStore(context)
        val report = ReportStore(context)

        val obj = JSONObject().apply {
            put("version", 2)
            put("blockedPackages", JSONArray(blocked.getBlockedPackages().toList()))
            put("profiles", JSONArray(profile.getProfiles().map { JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("sharedBlockedApps", it.sharedBlockedApps)
            } }))
            put("protectionEnabled", prefs.isProtectionEnabledRaw())
            put("quizIntervalMinutes", prefs.quizIntervalMinutes())
            put("minSuccessRatePercent", prefs.minSuccessRatePercent())
            put("studentLevel", prefs.studentLevel().name)
            put("schedules", JSONArray(schedule.getRules().map { r ->
                JSONObject().apply {
                    put("id", r.id)
                    put("daysOfWeek", JSONArray(r.daysOfWeek.toList()))
                    put("startMinuteOfDay", r.startMinuteOfDay)
                    put("endMinuteOfDay", r.endMinuteOfDay)
                    put("targetGroup", r.targetGroup)
                }
            }))
            put("xp", gam.xp())
            put("streakDays", gam.streakDays())
            put("freezeTokens", gam.freezeTokens())
            put("lastQuizPassedAtMs", prefs.lastQuizPassedAtMs())
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, obj.toString())
        }
        context.startActivity(Intent.createChooser(intent, context.getString(com.brainbuddy.app.R.string.backup_export)))
    }

    /** Returns import summary or null on parse error. */
    fun validateImport(context: Context, json: String): ImportSummary? {
        return try {
            val obj = JSONObject(json)
            val profiles = obj.optJSONArray("profiles")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            } ?: emptyList()
            val blocked = obj.optJSONArray("blockedPackages")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            } ?: emptyList()
            ImportSummary(
                profileCount = profiles.size,
                blockedAppCount = blocked.size,
                hasSchedules = obj.has("schedules"),
                hasStats = obj.has("xp")
            )
        } catch (_: Exception) { null }
    }

    fun importBackup(context: Context, json: String): Boolean {
        return try {
            val obj = JSONObject(json)
            val blockedArr = obj.optJSONArray("blockedPackages")
            if (blockedArr != null) {
                val pkgs = (0 until blockedArr.length()).mapNotNull { blockedArr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
                BlockedAppsStore(context).setBlockedPackages(pkgs)
            }
            val profilesArr = obj.optJSONArray("profiles")
            if (profilesArr != null && profilesArr.length() > 0) {
                val profiles = (0 until profilesArr.length()).mapNotNull {
                    val o = profilesArr.optJSONObject(it) ?: return@mapNotNull null
                    ProfileStore.Profile(
                        o.optString("id", "default"),
                        o.optString("name", "Öğrenci"),
                        o.optBoolean("sharedBlockedApps", true)
                    )
                }
                if (profiles.isNotEmpty()) ProfileStore(context).setProfiles(profiles)
            }
            if (obj.has("protectionEnabled")) ProtectionPrefs(context).setProtectionEnabled(obj.optBoolean("protectionEnabled", true))
            if (obj.has("quizIntervalMinutes")) ProtectionPrefs(context).setQuizIntervalMinutes(obj.optInt("quizIntervalMinutes", 30).coerceIn(30, 60))
            if (obj.has("minSuccessRatePercent")) ProtectionPrefs(context).setMinSuccessRatePercent(obj.optInt("minSuccessRatePercent", 60).coerceIn(50, 80))
            if (obj.has("studentLevel")) {
                try {
                    ProtectionPrefs(context).setStudentLevel(StudentLevel.valueOf(obj.optString("studentLevel", "AGE_3_5")))
                } catch (_: Exception) {}
            }
            val schedArr = obj.optJSONArray("schedules")
            if (schedArr != null) {
                val rules = (0 until schedArr.length()).mapNotNull {
                    val o = schedArr.optJSONObject(it) ?: return@mapNotNull null
                    val daysArr = o.optJSONArray("daysOfWeek") ?: JSONArray()
                    ScheduleStore.ScheduleRule(
                        o.optString("id", ""),
                        (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet(),
                        o.optInt("startMinuteOfDay", 0),
                        o.optInt("endMinuteOfDay", 1439),
                        o.optString("targetGroup", "all")
                    )
                }
                ScheduleStore(context).setRules(rules)
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
