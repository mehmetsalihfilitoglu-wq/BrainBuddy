package com.mioacademy.app.dailychallenge

/**
 * Pure runtime guardrails enforcing the Daily Challenge's hard product invariants (no Android deps →
 * testable, and cheap enough to assert on every challenge). A violation is a programming/data error;
 * callers log it via [DcEvents.GUARDRAIL_VIOLATION] and should fail closed (e.g. not serve the batch).
 */
object DailyChallengeGuardrails {

    data class Result(val ok: Boolean, val violations: List<String>) {
        companion object { val OK = Result(true, emptyList()) }
    }

    /** No challenge may ever contain more than CHALLENGE_SIZE new questions. */
    fun checkNewQuestionCount(count: Int): List<String> =
        if (count > DailyChallengeBlueprint.CHALLENGE_SIZE)
            listOf("new_question_count=$count exceeds cap ${DailyChallengeBlueprint.CHALLENGE_SIZE}")
        else emptyList()

    /** Premium must never change the NEW-question count for the same day/inputs. */
    fun checkPremiumNeutralCount(freeCount: Int, premiumCount: Int): List<String> =
        if (freeCount != premiumCount)
            listOf("premium_count=$premiumCount != free_count=$freeCount (premium must not add new questions)")
        else emptyList()

    /** Review may only re-surface already-seen questions — never a NEW (unseen) one. */
    fun checkReviewHasNoNewQuestions(reviewStates: Collection<QuestionLearnState>): List<String> {
        val illegal = reviewStates.filter { ReviewScheduler.queueOf(it) == null }
        return if (illegal.isNotEmpty())
            listOf("review contained non-review states: ${illegal.map { it.name }.distinct()}")
        else emptyList()
    }

    /** Served questions must have left the unseen pool (retirement after first exposure). */
    fun checkRetirement(servedIds: Collection<String>, remainingUnseenIds: Collection<String>): List<String> {
        val leaked = servedIds.filter { it in remainingUnseenIds }
        return if (leaked.isNotEmpty()) listOf("served-but-still-unseen: $leaked") else emptyList()
    }

    /** Every question in a challenge must belong to that challenge's exam (no cross-exam contamination). */
    fun checkExamIsolation(challengeExam: String, questionExamTypes: Collection<String>): List<String> {
        val foreign = questionExamTypes.filter { it != challengeExam }.distinct()
        return if (foreign.isNotEmpty()) listOf("foreign exam types in $challengeExam challenge: $foreign") else emptyList()
    }

    /** After completion, no reminder slot may fire. */
    fun checkNoReminderAfterCompletion(completedToday: Boolean, wouldFire: Boolean): List<String> =
        if (completedToday && wouldFire) listOf("reminder would fire after completion") else emptyList()

    /** Combined check for a freshly generated challenge. */
    fun evaluateChallenge(
        newQuestionCount: Int,
        challengeExam: String,
        questionExamTypes: Collection<String>,
        servedIds: Collection<String>,
        remainingUnseenIds: Collection<String>,
    ): Result {
        val v = ArrayList<String>()
        v += checkNewQuestionCount(newQuestionCount)
        v += checkExamIsolation(challengeExam, questionExamTypes)
        v += checkRetirement(servedIds, remainingUnseenIds)
        return Result(v.isEmpty(), v)
    }
}
