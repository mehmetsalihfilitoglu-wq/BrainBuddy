package com.edumio.app.dailychallenge

import android.content.Context
import com.edumio.app.core.ExamType
import com.edumio.app.db.DatabaseProvider
import com.edumio.app.db.QuestionCandidateRow
import com.edumio.app.db.QuestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.TimeZone
import kotlin.random.Random

/** Read-only content the engine needs (from edumio.db). Injected so the engine is unit-testable. */
interface DcContentSource {
    suspend fun getDailyCandidatePool(examType: String, section: String): List<QuestionCandidateRow>
    suspend fun getQuestionsByIds(ids: List<String>): List<QuestionEntity>
}

/** Production content source backed by the shared content database (edumio.db / QuestionDao). */
private class RoomContentSource(private val appContext: Context) : DcContentSource {
    override suspend fun getDailyCandidatePool(examType: String, section: String) =
        DatabaseProvider.get(appContext).questionDao().getDailyCandidatePool(examType, section)

    override suspend fun getQuestionsByIds(ids: List<String>) =
        DatabaseProvider.get(appContext).questionDao().getQuestionsByIds(ids)
}

/**
 * The Daily Challenge engine. Guarantees the hard product invariants:
 *  - EXACTLY ONE challenge per (user account, local calendar day) — NOT one per exam,
 *  - exactly 5 NEW questions, generated ONCE and then IMMUTABLE (same ids, same order, forever),
 *  - idempotent retrieval: once a day's challenge exists it is ALWAYS returned — reopening, process
 *    death, rotation, switching exams, or opening Review/Premium never generate a second one,
 *  - Premium NEVER increases the new-question count (no premium input affects generation),
 *  - only production-eligible questions, drawn ONLY from the user's NEVER_SEEN pool,
 *  - blueprint-proportional section allocation (cumulative deficit) decided BEFORE selection,
 *  - each served question is retired from the unseen pool immediately (appears once, ever).
 *
 * Progress belongs to the account ([userId]); a different account (different email → different auth
 * uid) has an entirely independent keyspace and therefore starts from zero.
 *
 * Content is read from edumio.db via [DcContentSource]; user progress lives in daily_challenge.db.
 * All external dependencies are injected, so every invariant above is provable in a pure-JVM test
 * with an in-memory [DailyChallengeDao] and a fake [DcContentSource].
 */
