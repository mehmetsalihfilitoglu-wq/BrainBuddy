package com.mioacademy.app.dailychallenge

import android.content.Context
import com.mioacademy.app.core.ExamType
import com.mioacademy.app.db.DatabaseProvider
import com.mioacademy.app.db.QuestionCandidateRow
import com.mioacademy.app.db.QuestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
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
 * Content is read from brainbuddy.db (QuestionDao); user state lives in daily_challenge.db.
 */
class DailyChallengeEngine(private val appContext: Context) {

    private val analytics: DailyChallengeAnalytics get() = DailyChallengeAnalyticsProvider.get(appContext)

    data class Result(
        val challenge: DailyChallengeEntity,
        val questions: List<QuestionEntity>,
        val answered: Int,
        val total: Int,
        val completed: Boolean,
    )

    private fun key(userId: String, examType: String, localDate: String) = "$userId:$examType:$localDate"

    /** Local calendar date (YYYY-MM-DD) for the user's timezone. Calendar-based (safe on minSdk 24). */
    fun localDate(zone: TimeZone = TimeZone.getDefault(), nowMs: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance(zone); c.timeInMillis = nowMs
        return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** Millis at the end of the given local day (next midnight) — challenge expiry. */
    fun endOfLocalDay(localDate: String, zone: TimeZone = TimeZone.getDefault()): Long {
        val p = localDate.split("-").map { it.toInt() }
        val c = Calendar.getInstance(zone); c.clear(); c.set(p[0], p[1] - 1, p[2], 0, 0, 0); c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** Shift a YYYY-MM-DD date by [days] (calendar-safe, timezone-neutral). */
    private fun shiftDate(localDate: String, days: Int): String {
        val p = localDate.split("-").map { it.toInt() }
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.clear(); c.set(p[0], p[1] - 1, p[2]); c.add(Calendar.DAY_OF_MONTH, days)
        return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

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
        if (selected.isEmpty()) return@withContext null // no unseen questions at all → cannot form a challenge

        // ── persist ledgers ──
        for ((s, v) in expected) dcDao.upsertDeficit(SectionDeficitEntity(userId, examType, s, v, actual[s] ?: 0.0))
        for ((s, v) in subExpected) dcDao.upsertDeficit(SectionDeficitEntity(userId, examType, s, v, subActual[s] ?: 0.0))

        // ── retire selected (SEEN_ONCE) — leaves the unseen pool permanently ──
        val nowStamp = nowMs
        for (c in selected) {
            dcDao.upsertState(
                UserQuestionStateEntity(
                    userId = userId, questionId = c.id, examType = examType,
                    section = c.subject, topic = c.topic, state = QuestionLearnState.SEEN_ONCE.name,
                    timesSeen = 1, firstSeenAt = nowStamp, lastSeenAt = nowStamp,
                )
            )
        }

        // ── guardrail: never more than CHALLENGE_SIZE new questions ──
        val sizeViolations = DailyChallengeGuardrails.checkNewQuestionCount(selected.size)
        if (sizeViolations.isNotEmpty()) {
            analytics.track(DcEvents.GUARDRAIL_VIOLATION, mapOf(DcEvents.P_REASON to sizeViolations.joinToString("; ")))
            // fail closed: never serve an over-cap batch
            return@withContext null
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
        val yesterday = shiftDate(localDate, -1)
        val newCurrent = when (prev.lastCompletedLocalDate) {
            localDate -> prev.current // already counted today
            yesterday -> prev.current + 1
            else -> 1
        }
        if (prev.lastCompletedLocalDate != localDate) {
            analytics.track(DcEvents.STREAK_INCREMENTED, mapOf(DcEvents.P_EXAM to examType, DcEvents.P_STREAK to newCurrent))
        }
        val milestones = setOf(3, 7, 14, 30, 50, 100, 180, 365)
        val reached = prev.milestonesCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        if (newCurrent in milestones && newCurrent.toString() !in reached) {
            reached += newCurrent.toString()
            analytics.track(DcEvents.MILESTONE_REACHED, mapOf(DcEvents.P_EXAM to examType, DcEvents.P_STREAK to newCurrent))
        }
        dcDao.upsertStreak(
            prev.copy(
                current = newCurrent, longest = maxOf(prev.longest, newCurrent),
                lastCompletedLocalDate = localDate, milestonesCsv = reached.joinToString(","),
            )
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

    private suspend fun resolve(challenge: DailyChallengeEntity, qDao: com.mioacademy.app.db.QuestionDao, dcDao: DailyChallengeDao): Result {
        val ids = challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        val byId = qDao.getQuestionsByIds(ids).associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        val answered = dcDao.countAnswers(key(challenge.userId, challenge.examType, challenge.localDate))
        return Result(challenge, ordered, answered, DailyChallengeBlueprint.CHALLENGE_SIZE, challenge.status == ChallengeStatus.COMPLETED.name)
    }
}
