package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The full-account sync snapshot must round-trip losslessly and re-key cleanly (anon → account link). */
class DailyChallengeSyncCodecTest {

    private fun sampleSnapshot(userId: String) = DailyChallengeSnapshot(
        challenges = listOf(
            DailyChallengeEntity(
                userId = userId, localDate = "2026-07-18", challengeId = "$userId:2026-07-18",
                examProfile = "IMAT", status = ChallengeStatus.IN_PROGRESS.name,
                questionIdsCsv = "q1,q2,q3,q4,q5", currentIndex = 2, allocationCsv = "biology:2|chemistry:1",
                createdAt = 111, startedAt = 222, completedAt = 0, expiresAt = 999, score = 0, shortage = "",
            )
        ),
        answers = listOf(
            ChallengeAnswerEntity("$userId:2026-07-18", "q1", userId, 0, true, 1500, 300),
            ChallengeAnswerEntity("$userId:2026-07-18", "q2", userId, 3, false, 2200, 400),
        ),
        states = listOf(
            UserQuestionStateEntity(userId, "q1", "IMAT", "biology", "topic-1", QuestionLearnState.CORRECT.name, timesSeen = 1, timesCorrect = 1),
            UserQuestionStateEntity(userId, "q2", "IMAT", "chemistry", "topic-2", QuestionLearnState.INCORRECT_ONCE.name, timesSeen = 1, timesIncorrect = 1),
        ),
        deficits = listOf(SectionDeficitEntity(userId, "IMAT", "biology", 1.9, 2.0)),
        streak = StreakEntity(userId, current = 3, longest = 5, lastCompletedLocalDate = "2026-07-17", milestonesCsv = "3"),
    )

    @Test
    fun roundTrip_preservesFullAccount() {
        val original = sampleSnapshot("u1")
        val restored = DailyChallengeSyncCodec.fromJson(DailyChallengeSyncCodec.toJson(original))!!
        assertEquals(original.challenges, restored.challenges)
        assertEquals(original.answers, restored.answers)
        assertEquals(original.states, restored.states)
        assertEquals(original.deficits, restored.deficits)
        assertEquals(original.streak, restored.streak)
    }

    @Test
    fun rekeyedTo_rewritesEveryUserIdAndEmbeddedKey() {
        val re = sampleSnapshot("anon_x").rekeyedTo("acct_y")
        assertEquals("acct_y", re.challenges[0].userId)
        assertEquals("acct_y:2026-07-18", re.challenges[0].challengeId)
        assertEquals(listOf("acct_y", "acct_y"), re.answers.map { it.userId })
        assertEquals(listOf("acct_y:2026-07-18", "acct_y:2026-07-18"), re.answers.map { it.challengeKey })
        assertEquals(listOf("acct_y", "acct_y"), re.states.map { it.userId })
        assertEquals("acct_y", re.deficits[0].userId)
        assertEquals("acct_y", re.streak!!.userId)
        // question ids / order / progress must be untouched by re-keying
        assertEquals("q1,q2,q3,q4,q5", re.challenges[0].questionIdsCsv)
        assertEquals(2, re.challenges[0].currentIndex)
    }

    @Test
    fun malformedJson_returnsNull() {
        assertNull(DailyChallengeSyncCodec.fromJson("{ not valid"))
    }
}
