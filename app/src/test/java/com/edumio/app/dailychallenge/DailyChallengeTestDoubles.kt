package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import com.edumio.app.db.QuestionCandidateRow
import com.edumio.app.db.QuestionEntity

/**
 * Pure-JVM test doubles for the Daily Challenge engine. No Room, no Android — the engine's real
 * orchestration runs against an in-memory [DailyChallengeDao] and a fake content source, so every
 * immutability / no-regeneration invariant is provable deterministically.
 */

/** In-memory [DailyChallengeDao] mirroring Room's REPLACE-on-conflict upsert semantics. Thread-safe. */
class InMemoryDailyChallengeDao : DailyChallengeDao {
    private val lock = Any()
    val challenges = LinkedHashMap<String, DailyChallengeEntity>()  // "userId|localDate"
    val answers = LinkedHashMap<String, ChallengeAnswerEntity>()     // "challengeKey|questionId"
    val states = LinkedHashMap<String, UserQuestionStateEntity>()    // "userId|questionId"
    val deficits = LinkedHashMap<String, SectionDeficitEntity>()     // "userId|examType|section"
    val streaks = LinkedHashMap<String, StreakEntity>()              // "userId"

    private fun dayKey(u: String, d: String) = "$u|$d"

    override suspend fun upsertChallenge(c: DailyChallengeEntity) = synchronized(lock) {
        challenges[dayKey(c.userId, c.localDate)] = c
    }

    override suspend fun getChallengeForDay(userId: String, localDate: String): DailyChallengeEntity? =
        synchronized(lock) { challenges[dayKey(userId, localDate)] }

    override suspend fun countChallengesForDay(userId: String, localDate: String): Int =
        synchronized(lock) { if (challenges.containsKey(dayKey(userId, localDate))) 1 else 0 }

    override suspend fun countChallengesForUser(userId: String): Int =
        synchronized(lock) { challenges.values.count { it.userId == userId } }

    override suspend fun getAllChallengesForUser(userId: String): List<DailyChallengeEntity> =
        synchronized(lock) { challenges.values.filter { it.userId == userId } }

    override suspend fun getAllAnswersForUser(userId: String): List<ChallengeAnswerEntity> =
        synchronized(lock) { answers.values.filter { it.userId == userId } }

    override suspend fun getAllStatesForUser(userId: String): List<UserQuestionStateEntity> =
        synchronized(lock) { states.values.filter { it.userId == userId } }

    override suspend fun getAllDeficitsForUser(userId: String): List<SectionDeficitEntity> =
        synchronized(lock) { deficits.values.filter { it.userId == userId } }

    override suspend fun upsertAnswer(a: ChallengeAnswerEntity) = synchronized(lock) {
        answers["${a.challengeKey}|${a.questionId}"] = a
    }

    override suspend fun getAnswers(challengeKey: String): List<ChallengeAnswerEntity> =
        synchronized(lock) { answers.values.filter { it.challengeKey == challengeKey } }

    override suspend fun countAnswers(challengeKey: String): Int =
        synchronized(lock) { answers.values.count { it.challengeKey == challengeKey } }

    override suspend fun upsertState(s: UserQuestionStateEntity) = synchronized(lock) {
        states["${s.userId}|${s.questionId}"] = s
    }

    override suspend fun getState(userId: String, questionId: String): UserQuestionStateEntity? =
        synchronized(lock) { states["$userId|$questionId"] }

    override suspend fun getSeenQuestionIds(userId: String, examType: String): List<String> =
        synchronized(lock) { states.values.filter { it.userId == userId && it.examType == examType }.map { it.questionId } }

    override suspend fun countSeen(userId: String, examType: String): Int =
        synchronized(lock) { states.values.count { it.userId == userId && it.examType == examType } }

    override suspend fun getStatesByStates(userId: String, examType: String, states: List<String>): List<UserQuestionStateEntity> =
        synchronized(lock) { this.states.values.filter { it.userId == userId && it.examType == examType && it.state in states } }

