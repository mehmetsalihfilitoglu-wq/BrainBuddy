package com.edumio.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edumio.app.core.RewardedRetryStore
import com.edumio.app.gate.GateLockedStore
import com.edumio.app.gate.GateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for gate and rewarded retry logic with real Context.
 */
@RunWith(AndroidJUnit4::class)
class GateAndRetryInstrumentedTest {

    private lateinit var context: android.content.Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun gateManager_perPackageLockRequiresGate() {
        val testPkg = "com.example.blocked.app"
        val store = GateLockedStore(context)
        store.addGateLocked(testPkg)
        assertTrue(GateManager.gateRequiredNow(context, testPkg))
        store.removeGateLocked(testPkg)
        assertFalse(GateManager.gateRequiredNow(context, testPkg))
    }

    @Test
    fun rewardedRetryStore_respectsDailyLimit() {
        val store = RewardedRetryStore(context)
        val profileId = "test_profile_${System.currentTimeMillis()}"
        val quizId = "quiz1"
        val questionId = "q1"
        assertTrue(store.canRetryWithAd(profileId, quizId, questionId))
        store.recordRetryUsed(profileId, quizId, questionId)
        assertFalse(store.canRetryWithAd(profileId, quizId, questionId))
        assertEquals(0, store.getRemainingRetriesToday(profileId))
    }

    @Test
    fun quizResultDecodeSession_handlesValidJson() {
        val json = """{"quizId":"q1","startedAt":1000,"completedAt":2000,"correctCount":7,"wrongCount":3,"blankCount":0,"passed":true,"wrongQuestionIds":[],"answers":{},"questionIds":["a","b","c","d","e","f","g","h","i","j"]}"""
        val s = com.edumio.app.quiz.QuizResultActivity.decodeSession(json)
        assertEquals("q1", s?.quizId)
        assertEquals(7, s?.correctCount)
        assertEquals(3, s?.wrongCount)
        assertEquals(true, s?.passed)
    }

    @Test
    fun quizResultDecodeSession_returnsNullForInvalid() {
        assertEquals(null, com.edumio.app.quiz.QuizResultActivity.decodeSession(null))
        assertEquals(null, com.edumio.app.quiz.QuizResultActivity.decodeSession(""))
    }
}
