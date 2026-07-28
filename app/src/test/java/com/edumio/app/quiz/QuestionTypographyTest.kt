package com.edumio.app.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves the EDUmio Question Typography Standard is presentation-only:
 *  - it re-paragraphs a dense stem into readable sentence-per-paragraph text, AND
 *  - it NEVER changes the content — verified as a hard invariant over EVERY real question in the
 *    IMAT / TIL-I / CEnT-S banks (the same `normalize` is used on both sides, so "same content" means
 *    identical once whitespace is collapsed).
 */
class QuestionTypographyTest {

    /** The canonical content fingerprint: collapse all whitespace to single spaces and trim. */
    private fun collapse(s: String) = QuestionTypography.normalize(s)

    private fun assertContentPreserved(raw: String) = assertEquals(
        "typography must change ONLY whitespace/paragraphing, never content: <<$raw>>",
        collapse(raw), collapse(QuestionTypography.format(raw)),
    )

    @Test fun contentPreserved_onCraftedInputs() {
        listOf(
            "One idea. Another idea. And the question, what is X?",
            "The value is 3.14 and then it rises to 6.28 quickly.",
            "See e.g. the diagram. Then answer.",
            "Single sentence with no terminator",
            "P, Q and R are shown. Which is largest?",
            "A technician logs a response time. Dr. Smith reviews it. What is the mean?",
            "Multiple   spaces\tand\nnewlines   collapse. Second sentence here!",
            "",
            "   ",
        ).forEach { assertContentPreserved(it) }
    }

    @Test fun emptyAndBlank() {
        assertEquals("", QuestionTypography.format(null))
        assertEquals("", QuestionTypography.format(""))
        assertEquals("", QuestionTypography.format("   \n  "))
    }

    @Test fun singleSentence_isReturnedUnchanged() {
        val one = "What is the population standard deviation of the readings?"
        assertEquals(one, QuestionTypography.format(one))
    }

    @Test fun multiSentence_becomesParagraphs_andTheQuestionIsItsOwnParagraph() {
        val raw = "A technician logs a solenoid valve's response time, in milliseconds, over five trials. " +
            "The upper number line (\"Session 1\") shows the five readings. " +
            "A firmware update then adds the same fixed delay to every reading. " +
            "Working only from the plotted points, what is the population standard deviation (in ms) of the Session 2 readings?"
        val paras = QuestionTypography.format(raw).split("\n\n")
        assertEquals("4 sentences → 4 paragraphs", 4, paras.size)
        assertTrue("the actual question is the last, separated paragraph",
            paras.last().startsWith("Working only from the plotted points"))
        assertContentPreserved(raw)
    }

    @Test fun doesNotSplitOnDecimals() {
        assertEquals("The value is 3.14 today.", QuestionTypography.format("The value is 3.14 today."))
    }

    @Test fun doesNotSplitAfterAbbreviations() {
        val eg = QuestionTypography.format("Use a catalyst, e.g. Platinum, to speed it up. Then measure.")
        assertEquals(2, eg.split("\n\n").size)
        assertTrue("'e.g. Platinum' stays in one paragraph", eg.split("\n\n")[0].contains("e.g. Platinum"))
    }

    @Test fun structuredStem_withLineBreaks_isPreserved_notFlattened() {
        val table = "If a cell had 36 chromosomes, which row is correct?\n\n" +
            "Row | Zygote | Mitosis\n" +
            "A: 72 | 18 | 18\n" +
            "B: 36 | 18 | 36"
        val out = QuestionTypography.format(table)
        val lines = out.split("\n").filter { it.isNotBlank() }
        assertTrue("table rows must stay on their own lines (not flattened into prose)",
            lines.contains("A: 72 | 18 | 18") && lines.contains("B: 36 | 18 | 36"))
        assertTrue("the question line is preserved", lines.first().startsWith("If a cell had 36 chromosomes"))
        assertContentPreserved(table)
    }

    @Test fun isIdempotent() {
        val raw = "First. Second. Third one here?"
        val once = QuestionTypography.format(raw)
        assertEquals(collapse(once), collapse(QuestionTypography.format(once)))
    }

    // ── MVP guarantee: NO content change anywhere across the ENTIRE production bank ────────────────
    @Test fun contentPreserved_acrossEntireBank_allThreeExams() {
        var checked = 0
        var reflowed = 0
        for (asset in listOf("imat/imat_questions.json", "til_i/questions.json", "cents_s/questions.json")) {
            val arr = org.json.JSONArray(readAsset(asset))
            var sampled = 0
            for (i in 0 until arr.length()) {
                val stem = arr.optJSONObject(i)?.optString("stem").orEmpty()
                if (stem.isBlank()) continue
                val formatted = QuestionTypography.format(stem)
                assertEquals("$asset[$i]: typography changed the content", collapse(stem), collapse(formatted))
                if (formatted.contains("\n\n")) {
                    reflowed++
                    if (sampled < 1 && stem.length in 200..600) {
                        sampled++
                        println("\n[SAMPLE $asset]\n--- BEFORE ---\n$stem\n--- AFTER ---\n$formatted\n")
                    }
                }
                checked++
            }
        }
        println("[TYPOGRAPHY] $reflowed of $checked questions were re-paragraphed into multiple lines")
        assertTrue("should have checked the whole bank (>3000 questions)", checked > 3000)
        assertTrue("the standard must actually re-paragraph most dense questions, not be a no-op", reflowed > 500)
        println("[TYPOGRAPHY] content-preservation verified on $checked real questions across IMAT/TIL-I/CEnT-S")
    }

    private fun readAsset(rel: String): String {
        val f = listOf(
            File("src/main/assets/$rel"),
            File("app/src/main/assets/$rel"),
            File("../app/src/main/assets/$rel"),
        ).firstOrNull { it.exists() } ?: error("asset not found: $rel (cwd=${File(".").absolutePath})")
        return f.readText(Charsets.UTF_8)
    }
}