    override suspend fun countStatesByStates(userId: String, examType: String, states: List<String>): Int =
        synchronized(lock) { this.states.values.count { it.userId == userId && it.examType == examType && it.state in states } }

    override suspend fun getStatesByStatesAllExams(userId: String, states: List<String>): List<UserQuestionStateEntity> =
        synchronized(lock) { this.states.values.filter { it.userId == userId && it.state in states } }

    override suspend fun getLatestAnswerForQuestion(userId: String, questionId: String): ChallengeAnswerEntity? =
        synchronized(lock) {
            answers.values.filter { it.userId == userId && it.questionId == questionId }.maxByOrNull { it.answeredAt }
        }

    override suspend fun upsertDeficit(d: SectionDeficitEntity) = synchronized(lock) {
        deficits["${d.userId}|${d.examType}|${d.section}"] = d
    }

    override suspend fun getDeficits(userId: String, examType: String): List<SectionDeficitEntity> =
        synchronized(lock) { deficits.values.filter { it.userId == userId && it.examType == examType } }

    override suspend fun upsertStreak(s: StreakEntity) = synchronized(lock) { streaks[s.userId] = s }

    override suspend fun getStreak(userId: String): StreakEntity? = synchronized(lock) { streaks[userId] }
}

/** Fake content source: fixed candidate pools per (exam, section) + matching question rows. */
class FakeDcContent(
    private val pools: Map<Pair<String, String>, List<QuestionCandidateRow>>,
    private val questions: Map<String, QuestionEntity>,
) : DcContentSource {
    override suspend fun getDailyCandidatePool(examType: String, section: String): List<QuestionCandidateRow> =
        pools[examType to section] ?: emptyList()

    override suspend fun getQuestionsByIds(ids: List<String>): List<QuestionEntity> =
        ids.mapNotNull { questions[it] }

    companion object {
        /**
         * Build a satisfiable content source for [exam] with [perSection] unique candidates per section
         * (unique id/topic/stemHash so selection is never starved). Uses the exam's blueprint sections.
         */
        fun forExam(exam: ExamType, perSection: Int = 12): FakeDcContent {
            val pools = HashMap<Pair<String, String>, List<QuestionCandidateRow>>()
            val questions = HashMap<String, QuestionEntity>()
            val sections = DailyChallengeBlueprint.sections(exam)
            // sub-section skills so bundled sections (TIL) can be filled by skill.
            for (section in sections) {
                val subs = DailyChallengeBlueprint.SUBSECTIONS[section]?.keys?.toList()
                val rows = ArrayList<QuestionCandidateRow>(perSection)
                for (i in 0 until perSection) {
                    val id = "${exam.name}-$section-$i"
                    val skill = subs?.get(i % subs.size) ?: section
                    rows += QuestionCandidateRow(
                        id = id, subject = section, difficulty = 2, grade = 13,
                        stemHash = "hash-$id", stemNormalized = "stem $id", type = "PROBLEM",
                        skill = skill, topic = "topic-$id", qualityTier = "ELITE", reasoningLevel = 2,
                    )
                    questions[id] = QuestionEntity(
                        id = id, grade = 13, subject = section, difficulty = 2,
                        questionText = "Q $id", optionsJson = """["A","B","C","D","E"]""", answerIndex = 0,
                        explanation = "because $id", examType = exam.name,
                    )
                }
                pools[exam.name to section] = rows
            }
            return FakeDcContent(pools, questions)
        }
    }
}

/** Records analytics events so tests can assert e.g. "generated exactly once". */
class RecordingAnalytics : DailyChallengeAnalytics {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun track(name: String, params: Map<String, Any?>) {
        synchronized(events) { events += name to params }
    }
    fun count(name: String): Int = synchronized(events) { events.count { it.first == name } }
}

/** Convenience factory for a fully-injected engine (no reminder side effects in tests). */
fun testEngine(
    dao: DailyChallengeDao,
    content: DcContentSource,
    analytics: DailyChallengeAnalytics = RecordingAnalytics(),
    onCompleted: (String) -> Unit = {},
): DailyChallengeEngine = DailyChallengeEngine(dao, content, analytics, onCompleted)
