package com.edumio.app.dailychallenge

import android.content.Context
import android.util.Log

/**
 * Analytics seam for the Daily Challenge. Kept behind an interface so a remote sink (Firebase
 * Analytics / Amplitude / a first-party endpoint) plugs in later without touching call sites —
 * see [DailyChallengeAnalyticsProvider]. The default local impl only logs + buffers in memory, so
 * nothing leaves the device until a real sink is wired.
 */
interface DailyChallengeAnalytics {
    fun track(name: String, params: Map<String, Any?> = emptyMap())
}

/** Canonical event names + param keys. Keep names stable — dashboards key off them. */
object DcEvents {
    const val CHALLENGE_GENERATED = "dc_challenge_generated"
    const val CHALLENGE_STARTED = "dc_challenge_started"
    const val QUESTION_ANSWERED = "dc_question_answered"
    const val CHALLENGE_COMPLETED = "dc_challenge_completed"
    const val REVIEW_ANSWERED = "dc_review_answered"
    const val REMINDER_SHOWN = "dc_reminder_shown"
    const val REMINDER_SUPPRESSED = "dc_reminder_suppressed"
    const val STREAK_INCREMENTED = "dc_streak_incremented"
    const val MILESTONE_REACHED = "dc_milestone_reached"
    const val GUARDRAIL_VIOLATION = "dc_guardrail_violation"

    // Premium solutions + wrong-question pool. Params carry question IDs only — never stems,
    // options, or solution bodies (see premium_solution_architecture.md §Analytics).
    const val SOL_OPENED = "sol_opened"
    const val SOL_CTA_SHOWN = "sol_cta_shown"
    const val SOL_CTA_CONVERTED = "sol_cta_converted"
    const val RETRY_STARTED = "wp_retry_started"
    const val RETRY_CORRECT = "wp_retry_correct"
    const val RETRY_WRONG = "wp_retry_wrong"
    const val WRONG_POOL_RESOLVED = "wp_item_resolved"
    const val WRONG_POOL_RESCHEDULED = "wp_item_rescheduled"

    const val P_EXAM = "exam"
    const val P_LOCAL_DATE = "local_date"
    const val P_SECTION = "section"
    const val P_COUNT = "count"
    const val P_SHORTAGE = "shortage"
    const val P_IS_CORRECT = "is_correct"
    const val P_SCORE = "score"
    const val P_SLOT = "slot"
    const val P_REASON = "reason"
    const val P_NEW_STATE = "new_state"
    const val P_STREAK = "streak"
    const val P_QUESTION_ID = "question_id" // an internal id, never content

}

/** Default on-device implementation: logs and keeps a small ring buffer for QA/debug. */
class LocalDailyChallengeAnalytics : DailyChallengeAnalytics {
    private val ring = ArrayDeque<String>()

    override fun track(name: String, params: Map<String, Any?>) {
        val line = if (params.isEmpty()) name else "$name ${params.entries.joinToString(",") { "${it.key}=${it.value}" }}"
        Log.i(TAG, line)
        synchronized(ring) {
            ring.addLast(line)
            while (ring.size > MAX) ring.removeFirst()
        }
    }

    /** Recent events (newest last) — used by debug tooling only. */
    fun recent(): List<String> = synchronized(ring) { ring.toList() }

    companion object {
        private const val TAG = "DcAnalytics"
        private const val MAX = 200
    }
}

/** Composition root for the analytics sink. Swap the local impl for a remote one here. */
object DailyChallengeAnalyticsProvider {
    @Volatile private var instance: DailyChallengeAnalytics? = null

    fun get(@Suppress("UNUSED_PARAMETER") context: Context? = null): DailyChallengeAnalytics =
        instance ?: synchronized(this) { instance ?: LocalDailyChallengeAnalytics().also { instance = it } }

    /** Tests / a future remote provider install their sink here. */
    fun set(sink: DailyChallengeAnalytics) { instance = sink }
}
