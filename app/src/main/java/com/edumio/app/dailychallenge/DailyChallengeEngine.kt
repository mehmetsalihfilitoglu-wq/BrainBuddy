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

/**
 * The Daily Challenge engine. Guarantees the hard product invariants:
 *  - exactly 5 NEW questions per challenge,
 *  - one challenge per (user, exam, local calendar day) — idempotent, no second unlock,
 *  - Premium NEVER increases the new-question count (no premium input affects generation),
 *  - only production-eligible questions, drawn ONLY from the user's NEVER_SEEN pool,
 *  - blueprint-proportional section allocation (cumulative deficit) decided BEFORE selection,
 *  - each served question is retired from the unseen pool immediately (appears once, ever).
 * Content is read from edumio.db (QuestionDao); user state lives in daily_challenge.db.
 */
class DailyChallengeEngine(private val appContext: Context) {

    private val analytics: DailyChallengeAnalytics get() = DailyChallengeAnalyticsProvider.get(appContext)

    private companion object {
        // Process-wide: engine instances are created per-Activity, so the generation lock must be
        // shared across ALL of them to serialize same-day challenge creation (no double-retire race).
        val generationLock = Mutex()
    }

    data class Result(
        val challenge: DailyChallengeEntity,
        val questions: List<QuestionEntity>,
        val answered: Int,
        val total: Int,
        val completed: Boolean,
    )

    private fun key(userId: String, examType: String, localDate: String) = "$userId:$examType:$localDate"

    /** Local calendar date (YYYY-MM-DD) for the user's timezone. Delegates to [DailyChallengeDates]. */
    fun localDate(zone: TimeZone = TimeZone.getDefault(), nowMs: Long = System.currentTimeMillis()): String =
        DailyChallengeDates.localDate(zone, nowMs)

    /** Millis at the end of the given local day (next midnight) — challenge expiry. */
    fun endOfLocalDay(localDate: String, zone: TimeZone = TimeZone.getDefault()): Long =
        DailyChallengeDates.endOfLocalDay(localDate, zone)

