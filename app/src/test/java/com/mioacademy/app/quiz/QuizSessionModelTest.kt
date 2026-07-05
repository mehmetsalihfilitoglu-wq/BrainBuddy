package com.mioacademy.app.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for QuizSession model - totalCount, accuracy, pass logic.
 */
class QuizSessionModelTest {

    @Test
    fun totalCount_sumsCorrectly() {
        val s = QuizSession(
            quizId = "q1", startedAt = 0L,
            correctCount = 7, wrongCount = 2, blankCount = 1
        )
        assertEquals(10, s.totalCount)
    }

    @Test
    fun accuracy_calculatesCorrectly() {
        val s = QuizSession(
            quizId = "q1", startedAt = 0L,
            correctCount = 8, wrongCount = 2, blankCount = 0
        )
        assertEquals(80f, s.accuracy, 0.01f)
    }

    @Test
    fun accuracy_zeroWhenEmpty() {
        val s = QuizSession(quizId = "q1", startedAt = 0L)
        assertEquals(0f, s.accuracy, 0.01f)
    }

    @Test
    fun passed_wrongCount3OrLess() {
        assertTrue(QuizSession(quizId = "q1", startedAt = 0L, wrongCount = 3, passed = true).passed)
    }

    @Test
    fun failed_wrongCount4OrMore() {
        assertFalse(QuizSession(quizId = "q1", startedAt = 0L, wrongCount = 4, passed = false).passed)
    }
}
