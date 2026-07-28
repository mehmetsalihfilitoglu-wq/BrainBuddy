package com.edumio.app.solutions

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves complete Premium solution coverage over the shipped production banks:
 *  - every production-eligible question has a solution record,
 *  - every solution's correctOption matches the bank's stored answer key (no drift possible),
 *  - figure questions carry a figure explanation,
 *  - student-facing text contains no internal metadata/provenance and no option-letter references,
 *  - the question banks themselves are untouched by solution shipping (ids + answer keys intact).
 */
class SolutionCoverageTest {

    private data class Bank(
        val name: String, val questionsFile: String, val solutionsFile: String, val blockedFile: String,
    )

    private val banks = listOf(
        Bank("IMAT", "src/main/assets/imat/imat_questions.json", "src/main/assets/imat/solutions.json", "src/main/assets/imat/solutions_blocked.json"),
        Bank("EDUmio-original", "src/main/assets/edumio_original/questions.json", "src/main/assets/edumio_original/solutions.json", "src/main/assets/edumio_original/solutions_blocked.json"),
        Bank("TIL-I", "src/main/assets/til_i/questions.json", "src/main/assets/til_i/solutions.json", "src/main/assets/til_i/solutions_blocked.json"),
        Bank("CEnT-S", "src/main/assets/cents_s/questions.json", "src/main/assets/cents_s/solutions.json", "src/main/assets/cents_s/solutions_blocked.json"),
    )

    /**
     * Single source of truth for the COMPLETE production solution inventory, so an entire bank can
     * never be silently omitted again (this test is why the EDUmio-original bank was caught). Every
     * production question bank (asset dir → its questions file → expected count) is listed here; the
     * `noProductionBankIsSilentlyOmitted` test cross-checks it against the on-disk assets both ways.
     */
    private val productionBanks = mapOf(
        "imat" to Pair("imat/imat_questions.json", 940),
        "edumio_original" to Pair("edumio_original/questions.json", 1010),
        "til_i" to Pair("til_i/questions.json", 1107),
        "cents_s" to Pair("cents_s/questions.json", 1100),
    )
    private val grandTotalProduction = 4157

