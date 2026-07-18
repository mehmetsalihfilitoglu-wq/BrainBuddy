package com.edumio.app.dailychallenge

import org.json.JSONArray
import org.json.JSONObject

/**
 * A portable snapshot of an ACCOUNT's entire Daily Challenge state: every challenge + its answers, plus
 * the learning state that makes the model correct over time — question retirement (`user_question_state`,
 * so served questions never recur), the review queue, the cumulative-deficit ledger, and the streak.
 *
 * This is the unit a backend syncs so the model survives a reinstall (which wipes local storage). It is
 * also what links an anonymous session to an account on first sign-in (see [rekeyedTo]) so signing in
 * never regenerates the day's challenge. No live backend is provisioned yet; this seam is what a real one
 * plugs into, and it is exercised end-to-end by the reinstall-restore and account-link tests.
 */
data class DailyChallengeSnapshot(
    val challenges: List<DailyChallengeEntity>,
    val answers: List<ChallengeAnswerEntity>,
    val states: List<UserQuestionStateEntity>,
    val deficits: List<SectionDeficitEntity>,
    val streak: StreakEntity?,
) {
    /**
     * Re-key every row from its current user to [toUserId] (anonymous → account linking). Recomputes the
     * embedded ids: challengeId and challengeKey both become `"toUserId:localDate"`.
     */
    fun rekeyedTo(toUserId: String): DailyChallengeSnapshot = DailyChallengeSnapshot(
        challenges = challenges.map { it.copy(userId = toUserId, challengeId = "$toUserId:${it.localDate}") },
        answers = answers.map {
            val localDate = it.challengeKey.removePrefix("${it.userId}:")
            it.copy(userId = toUserId, challengeKey = "$toUserId:$localDate")
        },
        states = states.map { it.copy(userId = toUserId) },
        deficits = deficits.map { it.copy(userId = toUserId) },
        streak = streak?.copy(userId = toUserId),
    )
}

/** Deterministic JSON (de)serialization for [DailyChallengeSnapshot] — no third-party deps. */
object DailyChallengeSyncCodec {

    const val SCHEMA_VERSION = 3

    fun toJson(s: DailyChallengeSnapshot): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("challenges", JSONArray().apply { s.challenges.forEach { put(challengeToJson(it)) } })
        .put("answers", JSONArray().apply { s.answers.forEach { put(answerToJson(it)) } })
        .put("states", JSONArray().apply { s.states.forEach { put(stateToJson(it)) } })
        .put("deficits", JSONArray().apply { s.deficits.forEach { put(deficitToJson(it)) } })
        .apply { if (s.streak != null) put("streak", streakToJson(s.streak)) }
        .toString()

    fun fromJson(json: String): DailyChallengeSnapshot? = try {
        val root = JSONObject(json)
        DailyChallengeSnapshot(
            challenges = root.optJSONArray("challenges").mapObjects(::challengeFromJson),
            answers = root.optJSONArray("answers").mapObjects(::answerFromJson),
            states = root.optJSONArray("states").mapObjects(::stateFromJson),
            deficits = root.optJSONArray("deficits").mapObjects(::deficitFromJson),
            streak = root.optJSONObject("streak")?.let(::streakFromJson),
        )
    } catch (_: Throwable) {
        null
    }

