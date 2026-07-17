package com.edumio.app.quiz

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [MatQuestionValidator].
 */
class MatQuestionValidatorTest {

    private fun validQuestion(): JSONObject = JSONObject().apply {
        put("stem", "Bir kitap mağazasında satılan romanın etiket fiyatı 480 TL'dir. Mağaza ilk olarak bu kitaba %25 indirim uyguluyor.")
        put("options", JSONArray(listOf("364", "360", "372", "348")))
        put("answerIndex", 1)
        put("topic", "yuzdeler")
        put("difficulty", 2)
        put("questionType", "long_context")
        put("skills", JSONArray(listOf("yuzdeler", "problem_cozme")))
        put("explanation", "480 × 0,75 = 360 TL.")
        put("source", "edumio_generated")
        put("sourceRef", "gold030_q01")
    }

    @Test
    fun validQuestionPasses() {
        val result = MatQuestionValidator.validateQuestion(validQuestion())
        assertTrue("Valid question should pass: ${result.reasons}", result.isValid)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun blankStemFails() {
        val q = validQuestion()
        q.put("stem", "")
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.STEM_BLANK))
    }

    @Test
    fun wrongOptionCountFails() {
        val q = validQuestion()
        q.put("options", JSONArray(listOf("A", "B", "C")))
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.OPTIONS_COUNT_NOT_4))
    }

    @Test
    fun fiveOptionsFails() {
        val q = validQuestion()
        q.put("options", JSONArray(listOf("A", "B", "C", "D", "E")))
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.OPTIONS_COUNT_NOT_4))
    }

    @Test
    fun invalidAnswerIndexFails() {
        val q = validQuestion()
        q.put("answerIndex", 4)
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.ANSWER_INDEX_OUT_OF_RANGE))
    }

    @Test
    fun negativeAnswerIndexFails() {
        val q = validQuestion()
        q.put("answerIndex", -1)
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.ANSWER_INDEX_OUT_OF_RANGE))
    }

    @Test
    fun duplicateOptionsFail() {
        val q = validQuestion()
        q.put("options", JSONArray(listOf("42", "42", "100", "200")))
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.DUPLICATE_OPTIONS))
    }

    @Test
    fun duplicateStemsInSamePackFail() {
        val q0 = validQuestion()
        val q1 = validQuestion().apply { put("sourceRef", "gold030_q02") }
        val root = JSONObject().apply {
            put("version", 1)
            put("subject", "mat")
            put("questions", JSONArray().apply {
                put(q0)
                put(q1)
            })
        }
        val packResult = MatQuestionValidator.validatePack(root)
        assertEquals(2, packResult.totalQuestions)
        assertTrue(packResult.questionResults[0].second.isValid)
        assertFalse("Second question with duplicate stem should fail", packResult.questionResults[1].second.isValid)
        assertTrue(
            packResult.questionResults[1].second.reasons.contains(MatQuestionValidator.RejectionReason.DUPLICATE_STEM_IN_PACK)
        )
    }

    @Test
    fun emptyPackFails() {
        val root = JSONObject().apply {
            put("version", 1)
            put("subject", "mat")
            put("questions", JSONArray())
        }
        val packResult = MatQuestionValidator.validatePack(root)
        assertFalse(packResult.isValid)
        assertTrue(packResult.packReasons.contains("pack_empty_questions"))
        assertTrue(packResult.questionResults.isEmpty())
    }

    @Test
    fun missingQuestionsArrayFails() {
        val root = JSONObject().apply {
            put("version", 1)
            put("subject", "mat")
        }
        val packResult = MatQuestionValidator.validatePack(root)
        assertFalse(packResult.isValid)
        assertTrue(packResult.packReasons.contains("pack_empty_questions"))
    }

    @Test
    fun blankTopicFails() {
        val q = validQuestion()
        q.put("topic", "")
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.TOPIC_BLANK))
    }

    @Test
    fun blankQuestionTypeFails() {
        val q = validQuestion()
        q.put("questionType", "")
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.QUESTION_TYPE_BLANK))
    }

    @Test
    fun skillsMissingOrEmptyFails() {
        val q = validQuestion()
        q.put("skills", JSONArray())
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.SKILLS_MISSING_OR_EMPTY))
    }

    @Test
    fun sourceMissingFails() {
        val q = validQuestion()
        q.put("source", "")
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.SOURCE_MISSING))
    }

    @Test
    fun sourceRefMissingFails() {
        val q = validQuestion()
        q.put("sourceRef", "")
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.SOURCE_REF_MISSING))
    }

    @Test
    fun blankOptionFails() {
        val q = validQuestion()
        q.put("options", JSONArray(listOf("A", "B", "", "D")))
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.OPTION_BLANK))
    }

    @Test
    fun invalidDifficultyFails() {
        val q = validQuestion()
        q.put("difficulty", 5)
        val result = MatQuestionValidator.validateQuestion(q)
        assertFalse(result.isValid)
        assertTrue(result.reasons.contains(MatQuestionValidator.RejectionReason.DIFFICULTY_INVALID))
    }

    @Test
    fun validationReportFormatsCorrectly() {
        val root = JSONObject().apply {
            put("version", 1)
            put("subject", "mat")
            put("questions", JSONArray().apply {
                put(validQuestion())
                put(validQuestion().apply {
                    put("stem", "")
                    put("sourceRef", "broken_q")
                })
            })
        }
        val packResult = MatQuestionValidator.validatePack(root, "test_pack.json")
        val report = MatQuestionValidator.buildReport(packResult, "test_pack.json")
        assertEquals("test_pack.json", report.packName)
        assertEquals(2, report.totalQuestions)
        assertEquals(1, report.validCount)
        assertEquals(1, report.rejectedCount)
        assertTrue(report.reasonsByCount.containsKey(MatQuestionValidator.RejectionReason.STEM_BLANK))
        val formatted = report.formatForLog()
        assertTrue(formatted.contains("test_pack.json"))
        assertTrue(formatted.contains("Total questions: 2"))
        assertTrue(formatted.contains("Valid: 1"))
        assertTrue(formatted.contains("Rejected: 1"))
    }
}
