package com.brainbuddy.app.core

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages per-day missions: generates defaults, tracks progress, and persists state.
 *
 * Storage: ProfileScopedPrefs.quizPrefs (single JSON blob).
 */
class DailyMissionManager(private val context: Context) {

    private val prefs = ProfileScopedPrefs.quizPrefs(context)

    companion object {
        private const val KEY_MISSION_JSON = "daily_mission_v1"
        private const val TAG = "DailyMission"

        private const val DEFAULT_TESTS_TARGET = 2
        private const val DEFAULT_RETRY_TARGET = 5
        private const val DEFAULT_WEAK_TOPIC_TARGET = 1
    }

    /** Today's date as yyyy-MM-dd in device locale. */
    private fun todayString(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return fmt.format(Date())
    }

    /** Load today's mission, generating a new one if needed (date change or missing). */
    fun getTodayMission(): DailyMission {
        val today = todayString()
        val raw = prefs.getString(KEY_MISSION_JSON, null)
        if (raw.isNullOrBlank()) {
            val m = DailyMission(
                date = today,
                testsTarget = DEFAULT_TESTS_TARGET,
                testsDone = 0,
                retryTarget = DEFAULT_RETRY_TARGET,
                retryDone = 0,
                weakTopicTarget = DEFAULT_WEAK_TOPIC_TARGET,
                weakTopicDone = 0
            )
            save(m)
            return m
        }
        return try {
            val o = JSONObject(raw)
            val date = o.optString("date", today)
            val mission = DailyMission(
                date = date,
                testsTarget = o.optInt("testsTarget", DEFAULT_TESTS_TARGET),
                testsDone = o.optInt("testsDone", 0),
                retryTarget = o.optInt("retryTarget", DEFAULT_RETRY_TARGET),
                retryDone = o.optInt("retryDone", 0),
                weakTopicTarget = o.optInt("weakTopicTarget", DEFAULT_WEAK_TOPIC_TARGET),
                weakTopicDone = o.optInt("weakTopicDone", 0)
            )
            if (mission.date != today) {
                // New day, generate fresh mission with same defaults.
                val fresh = mission.copy(
                    date = today,
                    testsDone = 0,
                    retryDone = 0,
                    weakTopicDone = 0
                )
                save(fresh)
                fresh
            } else {
                mission
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse mission JSON, regenerating", e)
            val m = DailyMission(
                date = today,
                testsTarget = DEFAULT_TESTS_TARGET,
                testsDone = 0,
                retryTarget = DEFAULT_RETRY_TARGET,
                retryDone = 0,
                weakTopicTarget = DEFAULT_WEAK_TOPIC_TARGET,
                weakTopicDone = 0
            )
            save(m)
            m
        }
    }

    private fun save(mission: DailyMission) {
        val o = JSONObject().apply {
            put("date", mission.date)
            put("testsTarget", mission.testsTarget)
            put("testsDone", mission.testsDone)
            put("retryTarget", mission.retryTarget)
            put("retryDone", mission.retryDone)
            put("weakTopicTarget", mission.weakTopicTarget)
            put("weakTopicDone", mission.weakTopicDone)
        }
        prefs.edit().putString(KEY_MISSION_JSON, o.toString()).apply()
    }

    /** Increment testsDone for today. */
    fun onTestCompleted() {
        val m = getTodayMission()
        val updated = m.copy(testsDone = (m.testsDone + 1).coerceAtMost(m.testsTarget))
        save(updated)
        Log.d(TAG, "testsDone=${updated.testsDone}/${updated.testsTarget}")
    }

    /** Increment retryDone for today (called when a previously wrong question is solved correctly). */
    fun onRetrySolved() {
        val m = getTodayMission()
        val updated = m.copy(retryDone = (m.retryDone + 1).coerceAtMost(m.retryTarget))
        save(updated)
        Log.d(TAG, "retryDone=${updated.retryDone}/${updated.retryTarget}")
    }

    /** Increment weakTopicDone for today (when a test explicitly targets the weakest topic). */
    fun onWeakTopicTestTaken() {
        val m = getTodayMission()
        val updated = m.copy(weakTopicDone = (m.weakTopicDone + 1).coerceAtMost(m.weakTopicTarget))
        save(updated)
        Log.d(TAG, "weakTopicDone=${updated.weakTopicDone}/${updated.weakTopicTarget}")
    }
}

