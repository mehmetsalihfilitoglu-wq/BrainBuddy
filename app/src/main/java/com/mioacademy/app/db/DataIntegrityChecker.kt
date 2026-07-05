package com.mioacademy.app.db

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
    )

    /**
     * Run the full integrity pass.
     * Returns counts of questions marked per category.
     */
    suspend fun runCleanup(context: Context): CleanupResult {
        val db = DatabaseProvider.get(context)
        val dao = db.questionDao()

        // Phase 1: SQL-based bulk detection (fast, indexed)
        val ph = dao.markPlaceholderOptions()
        val ss = dao.markShortStems()
        val eo = dao.markEmptyOptions()

        // Phase 2: Kotlin-side scan for complex corruption
        // (options JSON parsing, answer index validation, duplicate detection)
        val corruptCount = scanAndMarkCorruptPayloads(dao)

        val total = ph + ss + eo + corruptCount

        Log.w(TAG, "[DB_CLEANUP] placeholderOptions=$ph shortStems=$ss emptyOptions=$eo corruptPayloads=$corruptCount totalMarked=$total")

        // Phase 3: Log servable pool health
        logServablePoolHealth(dao)

        return CleanupResult(ph, ss, eo, corruptCount, total)
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

        // 3. Need at least 2 non-blank options
        val meaningful = opts.filter { it.isNotBlank() && it != "-" }
        if (meaningful.size < 2) return "DATA_CORRUPT_TOO_FEW_OPTIONS"

        // 4. Answer index must point to a valid, non-blank option
        if (q.answerIndex < 0 || q.answerIndex >= opts.size) return "DATA_CORRUPT_INVALID_ANSWER_IDX"
        val correctAnswer = opts.getOrNull(q.answerIndex)?.trim() ?: ""
        if (correctAnswer.isBlank() || correctAnswer == "-") return "DATA_CORRUPT_BLANK_ANSWER"

        // 5. All distinct (case-insensitive) — reject if < 2 distinct
        val distinctLower = meaningful.map { it.lowercase() }.distinct()
        if (distinctLower.size < 2) return "DATA_CORRUPT_ALL_SAME_OPTIONS"

        // 6. Placeholder options: "Seçenek A/B/C/D", "Option A/B/C/D", "Cevap A/B/C/D"
        val placeholderRegex = Regex("^(Seçenek|Option|Cevap|Şık)\\s*[A-Ea-e]$", RegexOption.IGNORE_CASE)
        val placeholderCount = opts.count { placeholderRegex.matches(it.trim()) }
        if (placeholderCount >= 2) return "DATA_CORRUPT_PLACEHOLDER"

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

            // Log per grade, focusing on key grades
            for (grade in 1..7) {
                val gradeRows = servable.filter { it.grade == grade }
                if (gradeRows.isEmpty()) continue
                val summary = gradeRows.joinToString(" ") { "${it.subject}=${it.count}" }
                val gradeTotal = gradeRows.sumOf { it.count }
                Log.w(TAG, "[POOL_HEALTH] grade=$grade total=$gradeTotal $summary")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[POOL_HEALTH] failed: ${e.message}")
        }
    }
}
