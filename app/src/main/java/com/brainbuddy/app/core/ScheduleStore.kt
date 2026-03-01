package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Time schedule rules: e.g. Weekdays 19:00-21:00 → Social apps always blocked.
 */
class ScheduleStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class ScheduleRule(
        val id: String,
        val daysOfWeek: Set<Int>, // 0=Sun, 1=Mon, ..., 6=Sat
        val startMinuteOfDay: Int, // 0-1439 (e.g. 19:00 = 1140)
        val endMinuteOfDay: Int,
        val targetGroup: String // "social" | "games" | "browsers" | "all"
    ) {
        fun contains(dayOfWeek: Int, minuteOfDay: Int): Boolean {
            if (dayOfWeek !in daysOfWeek) return false
            return if (startMinuteOfDay <= endMinuteOfDay) {
                minuteOfDay in startMinuteOfDay..endMinuteOfDay
            } else {
                minuteOfDay >= startMinuteOfDay || minuteOfDay <= endMinuteOfDay
            }
        }
    }

    fun getRules(): List<ScheduleRule> {
        val arr = JSONArray(prefs.getString(KEY_RULES, "[]"))
        return (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            val daysArr = o.optJSONArray("daysOfWeek") ?: JSONArray()
            val days = (0 until daysArr.length()).map { daysArr.getInt(it) }.toSet()
            ScheduleRule(
                id = o.optString("id", ""),
                daysOfWeek = days,
                startMinuteOfDay = o.optInt("startMinuteOfDay", 0),
                endMinuteOfDay = o.optInt("endMinuteOfDay", 1439),
                targetGroup = o.optString("targetGroup", "all")
            )
        }
    }

    fun setRules(rules: List<ScheduleRule>) {
        val arr = JSONArray()
        rules.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("daysOfWeek", JSONArray(r.daysOfWeek.toList()))
                put("startMinuteOfDay", r.startMinuteOfDay)
                put("endMinuteOfDay", r.endMinuteOfDay)
                put("targetGroup", r.targetGroup)
            })
        }
        prefs.edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun addRule(rule: ScheduleRule) {
        val list = getRules().toMutableList()
        val idx = list.indexOfFirst { it.id == rule.id }
        if (idx >= 0) list[idx] = rule else list.add(rule)
        setRules(list)
    }

    fun removeRule(id: String) = setRules(getRules().filter { it.id != id })

    /** Returns true if any schedule says "always blocked" for given time and target group. */
    fun isAlwaysBlockedNow(
        minuteOfDay: Int,
        dayOfWeek: Int,
        targetGroup: String,
        blockedPackages: Set<String>,
        appGroups: AppGroupPresets
    ): Boolean {
        val rules = getRules()
        for (r in rules) {
            if (!r.contains(dayOfWeek, minuteOfDay)) continue
            val pkgs = when (r.targetGroup) {
                "all" -> blockedPackages
                "social" -> appGroups.socialPackages.intersect(blockedPackages)
                "games" -> appGroups.gamesPackages.intersect(blockedPackages)
                "browsers" -> appGroups.browsersPackages.intersect(blockedPackages)
                else -> blockedPackages
            }
            if (pkgs.isNotEmpty()) return true
        }
        return false
    }

    companion object {
        private const val PREFS = "bb_schedules"
        private const val KEY_RULES = "schedule_rules"
    }
}
