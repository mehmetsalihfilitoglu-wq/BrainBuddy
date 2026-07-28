package com.edumio.app.dailychallenge

import android.content.Context
import android.util.Log
import com.edumio.app.core.ActiveProfileManager
import com.edumio.app.core.AnalyticsStore
import com.edumio.app.core.StudyAreaManager

/**
 * Production [DailyChallengeStatsSink]: persists a completed Daily Challenge into the learning
 * statistics the StudyHub card reads.
 *
 * EXAM SCOPING. The entry is written to the store of the profile whose study area owns the
 * completion's examProfile — NOT simply the profile that happens to be active when the write runs.
 * The two differ whenever the user switches areas between finishing a challenge and the write being
 * retried, and mixing them would corrupt the per-exam isolation the statistics rely on.
 *
 * FAILURE ISOLATION. Never throws. Challenge completion is the user's real progress; a statistics
 * write is bookkeeping. If persistence fails the completion still stands, and the engine's repair
 * path retries the (idempotent) write the next time it loads that completed challenge.
 */
class AndroidDailyChallengeStatsSink(context: Context) : DailyChallengeStatsSink {

    private val appContext = context.applicationContext

    override fun recordIfAbsent(completion: DailyChallengeEngine.Completion, nowMs: Long) {
        try {
            val profileId = profileIdForExam(completion.examProfile)
            val store = AnalyticsStore.forProfile(appContext, profileId)
            if (!DailyChallengeStatsRecorder.shouldRecord(completion, store.performanceQuizIds())) return
            store.recordTestPerformance(DailyChallengeStatsRecorder.toPerformance(completion, nowMs))
        } catch (t: Throwable) {
            // Bookkeeping must never take down real progress. The engine retries on next load.
            Log.w(TAG, "daily-challenge stats write failed; will retry on next load", t)
        }
    }

    /**
     * Resolves the study area that owns [examProfile]. Falls back to the active profile so a
     * completion is still recorded somewhere rather than dropped if no area matches (e.g. the area
     * was removed between playing and writing).
     */
    private fun profileIdForExam(examProfile: String): String {
        val match = StudyAreaManager.getAreas(appContext)
            .firstOrNull { it.career.examType.name == examProfile }
        return match?.id ?: ActiveProfileManager.getActiveProfileId(appContext)
    }

    private companion object {
        const val TAG = "DcStatsSink"
    }
}