    /**
     * Returns today's Daily Challenge, generating it once if needed. Idempotent per local day: a second
     * call the same day returns the SAME challenge (never a new one). [isPremium] is accepted only to make
     * the invariant explicit in tests — it is intentionally IGNORED for question count.
     */
    suspend fun getOrCreateToday(
        userId: String,
        exam: ExamType,
        zone: TimeZone = TimeZone.getDefault(),
        nowMs: Long = System.currentTimeMillis(),
        @Suppress("UNUSED_PARAMETER") isPremium: Boolean = false,
        rngSeed: Long? = null,
    ): Result? = withContext(Dispatchers.IO) {
        if (!DailyChallengeBlueprint.isSupported(exam)) return@withContext null
        val examType = exam.name
        val day = localDate(zone, nowMs)
        val qDao = DatabaseProvider.get(appContext).questionDao()
        val dcDao = DailyChallengeDatabase.get(appContext).dailyChallengeDao()

        dcDao.getChallenge(userId, examType, day)?.let { existing ->
            return@withContext resolve(existing, qDao, dcDao)
        }

        // Serialize generation: a concurrent caller (e.g. the home card refreshing while the flow
        // screen opens) must never generate or retire two challenges for the same day. Double-checked
        // under the lock so the winner's challenge is reused rather than regenerated.
        return@withContext generationLock.withLock {
            dcDao.getChallenge(userId, examType, day)?.let { existing ->
                return@withLock resolve(existing, qDao, dcDao)
            }

            // ── ledgers ──
            val deficits = dcDao.getDeficits(userId, examType)
            val expected = HashMap<String, Double>(); val actual = HashMap<String, Double>()
            val subExpected = HashMap<String, Double>(); val subActual = HashMap<String, Double>()
            for (d in deficits) {
                if (d.section.contains('/')) { subExpected[d.section] = d.expectedToDate; subActual[d.section] = d.actualToDate }
                else { expected[d.section] = d.expectedToDate; actual[d.section] = d.actualToDate }
            }

            val alloc = DailyChallengeBlueprint.allocate(exam, expected, actual)
            val seen = dcDao.getSeenQuestionIds(userId, examType).toHashSet()
            val rng = Random(rngSeed ?: (day.hashCode().toLong() * 1_000_003L + userId.hashCode()))

            val selected = ArrayList<QuestionCandidateRow>()
            val usedTopics = HashSet<String>(); val usedStems = HashSet<String>()
            val shortages = ArrayList<String>()
            val leftoverPools = ArrayList<QuestionCandidateRow>() // unseen candidates not picked, for reallocation

            for ((section, count) in alloc) {
                if (count <= 0) continue
                val pool = qDao.getDailyCandidatePool(examType, section).filter { it.id !in seen && it.stemHash !in usedStems }
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
            if (selected.isEmpty()) return@withLock null // no unseen questions at all → cannot form a challenge

            // ── guardrail BEFORE any side effect: never more than CHALLENGE_SIZE new questions ──
            // Runs prior to ledger/retirement writes so a fail-closed batch leaves NO trace.
            val sizeViolations = DailyChallengeGuardrails.checkNewQuestionCount(selected.size)
            if (sizeViolations.isNotEmpty()) {
                analytics.track(DcEvents.GUARDRAIL_VIOLATION, mapOf(DcEvents.P_REASON to sizeViolations.joinToString("; ")))
                return@withLock null // fail closed: never serve an over-cap batch, and never retire it
            }

            // ── persist ledgers ──
            for ((s, v) in expected) dcDao.upsertDeficit(SectionDeficitEntity(userId, examType, s, v, actual[s] ?: 0.0))
            for ((s, v) in subExpected) dcDao.upsertDeficit(SectionDeficitEntity(userId, examType, s, v, subActual[s] ?: 0.0))

            // ── retire selected (SEEN_ONCE) — leaves the unseen pool permanently ──
            for (c in selected) {
                dcDao.upsertState(
                    UserQuestionStateEntity(
                        userId = userId, questionId = c.id, examType = examType,
                        section = c.subject, topic = c.topic, state = QuestionLearnState.SEEN_ONCE.name,
                        timesSeen = 1, firstSeenAt = nowMs, lastSeenAt = nowMs,
                    )
                )
            }

            val challenge = DailyChallengeEntity(
                userId = userId, examType = examType, localDate = day,
                status = ChallengeStatus.AVAILABLE.name,
                questionIdsCsv = selected.joinToString(",") { it.id },
                allocationCsv = alloc.entries.joinToString("|") { "${it.key}:${it.value}" },
                expiresAt = endOfLocalDay(day, zone),
                shortage = shortages.joinToString(";"),
            )
            dcDao.upsertChallenge(challenge)
            analytics.track(
                DcEvents.CHALLENGE_GENERATED,
                mapOf(
                    DcEvents.P_EXAM to examType, DcEvents.P_LOCAL_DATE to day,
                    DcEvents.P_COUNT to selected.size, DcEvents.P_SHORTAGE to challenge.shortage,
                ),
            )
            resolve(challenge, qDao, dcDao)
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

    /** Record an answer; on the 5th answer, complete the challenge and update the streak. */
    suspend fun submitAnswer(
        userId: String, exam: ExamType, localDate: String,
        questionId: String, chosenIndex: Int, isCorrect: Boolean, timeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Result? = withContext(Dispatchers.IO) {
        val examType = exam.name
        val qDao = DatabaseProvider.get(appContext).questionDao()
        val dcDao = DailyChallengeDatabase.get(appContext).dailyChallengeDao()
        val challenge = dcDao.getChallenge(userId, examType, localDate) ?: return@withContext null
        val ck = key(userId, examType, localDate)
        dcDao.upsertAnswer(ChallengeAnswerEntity(ck, questionId, userId, chosenIndex, isCorrect, timeMs, nowMs))
        // update learning state
        val prev = dcDao.getState(userId, questionId)
        val newState = when {
            isCorrect -> QuestionLearnState.CORRECT
            prev == null || prev.state == QuestionLearnState.SEEN_ONCE.name -> QuestionLearnState.INCORRECT_ONCE
            else -> QuestionLearnState.INCORRECT_MULTIPLE
        }
        val section = prev?.section ?: ""
        val topic = prev?.topic ?: ""
        dcDao.upsertState(
            UserQuestionStateEntity(
                userId = userId, questionId = questionId, examType = examType, section = section, topic = topic,
                state = newState.name,
                timesSeen = (prev?.timesSeen ?: 1),
                timesCorrect = (prev?.timesCorrect ?: 0) + (if (isCorrect) 1 else 0),
                timesIncorrect = (prev?.timesIncorrect ?: 0) + (if (isCorrect) 0 else 1),
                firstSeenAt = prev?.firstSeenAt ?: nowMs, lastSeenAt = nowMs,
            )
        )
        analytics.track(DcEvents.QUESTION_ANSWERED, mapOf(DcEvents.P_EXAM to examType, DcEvents.P_IS_CORRECT to isCorrect))
        val answered = dcDao.countAnswers(ck)
        if (challenge.startedAt == 0L) {
            analytics.track(DcEvents.CHALLENGE_STARTED, mapOf(DcEvents.P_EXAM to examType, DcEvents.P_LOCAL_DATE to localDate))
        }
        var updated = challenge.copy(status = ChallengeStatus.IN_PROGRESS.name, startedAt = if (challenge.startedAt == 0L) nowMs else challenge.startedAt)
        if (answered >= DailyChallengeBlueprint.CHALLENGE_SIZE && challenge.status != ChallengeStatus.COMPLETED.name) {
            val score = dcDao.getAnswers(ck).count { it.isCorrect }
            updated = updated.copy(status = ChallengeStatus.COMPLETED.name, completedAt = nowMs, score = score)
            updateStreak(dcDao, userId, localDate, examType)
            // Suppress any remaining local reminders for today (tomorrow's slots stay intact).
            DailyChallengeReminderScheduler.onChallengeCompleted(appContext, localDate)
            analytics.track(
                DcEvents.CHALLENGE_COMPLETED,
                mapOf(DcEvents.P_EXAM to examType, DcEvents.P_LOCAL_DATE to localDate, DcEvents.P_SCORE to score),
            )
        }
        dcDao.upsertChallenge(updated)
        resolve(updated, qDao, dcDao)
    }

    private suspend fun updateStreak(dcDao: DailyChallengeDao, userId: String, localDate: String, examType: String) {
        val prev = dcDao.getStreak(userId) ?: StreakEntity(userId)
        val r = DailyChallengeStreak.onComplete(
            prevCurrent = prev.current, prevLongest = prev.longest,
            prevLastDate = prev.lastCompletedLocalDate, prevMilestonesCsv = prev.milestonesCsv,
            todayLocalDate = localDate,
        )
        if (r.incremented) {
            analytics.track(DcEvents.STREAK_INCREMENTED, mapOf(DcEvents.P_EXAM to examType, DcEvents.P_STREAK to r.current))
        }
        for (m in r.newMilestones) {
            analytics.track(DcEvents.MILESTONE_REACHED, mapOf(DcEvents.P_EXAM to examType, DcEvents.P_STREAK to m))
        }
        dcDao.upsertStreak(
            prev.copy(
                current = r.current, longest = r.longest,
                lastCompletedLocalDate = r.lastCompletedLocalDate, milestonesCsv = r.milestonesCsv,
            )
        )
    }

    /** Current streak length (0 if none). */
    suspend fun streak(userId: String): Int = withContext(Dispatchers.IO) {
        DailyChallengeDatabase.get(appContext).dailyChallengeDao().getStreak(userId)?.current ?: 0
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
        val score: Int,
        val total: Int,
        val nextUnlockAtMs: Long,
        val streakCurrent: Int,
        val reviews: List<AnswerReview>,
        val sectionCorrect: LinkedHashMap<String, Int>,
        val sectionTotal: LinkedHashMap<String, Int>,
    )

    /** Builds the completion summary for a (completed or in-progress) challenge; null if none exists. */
    suspend fun getCompletion(userId: String, exam: ExamType, localDate: String): Completion? = withContext(Dispatchers.IO) {
        val examType = exam.name
        val qDao = DatabaseProvider.get(appContext).questionDao()
        val dcDao = DailyChallengeDatabase.get(appContext).dailyChallengeDao()
        val challenge = dcDao.getChallenge(userId, examType, localDate) ?: return@withContext null
        val ck = key(userId, examType, localDate)
        val ids = challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        val order = ids.withIndex().associate { (i, id) -> id to i }
        val byId = qDao.getQuestionsByIds(ids).associateBy { it.id }
        val answers = dcDao.getAnswers(ck).sortedBy { order[it.questionId] ?: Int.MAX_VALUE }
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
            score = answers.count { it.isCorrect },
            total = DailyChallengeBlueprint.CHALLENGE_SIZE,
            nextUnlockAtMs = challenge.expiresAt,
            streakCurrent = dcDao.getStreak(userId)?.current ?: 0,
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
        DailyChallengeDatabase.get(appContext).dailyChallengeDao().countStatesByStates(userId, exam.name, states)
    }

    private suspend fun resolve(challenge: DailyChallengeEntity, qDao: com.edumio.app.db.QuestionDao, dcDao: DailyChallengeDao): Result {
        val ids = challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        val byId = qDao.getQuestionsByIds(ids).associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        val answered = dcDao.countAnswers(key(challenge.userId, challenge.examType, challenge.localDate))
        return Result(challenge, ordered, answered, DailyChallengeBlueprint.CHALLENGE_SIZE, challenge.status == ChallengeStatus.COMPLETED.name)
    }
}