    /** Documented, verified answer-key conflicts that intentionally ship no solution. */
    private fun blockedIds(path: String): Set<String> {
        val f = File(path)
        if (!f.exists()) return emptySet()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    // Genuine provenance / internal-metadata leaks only (not ordinary English like "licensed physician").
    private val forbidden = Regex(
        "\\b(source\\s*type|provenance|as an ai|copyright|(licensed|official|original)\\s+bank|(elite|quality)\\s+tier)\\b|©|" +
            "\\boption\\s+[A-E]\\b|\\banswer\\s+[A-E]\\b|\\bchoice\\s+[A-E]\\b",
        RegexOption.IGNORE_CASE,
    )

    private fun read(path: String): String {
        val f = File(path)
        assertTrue("missing file: $path", f.exists())
        return f.readText()
    }

    @Test
    fun everyProductionQuestion_hasVerifiedMatchingSolution() {
        for (bank in banks) {
            val questions = JSONArray(read(bank.questionsFile))
            val solutions = JSONArray(read(bank.solutionsFile))
            val blocked = blockedIds(bank.blockedFile)
            val byId = HashMap<String, org.json.JSONObject>(solutions.length())
            for (i in 0 until solutions.length()) {
                val s = solutions.getJSONObject(i)
                byId[s.getString("questionId")] = s
            }
            val missingUnexpected = ArrayList<String>()
            var mismatched = 0
            var figureNoExpl = 0
            for (i in 0 until questions.length()) {
                val q = questions.getJSONObject(i)
                val id = q.getString("id")
                val s = byId[id]
                if (s == null) {
                    // A missing solution is allowed ONLY for a documented, verified key conflict.
                    if (id !in blocked) missingUnexpected.add(id)
                    continue
                }
                if (s.getInt("correctOption") != q.getInt("answerIndex")) mismatched++
                val hasFigure = q.optString("image").isNotBlank()
                if (hasFigure && s.optString("figureExplanation").isBlank()) figureNoExpl++
            }
            assertEquals("${bank.name}: questions missing a solution (and not documented-blocked): $missingUnexpected", 0, missingUnexpected.size)
            assertEquals("${bank.name}: solutions disagreeing with the stored answer key", 0, mismatched)
            assertEquals("${bank.name}: figure questions without figure explanation", 0, figureNoExpl)
            // A blocked id must genuinely have no shipped solution (blocking is honest, not cosmetic).
            for (b in blocked) assertTrue("${bank.name}: blocked id $b unexpectedly has a solution", byId[b] == null)
        }
    }

    @Test
    fun solutions_containNoInternalMetadataOrLetterReferences() {
        for (bank in banks) {
            val solutions = JSONArray(read(bank.solutionsFile))
            for (i in 0 until solutions.length()) {
                val s = solutions.getJSONObject(i)
                // internal verification fields must never ship
                assertTrue("${bank.name}: internal field shipped", !s.has("verified") && !s.has("verificationConfidence"))
                val texts = buildList {
                    add(s.optString("shortExplanation")); add(s.optString("keyConcept"))
                    add(s.optString("commonMistake")); add(s.optString("figureExplanation")); add(s.optString("formulaNotes"))
                    val steps = s.optJSONArray("solutionSteps")
                    if (steps != null) for (j in 0 until steps.length()) add(steps.optString(j))
                    val oe = s.optJSONObject("optionExplanations")
                    if (oe != null) for (k in oe.keys()) add(oe.optString(k))
                }
                for (t in texts) {
                    val m = forbidden.find(t)
                    assertTrue("${bank.name}/${s.optString("questionId")}: forbidden token '${m?.value}'", m == null)
                }
            }
        }
    }

    @Test
    fun noProductionBankIsSilentlyOmitted() {
        // Every listed production bank has complete coverage (a solution OR a documented block per Q).
        var grand = 0
        for ((dir, spec) in productionBanks) {
            val (qfile, expected) = spec
            val q = JSONArray(read("src/main/assets/$qfile"))
            assertEquals("$dir: question count drifted from expected", expected, q.length())
            val sol = JSONArray(read("src/main/assets/$dir/solutions.json"))
            val solIds = (0 until sol.length()).map { sol.getJSONObject(it).getString("questionId") }.toSet()
            val blocked = blockedIds("src/main/assets/$dir/solutions_blocked.json")
            for (i in 0 until q.length()) {
                val id = q.getJSONObject(i).getString("id")
                assertTrue("$dir/$id: neither a solution nor a documented block", id in solIds || id in blocked)
            }
            assertEquals("$dir: shipped + blocked must exactly equal the inventory", q.length(), solIds.size + blocked.size)
            grand += q.length()
        }
        assertEquals("complete production inventory across all banks", grandTotalProduction, grand)

        // Two-way guard: any asset bank that ships a solutions.json MUST be one of the production banks,
        // and every production bank MUST ship one — so a new bank cannot be added without coverage,
        // and a covered bank cannot be dropped, without failing here.
        val assetsRoot = File("src/main/assets")
        val banksWithSolutions = (assetsRoot.listFiles() ?: emptyArray())
            .filter { it.isDirectory && File(it, "solutions.json").exists() }
            .map { it.name }.toSet()
        assertEquals("mismatch between banks that ship solutions and the production coverage set",
            productionBanks.keys, banksWithSolutions)
    }

    @Test
    fun questionBanks_remainIntactNextToSolutions() {
        // Shipping solutions must not alter the banks: ids unique, options present, answerIndex in range.
        for (bank in banks) {
            val questions = JSONArray(read(bank.questionsFile))
            val seen = HashSet<String>()
            for (i in 0 until questions.length()) {
                val q = questions.getJSONObject(i)
                assertTrue("${bank.name}: duplicate id", seen.add(q.getString("id")))
                val choices = q.getJSONArray("choices")
                val idx = q.getInt("answerIndex")
                assertTrue("${bank.name}: answerIndex out of range", idx in 0 until choices.length())
            }
        }
    }
}
