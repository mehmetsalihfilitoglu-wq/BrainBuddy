package com.edumio.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Logs tamper events: service disabled, permission revoked, bypass attempts.
 */
class TamperStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun logEvent(type: TamperType) {
        val arr = JSONArray(prefs.getString(KEY_EVENTS, "[]"))
        val obj = JSONObject().apply {
            put("type", type.name)
            put("tsMs", System.currentTimeMillis())
        }
        arr.put(obj)
        trimAndSave(arr, 500)
    }

    fun getEvents(limit: Int = 100): List<TamperEvent> {
        val arr = JSONArray(prefs.getString(KEY_EVENTS, "[]"))
        val out = mutableListOf<TamperEvent>()
        val start = (arr.length() - limit).coerceAtLeast(0)
        for (i in start until arr.length()) {
            val o = arr.getJSONObject(i)
            val typeStr = o.optString("type", TamperType.SERVICE_DISABLED.name)
            val type = try { TamperType.valueOf(typeStr) } catch (_: Exception) { TamperType.SERVICE_DISABLED }
            out.add(TamperEvent(type = type, tsMs = o.optLong("tsMs", 0L)))
        }
        return out.reversed()
    }

    private fun trimAndSave(arr: JSONArray, maxSize: Int) {
        val trimmed = JSONArray()
        val start = (arr.length() - maxSize).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
    }

    enum class TamperType {
        SERVICE_DISABLED,
        PERMISSION_REVOKED,
        BYPASS_ATTEMPT
    }

    data class TamperEvent(val type: TamperType, val tsMs: Long)

    companion object {
        private const val PREFS = "bb_tamper"
        private const val KEY_EVENTS = "tamper_events"
    }
}
