package com.edumio.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Analytics must never carry PII or question/solution content. */
class AnalyticsSafetyTest {

    @Test fun blocksPiiAndContentKeys() {
        for (k in listOf("email", "user_email", "password", "auth_token", "question_stem",
            "optionA", "choice_text", "solution_body", "display_name")) {
            assertFalse("$k must be blocked", AnalyticsSafety.isKeyAllowed(k))
        }
    }

    @Test fun allowsSafeKeys() {
        for (k in listOf("question_id", "exam", "day", "score", "streak", "is_premium", "duration_ms")) {
            assertTrue("$k should be allowed", AnalyticsSafety.isKeyAllowed(k))
        }
    }

    @Test fun sanitizeDropsBlockedKeysAndTruncatesStrings() {
        val out = AnalyticsSafety.sanitize(mapOf(
            "question_id" to "q123",
            "email" to "a@b.com",
            "note" to "x".repeat(500),
        ))
        assertTrue(out.containsKey("question_id"))
        assertFalse(out.containsKey("email"))
        assertEquals(100, (out["note"] as String).length)
    }

    @Test fun eventNameTruncated() {
        assertEquals(40, AnalyticsSafety.safeEventName("e".repeat(80)).length)
    }
}
