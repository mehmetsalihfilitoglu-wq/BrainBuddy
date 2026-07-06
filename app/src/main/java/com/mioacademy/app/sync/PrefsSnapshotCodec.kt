package com.mioacademy.app.sync

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes a SharedPreferences store to a typed JSON snapshot and back, and derives
 * a stable content hash for change detection. Type tags keep Int/Long/Float/Boolean/
 * String/StringSet round-tripping losslessly across the sync boundary.
 */
object PrefsSnapshotCodec {

    fun encode(all: Map<String, *>): JSONObject {
        val o = JSONObject()
        for ((k, v) in all) {
            val e = JSONObject()
            when (v) {
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is String -> { e.put("t", "s"); e.put("v", v) }
                is Set<*> -> {
                    e.put("t", "ss")
                    val arr = JSONArray()
                    v.filterIsInstance<String>().forEach { arr.put(it) }
                    e.put("v", arr)
                }
                else -> continue
            }
            o.put(k, e)
        }
        return o
    }

    /** Replaces the store's contents with [payload] (store-level last-write-wins). */
    fun applyInto(prefs: SharedPreferences, payload: JSONObject) {
        val editor = prefs.edit()
        editor.clear()
        val keys = payload.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val e = payload.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "b" -> editor.putBoolean(k, e.optBoolean("v"))
                "i" -> editor.putInt(k, e.optInt("v"))
                "l" -> editor.putLong(k, e.optLong("v"))
                "f" -> editor.putFloat(k, e.optDouble("v").toFloat())
                "s" -> editor.putString(k, e.optString("v"))
                "ss" -> {
                    val arr = e.optJSONArray("v") ?: JSONArray()
                    val set = HashSet<String>(arr.length())
                    for (i in 0 until arr.length()) set.add(arr.optString(i))
                    editor.putStringSet(k, set)
                }
            }
        }
        editor.apply()
    }

    /** Order-independent content hash used to detect local changes since last sync. */
    fun contentHash(payload: JSONObject): String {
        val sb = StringBuilder()
        payload.keys().asSequence().sorted().forEach { k ->
            sb.append(k).append('=').append(payload.optJSONObject(k)?.toString()).append(';')
        }
        return Integer.toHexString(sb.toString().hashCode())
    }
}