    private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) out += f(getJSONObject(i))
        return out
    }

    private fun challengeToJson(c: DailyChallengeEntity) = JSONObject()
        .put("userId", c.userId).put("localDate", c.localDate).put("challengeId", c.challengeId)
        .put("examProfile", c.examProfile).put("status", c.status).put("questionIdsCsv", c.questionIdsCsv)
        .put("currentIndex", c.currentIndex).put("allocationCsv", c.allocationCsv).put("createdAt", c.createdAt)
        .put("startedAt", c.startedAt).put("completedAt", c.completedAt).put("expiresAt", c.expiresAt)
        .put("score", c.score).put("shortage", c.shortage)

    private fun challengeFromJson(o: JSONObject) = DailyChallengeEntity(
        userId = o.getString("userId"), localDate = o.getString("localDate"),
        challengeId = o.optString("challengeId", "${o.getString("userId")}:${o.getString("localDate")}"),
        examProfile = o.optString("examProfile", ""), status = o.getString("status"),
        questionIdsCsv = o.getString("questionIdsCsv"), currentIndex = o.optInt("currentIndex", 0),
        allocationCsv = o.optString("allocationCsv", ""), createdAt = o.optLong("createdAt", 0),
        startedAt = o.optLong("startedAt", 0), completedAt = o.optLong("completedAt", 0),
        expiresAt = o.optLong("expiresAt", 0), score = o.optInt("score", 0), shortage = o.optString("shortage", ""),
    )

    private fun answerToJson(a: ChallengeAnswerEntity) = JSONObject()
        .put("challengeKey", a.challengeKey).put("questionId", a.questionId).put("userId", a.userId)
        .put("chosenIndex", a.chosenIndex).put("isCorrect", a.isCorrect).put("timeMs", a.timeMs)
        .put("answeredAt", a.answeredAt)

    private fun answerFromJson(o: JSONObject) = ChallengeAnswerEntity(
        challengeKey = o.getString("challengeKey"), questionId = o.getString("questionId"),
        userId = o.getString("userId"), chosenIndex = o.getInt("chosenIndex"),
        isCorrect = o.getBoolean("isCorrect"), timeMs = o.optLong("timeMs", 0), answeredAt = o.optLong("answeredAt", 0),
    )

    private fun stateToJson(s: UserQuestionStateEntity) = JSONObject()
        .put("userId", s.userId).put("questionId", s.questionId).put("examType", s.examType)
        .put("section", s.section).put("topic", s.topic).put("state", s.state)
        .put("timesSeen", s.timesSeen).put("timesCorrect", s.timesCorrect).put("timesIncorrect", s.timesIncorrect)
        .put("consecutiveCorrect", s.consecutiveCorrect).put("firstSeenAt", s.firstSeenAt)
        .put("lastSeenAt", s.lastSeenAt).put("nextReviewAt", s.nextReviewAt).put("masteredAt", s.masteredAt)

    private fun stateFromJson(o: JSONObject) = UserQuestionStateEntity(
        userId = o.getString("userId"), questionId = o.getString("questionId"), examType = o.getString("examType"),
        section = o.optString("section", ""), topic = o.optString("topic", ""), state = o.getString("state"),
        timesSeen = o.optInt("timesSeen", 0), timesCorrect = o.optInt("timesCorrect", 0),
        timesIncorrect = o.optInt("timesIncorrect", 0), consecutiveCorrect = o.optInt("consecutiveCorrect", 0),
        firstSeenAt = o.optLong("firstSeenAt", 0), lastSeenAt = o.optLong("lastSeenAt", 0),
        nextReviewAt = o.optLong("nextReviewAt", 0), masteredAt = o.optLong("masteredAt", 0),
    )

    private fun deficitToJson(d: SectionDeficitEntity) = JSONObject()
        .put("userId", d.userId).put("examType", d.examType).put("section", d.section)
        .put("expectedToDate", d.expectedToDate).put("actualToDate", d.actualToDate)

    private fun deficitFromJson(o: JSONObject) = SectionDeficitEntity(
        userId = o.getString("userId"), examType = o.getString("examType"), section = o.getString("section"),
        expectedToDate = o.optDouble("expectedToDate", 0.0), actualToDate = o.optDouble("actualToDate", 0.0),
    )

    private fun streakToJson(s: StreakEntity) = JSONObject()
        .put("userId", s.userId).put("current", s.current).put("longest", s.longest)
        .put("lastCompletedLocalDate", s.lastCompletedLocalDate).put("milestonesCsv", s.milestonesCsv)

    private fun streakFromJson(o: JSONObject) = StreakEntity(
        userId = o.getString("userId"), current = o.optInt("current", 0), longest = o.optInt("longest", 0),
        lastCompletedLocalDate = o.optString("lastCompletedLocalDate", ""), milestonesCsv = o.optString("milestonesCsv", ""),
    )
}
