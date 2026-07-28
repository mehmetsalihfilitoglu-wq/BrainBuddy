package com.edumio.app.dailychallenge

import com.edumio.app.core.TestPerformance
import com.edumio.app.core.TopicCounts

/**
 * Records a completed Daily Challenge into the learning-statistics store — exactly once.
 *
 * WHY THIS EXISTS. The statistics card reads AnalyticsStore, but nothing in the v1 flow ever wrote to
 * it: recordTestPerformance() had a single caller in the removed subject-practice flow. So the card
 * could never leave its empty state no matter how many daily challenges the user finished.
 *
 * WHERE IT IS CALLED FROM. The authoritative call site is the DOMAIN completion transition in
 * [DailyChallengeEngine.submitAnswer] — the same `answered >= CHALLENGE_SIZE && status != COMPLETED`
 * edge that marks the challenge COMPLETED and updates the streak. It is deliberately NOT the result
 * screen: that Activity may never launch (process death after the fifth answer, failed navigation,
 * user backgrounding the app), and statistics must not depend on a screen being shown.
 *
 * IDEMPOTENCE. Every record is keyed `dc_<examProfile>_<localDate>`. A challenge is one per exam per
 * local day, so that key is a durable natural identity. [shouldRecord] refuses a key that is already
 * present, which is what makes reopening, retrying, Activity recreation, process restore and the
 * repair path below all safe — correctness comes from the key, not from the call site firing once.
 *
 * REPAIR. If the write fails (or the process dies between marking COMPLETED and writing), the engine
 * retries opportunistically whenever it loads an already-completed challenge. Because the key check
 * runs first, the retry either fills the gap or does nothing; it can never double-count.
 */
object DailyChallengeStatsRecorder {

    /** Durable identity of one completed challenge. One challenge per exam per local day. */
    fun quizId(examProfile: String, localDate: String): String = "dc_${examProfile}_$localDate"

    /**
     * True when this completion should be written. False for anything not genuinely finished, so a
     * partially answered challenge can never inflate the completed-session count, and false when the
     * key is already stored.
     */
    fun shouldRecord(
        completion: DailyChallengeEngine.Completion,
        existingQuizIds: Set<String>,
    ): Boolean {
        val expected = DailyChallengeBlueprint.CHALLENGE_SIZE
        // Guard 1 — only a genuinely complete challenge counts as a session.
        if (completion.total != expected) return false
        if (completion.reviews.size != expected) return false
        // Guard 2 — never record the same challenge twice.
        return quizId(completion.examProfile, completion.localDate) !in existingQuizIds
    }

    /**
     * Maps a completion onto the existing statistics model. Nothing is invented: every field comes
     * from data the challenge already holds, and only fields the current statistics UI consumes are
     * populated (counts drive Accuracy / Tests / Questions; section counts feed topic mastery).
     */
    fun toPerformance(
        completion: DailyChallengeEngine.Completion,
        nowMs: Long,
    ): TestPerformance {
        val correct = completion.reviews.count { it.isCorrect }
        val wrong = completion.reviews.size - correct
        // The Daily Challenge cannot be submitted with a blank: every question is answered to advance.
        val blank = 0
        val total = completion.reviews.size
        val accuracy = if (total == 0) 0f else (correct * 100f / total)

        val byTopicCounts = LinkedHashMap<String, TopicCounts>()
        for ((section, sectionTotal) in completion.sectionTotal) {
            val sectionCorrect = completion.sectionCorrect[section] ?: 0
            byTopicCounts[section] = TopicCounts(
                correct = sectionCorrect,
                wrong = (sectionTotal - sectionCorrect).coerceAtLeast(0),
                blank = 0,
                total = sectionTotal,
            )
        }
        val byTopic = byTopicCounts.mapValues { (_, tc) ->
            if (tc.total == 0) 0f else tc.correct * 100f / tc.total
        }

        return TestPerformance(
            quizId = quizId(completion.examProfile, completion.localDate),
            tsMs = nowMs,
            accuracy = accuracy,
            correctCount = correct,
            wrongCount = wrong,
            blankCount = blank,
            totalQuestions = total,
            passed = correct >= (total + 1) / 2,
            wrongQuestionIds = completion.reviews.filterNot { it.isCorrect }.map { it.questionId },
            questionIds = completion.reviews.map { it.questionId },
            byTopic = byTopic,
            byTopicCounts = byTopicCounts,
        )
    }
}

/**
 * Narrow seam so the engine — which is pure domain code with no Android Context — can persist
 * statistics. The default is a no-op, so unit tests and any non-production wiring are unaffected.
 */
interface DailyChallengeStatsSink {
    /** Must be idempotent for a given completion. Must never throw. */
    fun recordIfAbsent(completion: DailyChallengeEngine.Completion, nowMs: Long)

    object NoOp : DailyChallengeStatsSink {
        override fun recordIfAbsent(completion: DailyChallengeEngine.Completion, nowMs: Long) = Unit
    }
}
