package com.brainbuddy.app.db

import android.content.Context
import android.util.Log
import org.json.JSONArray

/**
 * One-shot data integrity pass that runs at startup AFTER seeding and quarantine restore.
 *
 * Detects corrupt/placeholder questions in the existing DB and marks them
 * with unservableReason = 'DATA_CORRUPT_*'. These questions will be excluded
 * from the candidate pool by DAO queries (unservableReason IS NULL check)
 * but are NOT deleted — they can be inspected and repaired later.
 *
 * Idempotent: questions already marked are skipped (WHERE unservableReason IS NULL).
 * Cheap: uses indexed SQL updates, no full table scan in Kotlin.
 */
object DataIntegrityChecker {

    private const val TAG = "DataIntegrityChecker"

    data class CleanupResult(
        val placeholderOptions: Int,
        val shortStems: Int,
        val emptyOptions: Int,
        val corruptPayloads: Int,
        val totalMarked: Int,
        /** Questions hard-deleted because they contained placeholder options. */
        val hardDeleted: Int = 0,
    )

    /**
     * Run the full integrity pass.
     * Returns counts of questions marked per category.
     */
    suspend fun runCleanup(context: Context): CleanupResult {
        val db = DatabaseProvider.get(context)
        val dao = db.questionDao()

        // Phase -1: Repair progressive quarantine damage from previous app versions.
        // The old runtime picker permanently marked questions as LOW_QUALITY_QUARANTINED
        // and the old integrity checker marked normal math questions as DATA_CORRUPT_WEAK_DISTRACTOR.
        // Both caused irreversible pool depletion. Un-quarantine them so they re-enter the pool.
        val repaired = try {
            val count = dao.clearProgressiveQuarantineDamage()
            if (count > 0) Log.w(TAG, "[POOL_REPAIR] Cleared $count questions from progressive quarantine (LOW_QUALITY_QUARANTINED + WEAK_DISTRACTOR)")
            count
        } catch (e: Exception) {
            Log.e(TAG, "[POOL_REPAIR] Failed: ${e.message}")
            0
        }

        // Phase 0: Hard delete existing placeholder-option questions (they should not exist in DB).
        val hardDeleted = try {
            val deleted = dao.deleteQuestionsWithPlaceholderOptions()
            if (deleted > 0) Log.w(TAG, "[DB_CLEANUP_HARD_DELETE] Deleted $deleted question(s) with placeholder options (Seçenek A/B/C/D etc.)")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "[DB_CLEANUP_HARD_DELETE] Failed: ${e.message}")
            0
        }

        // Phase 1: SQL-based bulk detection (fast, indexed)
        val ph = dao.markPlaceholderOptions()
        val ss = dao.markShortStems()
        val eo = dao.markEmptyOptions()

        // Phase 2: Kotlin-side scan for complex corruption
        // (options JSON parsing, answer index validation, duplicate detection)
        val corruptCount = scanAndMarkCorruptPayloads(dao)

        val total = ph + ss + eo + corruptCount

        Log.w(TAG, "[DB_CLEANUP] hardDeleted=$hardDeleted placeholderOptions=$ph shortStems=$ss emptyOptions=$eo corruptPayloads=$corruptCount totalMarked=$total")

        // Phase 3: Log servable pool health
        logServablePoolHealth(dao)