class DailyChallengeEngine internal constructor(
    private val dao: DailyChallengeDao,
    private val content: DcContentSource,
    private val analytics: DailyChallengeAnalytics,
    private val onChallengeCompleted: (localDate: String) -> Unit,
    // Learning-statistics persistence. Defaulted to a no-op so existing wiring and unit tests are
    // untouched; production supplies the Android-backed sink below.
    private val statsSink: DailyChallengeStatsSink = DailyChallengeStatsSink.NoOp,
) {

    /** Production wiring: real Room stores + analytics + local reminder scheduler. */
    constructor(appContext: Context) : this(
        dao = DailyChallengeDatabase.get(appContext).dailyChallengeDao(),
        content = RoomContentSource(appContext.applicationContext),
        analytics = DailyChallengeAnalyticsProvider.get(appContext),
        onChallengeCompleted = { date -> DailyChallengeReminderScheduler.onChallengeCompleted(appContext, date) },
        statsSink = AndroidDailyChallengeStatsSink(appContext),
    )

    private companion object {
        // Process-wide: engine instances are created per-Activity, so the generation lock must be
        // shared across ALL of them to serialize same-day challenge creation (no double-generate race).
        val generationLock = Mutex()

        /** Never displace more than this many of the day's new questions with scheduled reviews. */
        const val MAX_INJECTED_REVIEWS = 2
    }

    data class Result(
        val challenge: DailyChallengeEntity,
        val questions: List<QuestionEntity>,
        val answered: Int,
        val total: Int,
        val completed: Boolean,
    )

    /** Answer/progress key — exam-agnostic, so it is stable even if the active exam changes mid-day. */
    private fun key(userId: String, localDate: String) = "$userId:$localDate"

    /** Local calendar date (YYYY-MM-DD) for the user's timezone. Delegates to [DailyChallengeDates]. */
    fun localDate(zone: TimeZone = TimeZone.getDefault(), nowMs: Long = System.currentTimeMillis()): String =
        DailyChallengeDates.localDate(zone, nowMs)

    /** Millis at the end of the given local day (next midnight) — challenge expiry. */
    fun endOfLocalDay(localDate: String, zone: TimeZone = TimeZone.getDefault()): Long =
        DailyChallengeDates.endOfLocalDay(localDate, zone)

    /**
     * Returns today's ONE Daily Challenge, generating it once if — and only if — none exists yet for
     * this account+day. A second call the same day (any exam, any entry point) returns the SAME
     * challenge and never a new one. [exam] is used ONLY as the generation source when there is nothing
     * to return yet; it never re-keys or replaces an existing challenge. [isPremium] is accepted only to
     * make the invariant explicit in tests — it is intentionally IGNORED for question count.
     */
    suspend fun getOrCreateToday(
        userId: String,
        exam: ExamType,
        zone: TimeZone = TimeZone.getDefault(),
        nowMs: Long = System.currentTimeMillis(),
        @Suppress("UNUSED_PARAMETER") isPremium: Boolean = false,
        rngSeed: Long? = null,
    ): Result? = withContext(Dispatchers.IO) {
        val day = localDate(zone, nowMs)

        // 1) Immutable return: if today's challenge already exists, ALWAYS return it — regardless of
        //    which exam is active now (it may have been generated from a different one). No regeneration.
        dao.getChallengeForDay(userId, day)?.let { existing ->
            // REPAIR PATH: if this challenge is already COMPLETED, make sure its statistics entry
            // exists. Covers a completion whose stats write failed or was interrupted (process death
            // between marking COMPLETED and writing). Idempotent — keyed by exam + local date — so a
            // challenge already recorded is left untouched and can never be counted twice.
            if (existing.status == ChallengeStatus.COMPLETED.name) {
                recordCompletionStats(userId, day)
            }
            return@withContext resolve(existing)
        }

        // 2) No challenge yet. We can only generate for a supported exam; otherwise there is nothing to
        //    show. (An existing challenge is served in step 1 even when the active exam is unsupported.)
        if (!DailyChallengeBlueprint.isSupported(exam)) return@withContext null
        val examType = exam.name

        // 3) Serialize generation: a concurrent caller (e.g. the home card refreshing while the flow
        //    screen opens) must never generate or retire two challenges for the same day. Double-checked
        //    under the lock so the winner's challenge is reused rather than regenerated.
        return@withContext generationLock.withLock {
            dao.getChallengeForDay(userId, day)?.let { existing ->
                return@withLock resolve(existing)
            }

            // ── ledgers ──
            val deficits = dao.getDeficits(userId, examType)
            val expected = HashMap<String, Double>(); val actual = HashMap<String, Double>()
            val subExpected = HashMap<String, Double>(); val subActual = HashMap<String, Double>()
            for (d in deficits) {
                if (d.section.contains('/')) { subExpected[d.section] = d.expectedToDate; subActual[d.section] = d.actualToDate }
                else { expected[d.section] = d.expectedToDate; actual[d.section] = d.actualToDate }
            }

            val alloc = DailyChallengeBlueprint.allocate(exam, expected, actual)
            val seen = dao.getSeenQuestionIds(userId, examType).toHashSet()
            val rng = Random(rngSeed ?: (day.hashCode().toLong() * 1_000_003L + userId.hashCode()))

            val selected = ArrayList<QuestionCandidateRow>()
            val usedTopics = HashSet<String>(); val usedStems = HashSet<String>()
            val shortages = ArrayList<String>()
            val leftoverPools = ArrayList<QuestionCandidateRow>() // unseen candidates not picked, for reallocation

            for ((section, count) in alloc) {
                if (count <= 0) continue
                val pool = content.getDailyCandidatePool(examType, section).filter { it.id !in seen && it.stemHash !in usedStems }
                val picked = if (DailyChallengeBlueprint.SUBSECTIONS.containsKey(section)) {
                    pickWithSubSections(section, count, pool, subExpected, subActual, usedTopics, usedStems, rng)
                } else {
                    DailyChallengeSelection.pickN(pool, count, usedTopics, usedStems, rng)
                }
                selected += picked
                leftoverPools += pool.filter { p -> picked.none { it.id == p.id } && p.stemHash !in usedStems }
                if (picked.size < count) shortages += "$section:${picked.size}/$count"
            }

            // ── reconcile to exactly 5 without substituting the blueprint silently (record shortage) ──
            var deficitSlots = DailyChallengeBlueprint.CHALLENGE_SIZE - selected.size
            if (deficitSlots > 0) {
                val fill = DailyChallengeSelection.pickN(leftoverPools.distinctBy { it.id }, deficitSlots, usedTopics, usedStems, rng)
                selected += fill
                deficitSlots = DailyChallengeBlueprint.CHALLENGE_SIZE - selected.size
            }
            // Fail CLOSED on undersupply: the challenge is "exactly 5 NEW questions, never fewer". If the
            // unseen pool can't fill all 5 slots, serve NOTHING today (Home shows the empty state) rather
            // than a 1–4 question challenge that could never satisfy the `answered >= 5` completion gate
            // (which would strand it IN_PROGRESS forever and never fire streak/reminder resolution).
            if (selected.size < DailyChallengeBlueprint.CHALLENGE_SIZE) return@withLock null

            // ── guardrail BEFORE any side effect: never more than CHALLENGE_SIZE new questions ──
            // Runs prior to ledger/retirement writes so a fail-closed batch leaves NO trace.
            val sizeViolations = DailyChallengeGuardrails.checkNewQuestionCount(selected.size)
            if (sizeViolations.isNotEmpty()) {
                analytics.track(DcEvents.GUARDRAIL_VIOLATION, mapOf(DcEvents.P_REASON to sizeViolations.joinToString("; ")))
                return@withLock null // fail closed: never serve an over-cap batch, and never retire it
            }

            // ── inject due scheduled reviews, BEFORE anything is persisted ──
            // At most two due reviews REPLACE same-section new picks, so the challenge stays exactly
            // CHALLENGE_SIZE and the blueprint's section distribution is untouched (which is also why
            // the deficit ledgers below stay correct — the count per section never changes).
            // A displaced question is simply not shown: nothing here marks it exposed, and retirement
            // happens only at answer time, so it remains fully eligible for a later challenge.
            // Every due review, oldest first — NOT pre-capped: eligibility depends on today's sections,
            // so trimming before that check could let older ineligible reviews mask an eligible one.
            val dueReviews = dao.getDueReviews(userId, examType, nowMs)
                .map { DailyChallengeSelection.DueReview(it.questionId, it.section, it.nextReviewAt) }
            val injection = DailyChallengeSelection.injectDueReviews(selected, dueReviews, MAX_INJECTED_REVIEWS)

            // ── persist ledgers ──
            for ((s, v) in expected) dao.upsertDeficit(SectionDeficitEntity(userId, examType, s, v, actual[s] ?: 0.0))
            for ((s, v) in subExpected) dao.upsertDeficit(SectionDeficitEntity(userId, examType, s, v, subActual[s] ?: 0.0))

            // ── NO retirement here ──
            // Questions are CONSUMED when they are ANSWERED (see submitAnswer), not when the challenge
            // is generated. Merely opening Home generates today's challenge; retiring at that moment
            // permanently burned five unseen questions even if the student never answered one. The
            // day's five ids are already persisted in the immutable challenge row below, so reopening
            // returns exactly the same unfinished challenge and partial progress survives a restart —
            // while anything left unanswered stays in the unseen pool and can be served again later.

            val challenge = DailyChallengeEntity(
                userId = userId, localDate = day,
                challengeId = "$userId:$day",
                examProfile = examType,
                status = ChallengeStatus.AVAILABLE.name,
                questionIdsCsv = injection.orderedIds.joinToString(","),
                currentIndex = 0,
                allocationCsv = alloc.entries.joinToString("|") { "${it.key}:${it.value}" },
                createdAt = nowMs,
                expiresAt = endOfLocalDay(day, zone),
                shortage = shortages.joinToString(";"),
            )
            dao.upsertChallenge(challenge)
            analytics.track(
                DcEvents.CHALLENGE_GENERATED,
                mapOf(
                    DcEvents.P_EXAM to examType, DcEvents.P_LOCAL_DATE to day,
                    DcEvents.P_COUNT to selected.size, DcEvents.P_SHORTAGE to challenge.shortage,
                ),
            )
            resolve(challenge)
        }
    }

    private fun pickWithSubSections(
        section: String, count: Int, pool: List<QuestionCandidateRow>,
        subExpected: MutableMap<String, Double>, subActual: MutableMap<String, Double>,
        usedTopics: MutableSet<String>, usedStems: MutableSet<String>, rng: Random,
    ): List<QuestionCandidateRow> {
        val exp = HashMap<String, Double>(); val act = HashMap<String, Double>()
        DailyChallengeBlueprint.SUBSECTIONS[section]!!.keys.forEach { sub ->
            val k = "$section/$sub"; exp[sub] = subExpected[k] ?: 0.0; act[sub] = subActual[k] ?: 0.0
        }
        val subAlloc = DailyChallengeBlueprint.allocateSubSections(section, count, exp, act)
        val picked = ArrayList<QuestionCandidateRow>()
        for ((sub, k) in subAlloc) {
            val subPool = pool.filter { it.skill == sub && picked.none { p -> p.id == it.id } }
            picked += DailyChallengeSelection.pickN(subPool, k, usedTopics, usedStems, rng)
        }
        // fill any sub shortage from the rest of the section pool
        val short = count - picked.size
        if (short > 0) picked += DailyChallengeSelection.pickN(pool.filter { c -> picked.none { it.id == c.id } }, short, usedTopics, usedStems, rng)
        DailyChallengeBlueprint.SUBSECTIONS[section]!!.keys.forEach { sub ->
            subExpected["$section/$sub"] = exp[sub] ?: 0.0; subActual["$section/$sub"] = act[sub] ?: 0.0
        }
        return picked
    }

    /**
     * Record an answer against today's ONE challenge; on the 5th answer, complete it and update the
     * streak. Looks the challenge up by (user, day) only — so it targets the correct challenge even if
     * the active exam changed since it was generated. Never creates a challenge.
     */
    suspend fun submitAnswer(
        userId: String, localDate: String,
        questionId: String, chosenIndex: Int, isCorrect: Boolean, timeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Result? = withContext(Dispatchers.IO) {
        val challenge = dao.getChallengeForDay(userId, localDate) ?: return@withContext null
        val ck = key(userId, localDate)
        dao.upsertAnswer(ChallengeAnswerEntity(ck, questionId, userId, chosenIndex, isCorrect, timeMs, nowMs))
        // update learning state — the question's exam comes from its own retired state / the challenge,
        // NEVER from a (possibly since-switched) active exam, so review scoping stays correct.
        val prev = dao.getState(userId, questionId)
        val questionExam = prev?.examType ?: challenge.examProfile

        // A question is an INJECTED SCHEDULED REVIEW iff its PRE-ANSWER persisted state is
        // NEEDS_REVISION. That fact lives in Room, so routing survives Activity recreation and process
        // death with no extra column and no migration. Such an answer runs the spaced-repetition
        // contract instead of the plain new-question path: correct advances the ladder, incorrect sends
        // it back to the active wrong pool with the streak reset (ReviewScheduler.onReview). Everything
        // after this block — answer count, completion, streak, stats — is deliberately shared.
        val isInjectedReview = prev != null && prev.state == QuestionLearnState.NEEDS_REVISION.name
        if (isInjectedReview) {
            val outcome = ReviewScheduler.onReview(
                QuestionLearnState.NEEDS_REVISION, prev!!.consecutiveCorrect, isCorrect, nowMs,
            )
            dao.upsertState(
                prev.copy(
                    state = outcome.state.name,
                    consecutiveCorrect = outcome.consecutiveCorrect,
                    timesSeen = prev.timesSeen + 1,
                    timesCorrect = prev.timesCorrect + if (isCorrect) 1 else 0,
                    timesIncorrect = prev.timesIncorrect + if (isCorrect) 0 else 1,
                    lastSeenAt = nowMs,
                    nextReviewAt = outcome.nextReviewAtMs,
                    masteredAt = if (outcome.masteredAtMs != 0L) outcome.masteredAtMs else prev.masteredAt,
                )
            )
        } else {
        val newState = when {
            isCorrect -> QuestionLearnState.CORRECT
            prev == null || prev.state == QuestionLearnState.SEEN_ONCE.name -> QuestionLearnState.INCORRECT_ONCE
            else -> QuestionLearnState.INCORRECT_MULTIPLE
        }
        // Section/topic used to come from the SEEN_ONCE row written at generation time. Now that a
        // question is only recorded when it is answered, resolve them from the question itself on the
        // first answer so review grouping and section labels stay correct.
        val meta = if (prev == null) content.getQuestionsByIds(listOf(questionId)).firstOrNull() else null
        val section = prev?.section ?: meta?.subject ?: ""
        val topic = prev?.topic ?: meta?.topic ?: ""
        dao.upsertState(
            UserQuestionStateEntity(
                userId = userId, questionId = questionId, examType = questionExam, section = section, topic = topic,
                state = newState.name,
                timesSeen = (prev?.timesSeen ?: 1),
                timesCorrect = (prev?.timesCorrect ?: 0) + (if (isCorrect) 1 else 0),
                timesIncorrect = (prev?.timesIncorrect ?: 0) + (if (isCorrect) 0 else 1),
                firstSeenAt = prev?.firstSeenAt ?: nowMs, lastSeenAt = nowMs,
            )
        )
        }
        analytics.track(DcEvents.QUESTION_ANSWERED, mapOf(DcEvents.P_EXAM to questionExam, DcEvents.P_IS_CORRECT to isCorrect))
        val answered = dao.countAnswers(ck)
        if (challenge.startedAt == 0L) {
            analytics.track(DcEvents.CHALLENGE_STARTED, mapOf(DcEvents.P_EXAM to challenge.examProfile, DcEvents.P_LOCAL_DATE to localDate))
        }
        // currentIndex mirrors the durable answered-count (the resume cursor) and is persisted every step.
        var updated = challenge.copy(
            status = ChallengeStatus.IN_PROGRESS.name,
            startedAt = if (challenge.startedAt == 0L) nowMs else challenge.startedAt,
            currentIndex = answered,
        )
        var justCompleted = false
        if (answered >= DailyChallengeBlueprint.CHALLENGE_SIZE && challenge.status != ChallengeStatus.COMPLETED.name) {
            val score = dao.getAnswers(ck).count { it.isCorrect }
            updated = updated.copy(status = ChallengeStatus.COMPLETED.name, completedAt = nowMs, score = score)
            updateStreak(userId, localDate, challenge.examProfile)
            // Suppress any remaining local reminders for today (tomorrow's slots stay intact).
            onChallengeCompleted(localDate)
            analytics.track(
                DcEvents.CHALLENGE_COMPLETED,
                mapOf(DcEvents.P_EXAM to challenge.examProfile, DcEvents.P_LOCAL_DATE to localDate, DcEvents.P_SCORE to score),
            )
            justCompleted = true
        }
        dao.upsertChallenge(updated)
        // Order matters: the final answer is already persisted and the challenge is now durably
        // COMPLETED, so statistics are written last. This is the AUTHORITATIVE stats write — it is a
        // domain transition, not a screen, so it happens even if the result screen never launches
        // (process death after the fifth answer, failed navigation, user backgrounding the app).
        // recordCompletionStats never throws: a failed write must not roll back real progress, and the
        // repair path in getOrCreateToday retries it idempotently.
        if (justCompleted) recordCompletionStats(userId, localDate)
        resolve(updated)
    }

    /**
     * Idempotent statistics write for a completed challenge. Safe to call repeatedly: the recorder
     * refuses a challenge that is already stored (keyed by exam + local date) and refuses one that is
     * not genuinely finished, so retries repair a missing write without ever double-counting.
     */
    private suspend fun recordCompletionStats(userId: String, localDate: String) {
        try {
            val completion = getCompletion(userId, localDate) ?: return
            statsSink.recordIfAbsent(completion, System.currentTimeMillis())
        } catch (_: Throwable) {
            // Never surface a bookkeeping failure to the user; the next load retries.
        }
    }

    private suspend fun updateStreak(userId: String, localDate: String, examProfile: String) {
        val prev = dao.getStreak(userId) ?: StreakEntity(userId)
        val r = DailyChallengeStreak.onComplete(
            prevCurrent = prev.current, prevLongest = prev.longest,
            prevLastDate = prev.lastCompletedLocalDate, prevMilestonesCsv = prev.milestonesCsv,
            todayLocalDate = localDate,
        )
        if (r.incremented) {
            analytics.track(DcEvents.STREAK_INCREMENTED, mapOf(DcEvents.P_EXAM to examProfile, DcEvents.P_STREAK to r.current))
        }
        for (m in r.newMilestones) {
            analytics.track(DcEvents.MILESTONE_REACHED, mapOf(DcEvents.P_EXAM to examProfile, DcEvents.P_STREAK to m))
        }
        dao.upsertStreak(
            prev.copy(
                current = r.current, longest = r.longest,
                lastCompletedLocalDate = r.lastCompletedLocalDate, milestonesCsv = r.milestonesCsv,
            )
        )
    }

    /** Current streak length (0 if none). */
    suspend fun streak(userId: String): Int = withContext(Dispatchers.IO) {
        dao.getStreak(userId)?.current ?: 0
    }

    /** One answered question, for the completion breakdown. */
    data class AnswerReview(
        val questionId: String,
        val section: String,
        val stem: String,
        val chosenIndex: Int,
        val correctIndex: Int,
        val isCorrect: Boolean,
        val explanation: String,
    )

    /** Everything the completion screen needs — computed from persisted answers (restart-safe). */
    data class Completion(
        val localDate: String,
        val examProfile: String, // the exam this challenge belongs to (for correctly-scoped review)
        val score: Int,
        val total: Int,
        val nextUnlockAtMs: Long,
        val streakCurrent: Int,
        val reviews: List<AnswerReview>,
        val sectionCorrect: LinkedHashMap<String, Int>,
        val sectionTotal: LinkedHashMap<String, Int>,
    )

    /** Builds the completion summary for today's (completed or in-progress) challenge; null if none. */
    suspend fun getCompletion(userId: String, localDate: String): Completion? = withContext(Dispatchers.IO) {
        val challenge = dao.getChallengeForDay(userId, localDate) ?: return@withContext null
        val ck = key(userId, localDate)
        val ids = challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        val order = ids.withIndex().associate { (i, id) -> id to i }
        val byId = content.getQuestionsByIds(ids).associateBy { it.id }
        val answers = dao.getAnswers(ck).sortedBy { order[it.questionId] ?: Int.MAX_VALUE }
        val reviews = ArrayList<AnswerReview>()
        val sectionCorrect = LinkedHashMap<String, Int>()
        val sectionTotal = LinkedHashMap<String, Int>()
        for (a in answers) {
            val q = byId[a.questionId] ?: continue
            val section = q.subject ?: ""
            sectionTotal[section] = (sectionTotal[section] ?: 0) + 1
            sectionCorrect[section] = (sectionCorrect[section] ?: 0) + if (a.isCorrect) 1 else 0
            reviews += AnswerReview(
                questionId = a.questionId, section = section, stem = q.questionText,
                chosenIndex = a.chosenIndex, correctIndex = q.answerIndex, isCorrect = a.isCorrect,
                explanation = q.explanation ?: "",
            )
        }
        Completion(
            localDate = localDate,
            examProfile = challenge.examProfile,
            score = answers.count { it.isCorrect },
            total = DailyChallengeBlueprint.CHALLENGE_SIZE,
            nextUnlockAtMs = challenge.expiresAt,
            streakCurrent = dao.getStreak(userId)?.current ?: 0,
            reviews = reviews, sectionCorrect = sectionCorrect, sectionTotal = sectionTotal,
        )
    }

    /** Count of questions currently available for review in the given queues. */
    suspend fun reviewCount(userId: String, exam: ExamType, queues: List<ReviewQueue>): Int = withContext(Dispatchers.IO) {
        val states = queues.flatMap {
            when (it) {
                ReviewQueue.INCORRECT -> listOf(QuestionLearnState.INCORRECT_ONCE.name, QuestionLearnState.INCORRECT_MULTIPLE.name)
                ReviewQueue.NEEDS_REVISION -> listOf(QuestionLearnState.NEEDS_REVISION.name)
                ReviewQueue.FORGOTTEN -> listOf(QuestionLearnState.FORGOTTEN.name)
            }
        }.distinct()
        dao.countStatesByStates(userId, exam.name, states)
    }

    /**
     * Portable snapshot of an account's ENTIRE Daily Challenge state — every challenge + answers, plus
     * the retirement/review states, the deficit ledger, and the streak. This is the unit a backend syncs
     * so a reinstall restores not just today's challenge but also question retirement (so served questions
     * never recur) and the review queue.
     */
    suspend fun exportSnapshot(userId: String): DailyChallengeSnapshot = withContext(Dispatchers.IO) {
        DailyChallengeSnapshot(
            challenges = dao.getAllChallengesForUser(userId),
            answers = dao.getAllAnswersForUser(userId),
            states = dao.getAllStatesForUser(userId),
            deficits = dao.getAllDeficitsForUser(userId),
            streak = dao.getStreak(userId),
        )
    }

    /**
     * Restore a synced snapshot into local storage (e.g. after reinstall, once sync has pulled it).
     * Purely additive/idempotent: it writes back the SAME challenges + answers + retirement/review/
     * deficit/streak state, so subsequent [getOrCreateToday] calls return the stored challenge and never
     * regenerate, AND future days do not re-serve already-retired questions.
     */
    suspend fun restoreSnapshot(snapshot: DailyChallengeSnapshot): Unit = withContext(Dispatchers.IO) {
        // Write the challenge row LAST: presence of a day's challenge is the "this day is fully restored"
        // sentinel that [migrateAccount] keys off, so a crash before this point simply re-adopts on retry.
        for (a in snapshot.answers) dao.upsertAnswer(a)
        for (s in snapshot.states) dao.upsertState(s)
        for (d in snapshot.deficits) dao.upsertDeficit(d)
        snapshot.streak?.let { dao.upsertStreak(it) }
        for (c in snapshot.challenges) dao.upsertChallenge(c)
    }

    /**
     * Link an anonymous session's Daily Challenge state to a real account on sign-in, so signing in never
     * regenerates the day's challenge or loses its retirement history.
     *
     * This is a MERGE that only ever ADDS what the target account doesn't already own — it never clobbers
     * an existing account's data:
     *  - a challenge is adopted only for days the account has NO challenge (so a returning account keeps
     *    its history AND doesn't regenerate today — it adopts today's anon challenge instead),
     *  - a retirement/review state is adopted only for questions the account hasn't seen,
     *  - streak/deficit ledgers are adopted only when the account has none.
     *
     * Because every decision is recomputed from the target's CURRENT state, the operation is idempotent
     * and self-healing: a re-run after a crash mid-migration simply adopts whatever is still missing (no
     * partial-write hole). Returns true iff anything was adopted.
     */
    suspend fun migrateAccount(fromUserId: String, toUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (fromUserId.isBlank() || toUserId.isBlank() || fromUserId == toUserId) return@withContext false
        val source = exportSnapshot(fromUserId)
        if (source.challenges.isEmpty() && source.states.isEmpty()) return@withContext false

        val targetDays = dao.getAllChallengesForUser(toUserId).mapTo(HashSet()) { it.localDate }
        val challengesToAdopt = source.challenges.filter { it.localDate !in targetDays }
        val adoptDays = challengesToAdopt.mapTo(HashSet()) { it.localDate }
        val answersToAdopt = source.answers.filter { it.challengeKey.removePrefix("${it.userId}:") in adoptDays }
        val statesToAdopt = source.states.filter { dao.getState(toUserId, it.questionId) == null }
        val streakToAdopt = if (dao.getStreak(toUserId) == null) source.streak else null
        val deficitsToAdopt = if (dao.getAllDeficitsForUser(toUserId).isEmpty()) source.deficits else emptyList()

        if (challengesToAdopt.isEmpty() && statesToAdopt.isEmpty() && streakToAdopt == null && deficitsToAdopt.isEmpty()) {
            return@withContext false
        }
        restoreSnapshot(
            DailyChallengeSnapshot(challengesToAdopt, answersToAdopt, statesToAdopt, deficitsToAdopt, streakToAdopt)
                .rekeyedTo(toUserId)
        )
        true
    }

    private suspend fun resolve(challenge: DailyChallengeEntity): Result {
        val ids = challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        val byId = content.getQuestionsByIds(ids).associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        val answered = dao.countAnswers(key(challenge.userId, challenge.localDate))
        return Result(challenge, ordered, answered, DailyChallengeBlueprint.CHALLENGE_SIZE, challenge.status == ChallengeStatus.COMPLETED.name)
    }
}
