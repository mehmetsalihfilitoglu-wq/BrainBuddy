package com.mioacademy.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Persists exam-readiness over time for the active study area, so weekly/monthly
 * reports and the Coach can show an honest "readiness change" (this week vs last)
 * instead of a single live number. Real data only: one snapshot per day, capped.
 *
 * Area-scoped — IMAT readiness never mixes with TIL-I / SAT / CEnT-S.
 */
class ReadinessSnapshotStore(context: Context) {

    private val prefs = ProfileScopedPrefs.readinessSnapshots(context)

    data class Snapshot(val tsMs: Long, val score: Int)

    /** Records today's readiness once per day (updates today's if already set). */
    fun captureIfNewDay(score: Int) {
        val list = snapshots().toMutableList()
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val last = list.lastOrNull()
        if (last != null && TimeUnit.MILLISECONDS.toDays(last.tsMs) == today) {
            list[list.size - 1] = Snapshot(System.currentTimeMillis(), score)
        } else {
            list.add(Snapshot(System.currentTimeMillis(), score))
        }
        val trimmed = if (list.size > MAX) list.subList(list.size - MAX, list.size) else list
        save(trimmed)
    }

    fun snapshots(): List<Snapshot> {
        val arr = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Snapshot(o.optLong("ts"), o.optInt("score"))
        }.sortedBy { it.tsMs }
    }

    /** Most recent snapshot at or before [tsMs], for change comparisons. Null if none. */
    fun scoreOnOrBefore(tsMs: Long): Int? =
        snapshots().lastOrNull { it.tsMs <= tsMs }?.score

    private fun save(list: List<Snapshot>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("ts", it.tsMs).put("score", it.score)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "snapshots_json"
        private const val MAX = 180
    }
}