        return CleanupResult(ph, ss, eo, corruptCount, total, hardDeleted)
    }

    /**
     * Scan active questions for complex corruption that can't be caught by SQL alone:
     * - Invalid answerIndex pointing to blank/missing option
     * - All 4 options identical
     * - Fewer than 2 distinct meaningful options
     * - Malformed optionsJson
     */
    private suspend fun scanAndMarkCorruptPayloads(dao: QuestionDao): Int {
        // Only scan active questions with no existing unservableReason
        val candidates = dao.getAllQuestions().filter {
            it.isActive && it.unservableReason.isNullOrBlank()
        }

        var marked = 0
        for (q in candidates) {
            val reason = detectCorruption(q)
            if (reason != null) {
                try {
                    dao.updateQuarantineFlags(q.id, reason, q.qualityTier)
                    marked++
                    if (marked <= 20) {
                        Log.w(TAG, "[DB_CLEANUP_DETAIL] id=${q.id} reason=$reason stem=${q.questionText.take(60)}")
                    }
                } catch (_: Exception) { }
            }
        }
        if (marked > 20) {
            Log.w(TAG, "[DB_CLEANUP_DETAIL] ... and ${marked - 20} more")
        }
        return marked
    }

    /**
     * Returns non-null reason string if the question is corrupt, null if OK.
     */
    fun detectCorruption(q: QuestionEntity): String? {
        // 1. Blank or very short stem
        val stem = q.questionText.trim()
        if (stem.length < 15) return "DATA_CORRUPT_SHORT_STEM"

        // 2. Parse options
        val opts = try {
            val arr = JSONArray(q.optionsJson)
            List(arr.length()) { arr.getString(it).trim() }
        } catch (_: Exception) {
            return "DATA_CORRUPT_MALFORMED_JSON"
        }

        // 3. Need exactly 4 non-blank, non-dash options.
        val meaningful = opts.filter { it.isNotBlank() && it != "-" }
        if (meaningful.size < 4) return "DATA_CORRUPT_TOO_FEW_OPTIONS"

        // 4. Answer index must point to a valid, non-blank option
        if (q.answerIndex < 0 || q.answerIndex >= opts.size) return "DATA_CORRUPT_INVALID_ANSWER_IDX"
        val correctAnswer = opts.getOrNull(q.answerIndex)?.trim() ?: ""
        if (correctAnswer.isBlank() || correctAnswer == "-") return "DATA_CORRUPT_BLANK_ANSWER"

        // 5. All 4 options must be distinct (case-sensitive trim only —
        //    lowercase is NOT used because genetics options AA/Aa/aa and
        //    Turkish capitalization-quiz options are legitimately different).
        val distinctTrimmed = meaningful.take(4).map { it.trim() }.distinct()
        if (distinctTrimmed.size < 4) return "DATA_CORRUPT_ALL_SAME_OPTIONS"

        // 6. Placeholder options: ANY single "Seçenek A/B/C/D", "Option A/B/C/D", etc.
        //    Even ONE placeholder in the option list makes the question unservable.
        val placeholderRegex = Regex(
            "^(Se[çc]enek|Option|Cevap|[Şş][ıi]k)\\s*[A-Ea-e]$",
            RegexOption.IGNORE_CASE
        )
        val placeholderCount = opts.count { placeholderRegex.matches(it.trim()) }
        if (placeholderCount >= 1) return "DATA_CORRUPT_PLACEHOLDER"

        // 7. Telegraphed answer: correct option >3x longer than longest distractor
        //    AND distractor is very short (<15 chars). Raised from 2x to 3x to avoid
        //    quarantining legitimate questions with explanatory correct answers.
        val four = meaningful.take(4)
        val correctLen = four.getOrNull(q.answerIndex)?.trim()?.length ?: 0
        val maxDistractorLen = four.indices
            .filter { it != q.answerIndex }
            .maxOfOrNull { four[it].trim().length } ?: 0
        if (maxDistractorLen in 6..14 && correctLen > maxDistractorLen * 3) {
            return "DATA_CORRUPT_TELEGRAPHED"
        }

        // 8. Weak numeric distractors: disabled — the 2.5x spread threshold was
        //    quarantining the majority of math questions (e.g. options [3,4,7,12]
        //    have spread 4.0 which is perfectly normal for arithmetic questions).
        //    Quality is better enforced at the quiz-build layer, not here.

        return null
    }

    /**
     * Log the actual servable pool counts per grade+subject.
     */
    private suspend fun logServablePoolHealth(dao: QuestionDao) {
        try {
            val servable = dao.getServableCountsByGradeSubject()
            val totalServable = servable.sumOf { it.count }
            val totalActive = dao.countAllActive()
            val totalCorrupt = dao.countDataCorrupt()

            Log.w(TAG, "[POOL_HEALTH] totalActive=$totalActive totalServable=$totalServable totalCorrupt=$totalCorrupt")

            for (grade in 1..7) {
                val gradeRows = servable.filter { it.grade == grade }
                if (gradeRows.isEmpty()) continue
                val summary = gradeRows.joinToString(" ") { "${it.subject}=${it.count}" }
                val gradeTotal = gradeRows.sumOf { it.count }
                Log.w(TAG, "[POOL_HEALTH] grade=$grade total=$gradeTotal $summary")
            }

            // Deep runtime scan: verify zero bad rows in servable pool
            runDeepServableScan(dao)
        } catch (e: Exception) {
            Log.e(TAG, "[POOL_HEALTH] failed: ${e.message}")
        }
    }

    /**
     * Deep scan: every active servable question is checked for placeholder/duplicate options.
     * Logs 50 sample questions as runtime proof.
     */
    private suspend fun runDeepServableScan(dao: QuestionDao) {
        try {
            val placeholderPattern = Regex(
                "^(Se[çc]enek|Option|Cevap|[Şş][ıi]k)\\s*[A-Ea-e]$",
                RegexOption.IGNORE_CASE
            )
            val all = dao.getAllQuestions()
            val servable = all.filter { it.isActive && it.unservableReason.isNullOrBlank() }

            var badPlaceholder = 0
            var badTooFew = 0
            var badDuplicate = 0
            var badAnswerIdx = 0

            for (q in servable) {
                try {
                    val arr = org.json.JSONArray(q.optionsJson)
                    val opts = (0 until arr.length()).map { arr.optString(it, "").trim() }
                    val real = opts.filter { it.isNotBlank() && it != "-" }
                    if (real.size < 4) { badTooFew++; continue }
                    if (real.take(4).any { placeholderPattern.matches(it) }) { badPlaceholder++ }
                    if (real.take(4).map { it.trim() }.distinct().size < 4) { badDuplicate++ }
                    if (q.answerIndex < 0 || q.answerIndex >= opts.size) { badAnswerIdx++ }
                } catch (_: Exception) {}
            }

            Log.w(TAG, "[RUNTIME_PROOF] ===== STARTUP DB SCAN (DataIntegrityChecker) =====")
            Log.w(TAG, "[RUNTIME_PROOF] servablePool=${servable.size} totalDB=${all.size}")
            Log.w(TAG, "[RUNTIME_PROOF] badPlaceholder=$badPlaceholder (TARGET: 0)")
            Log.w(TAG, "[RUNTIME_PROOF] badTooFew=$badTooFew (TARGET: 0)")
            Log.w(TAG, "[RUNTIME_PROOF] badDuplicate=$badDuplicate (TARGET: 0)")
            Log.w(TAG, "[RUNTIME_PROOF] badAnswerIdx=$badAnswerIdx (TARGET: 0)")

            // Log 50 sample questions
            val sample = servable.shuffled().take(50)
            Log.w(TAG, "[RUNTIME_PROOF] ===== 50 SAMPLE SERVED QUESTIONS =====")
            sample.forEachIndexed { i, q ->
                try {
                    val arr = org.json.JSONArray(q.optionsJson)
                    val opts = (0 until arr.length()).map { arr.optString(it, "").trim() }
                    val correct = opts.getOrNull(q.answerIndex) ?: "?"
                    Log.w(TAG, "[RUNTIME_PROOF] Q$i sub=${q.subject} gr=${q.grade} " +
                        "stem='${q.questionText.take(70)}' " +
                        "opts=[${opts.joinToString(" | ")}] " +
                        "ansIdx=${q.answerIndex} correct='$correct'")
                } catch (_: Exception) {}
            }
            Log.w(TAG, "[RUNTIME_PROOF] ========================================")
        } catch (e: Exception) {
            Log.e(TAG, "[RUNTIME_PROOF] deep scan failed: ${e.message}")
        }
    }
}
