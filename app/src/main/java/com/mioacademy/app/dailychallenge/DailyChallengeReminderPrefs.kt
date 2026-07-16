package com.mioacademy.app.dailychallenge

import android.content.Context

/**
 * Tiny local store backing the reminder gate. Holds two per-day facts so reminder workers can decide
 * cheaply (no DB round-trip) whether to post:
 *  - the local date on which the user completed a Daily Challenge (suppresses remaining slots), and
 *  - which reminder slots have already fired today (idempotency against WorkManager re-runs).
 */
class DailyChallengeReminderPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Marks today's challenge as completed → every remaining reminder slot is suppressed. */
    fun markCompleted(localDate: String) =
        prefs.edit().putString(KEY_COMPLETED_DATE, localDate).apply()

    fun isCompletedOn(localDate: String): Boolean =
        prefs.getString(KEY_COMPLETED_DATE, null) == localDate

    fun markSlotFired(localDate: String, slotIndex: Int) =
        prefs.edit().putString(slotKey(slotIndex), localDate).apply()

    fun hasSlotFired(localDate: String, slotIndex: Int): Boolean =
        prefs.getString(slotKey(slotIndex), null) == localDate

    private fun slotKey(slotIndex: Int) = "$KEY_SLOT_FIRED_PREFIX$slotIndex"

    companion object {
        private const val PREFS = "dc_reminder_prefs"
        private const val KEY_COMPLETED_DATE = "completed_date"
        private const val KEY_SLOT_FIRED_PREFIX = "slot_fired_"
    }
}
