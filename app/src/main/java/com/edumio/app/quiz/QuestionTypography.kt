package com.edumio.app.quiz

/**
 * Presentation-only typography for question stems — the EDUmio Question Typography Standard.
 *
 * Makes a question read like a textbook, not a wall of text (Rules 1, 2, 5, 6), applied centrally by
 * every question renderer so the entire IMAT / TIL-I / CEnT-S bank is presented consistently. Two cases:
 *   - a dense single block (no line breaks) is re-flowed into one sentence per paragraph;
 *   - a stem the author already structured with line breaks (a table or list) is PRESERVED as-is (only
 *     horizontal whitespace tidied and runs of blank lines collapsed) — never flattened or reflowed.
 *
 * STRICTLY LOSSLESS — it NEVER changes the content. The only edits are whitespace normalization and
 * inserting/keeping line breaks between sentences/rows. No word is added, removed, reordered, shortened,
 * simplified or paraphrased. Formally, collapsing the OUTPUT's whitespace back to single spaces
 * reproduces EXACTLY the collapsed input — proven over every real question by QuestionTypographyTest.
 */
object QuestionTypography {

    /** Blank line between paragraphs — generous vertical spacing so the question breathes (Rule 6). */
    private const val PARAGRAPH_BREAK = "\n\n"

    /** Any run of whitespace, including the non-breaking space (U+00A0), collapses to one space. */
    val WHITESPACE = Regex("[\\s\\u00A0]+")

    /** Horizontal whitespace only (never a line break), so authored line structure is preserved. */
    private val HORIZONTAL_WHITESPACE = Regex("[^\\S\\n]+")

    /** Tokens ending in '.' that do NOT end a sentence — never split right after them. */
    private val ABBREVIATIONS = setOf(
        "e.g", "i.e", "etc", "vs", "cf", "approx", "no", "nos", "fig", "figs", "eq", "eqs", "al",
        "dr", "mr", "mrs", "ms", "prof", "st", "mt", "inc", "ltd", "co", "corp", "jr", "sr",
        "min", "max", "avg", "sec", "hr", "hrs", "vol", "ch", "pp", "ph.d", "b.c", "a.d", "b.c.e", "c.e",
        "u.s", "u.k", "u.s.a", "a.m", "p.m", "d.c", "mt", "gen", "sen", "rep", "gov",
    )

    /** Reformat a stem for display: same words, only re-paragraphed / tidied. "" for null/blank input. */
    fun format(raw: String?): String {
        if (raw == null) return ""
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return ""
        // If the author already used line breaks, they structured it deliberately (table / list / rows) —
        // preserve that structure. Otherwise re-flow the single dense block into sentence paragraphs.
        return if (text.contains('\n')) tidyStructured(text) else reflow(text)
    }

    /** Collapse ALL whitespace (incl. NBSP + line breaks) to single spaces and trim. */
    fun normalize(raw: String?): String = raw?.replace(WHITESPACE, " ")?.trim().orEmpty()

    /** Re-flow one dense block into one sentence per paragraph (blank line between). */
    private fun reflow(block: String): String {
        val normalized = normalize(block)
        val sentences = splitSentences(normalized)
        return if (sentences.size <= 1) normalized else sentences.joinToString(PARAGRAPH_BREAK)
    }

    /** Preserve authored line structure: tidy horizontal whitespace per line, collapse blank-line runs. */
    private fun tidyStructured(text: String): String {
        val out = ArrayList<String>()
        var prevBlank = true // also drops leading blank lines
        for (rawLine in text.split('\n')) {
            val line = rawLine.replace(HORIZONTAL_WHITESPACE, " ").trim()
            if (line.isEmpty() && prevBlank) continue // never more than one blank line in a row
            out.add(line)
            prevBlank = line.isEmpty()
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.size - 1)
        return out.joinToString("\n")
    }

    private fun splitSentences(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            sb.append(c)
            val isTerminator = c == '.' || c == '!' || c == '?'
            // A boundary is: terminator + a single space + an "opener" (capital / digit / quote / bracket).
            if (isTerminator && text.getOrNull(i + 1) == ' ') {
                val opener = text.getOrNull(i + 2)
                val opensSentence = opener != null && (
                    opener.isUpperCase() || opener.isDigit() ||
                        opener == '"' || opener == '“' || opener == '(' ||
                        opener == '\'' || opener == '‘' || opener == '['
                    )
                if (opensSentence && !isDecimalPoint(text, i) && !endsWithAbbreviation(sb)) {
                    out.add(sb.toString().trim())
                    sb.setLength(0)
                    i += 2 // consume the terminator's trailing space
                    continue
                }
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    /** '.' strictly between two digits is a decimal (e.g. 3.14), never a sentence end. */
    private fun isDecimalPoint(text: String, dot: Int): Boolean =
        text[dot] == '.' &&
            text.getOrNull(dot - 1)?.isDigit() == true &&
            text.getOrNull(dot + 1)?.isDigit() == true

    /** Whether [sb] ends in a known abbreviation or a single-letter initial (so we must not split). */
    private fun endsWithAbbreviation(sb: CharSequence): Boolean {
        val s = sb.toString()
        if (!s.endsWith(".")) return false
        val body = s.substring(0, s.length - 1)
        var start = -1
        for (k in body.length - 1 downTo 0) if (body[k] in " (\"“'‘[/") { start = k; break }
        val token = body.substring(start + 1).lowercase()
        if (token.isEmpty()) return false
        if (token.length == 1 && token[0].isLetter()) return true // "A." initial
        return token in ABBREVIATIONS
    }
}
