package com.edumio.app.solutions

import android.content.Context
import com.edumio.app.core.ExamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-only access to the verified solution overlays. Solutions ship as assets
 * (`{imat,til_i,cents_s}/solutions.json`), so they are available offline, survive restart/process
 * death/app update/database reseeding, and never need regeneration — opening one is a pure lookup.
 *
 * Each exam's file is parsed once per process and indexed by questionId; subsequent lookups are
 * in-memory. Missing file / malformed record → null (the UI shows its graceful "no solution" state);
 * nothing here ever throws into the caller.
 */
object SolutionStore {

    private val cache = ConcurrentHashMap<String, Map<String, Solution>>()

    /**
     * Asset directory per production bank, keyed by the question's stored examType STRING. Covers the
     * Daily-Challenge exams (IMAT / TIL-I / CEnT-S) AND the IMAT-format original practice pool
     * ("EDUMIO_ORIGINAL"), which is a separate examType served in the quiz flow. Returns null for
     * exams without a solution overlay.
     */
    fun assetDir(examTypeName: String): String? = when (examTypeName) {
        "IMAT" -> "imat"
        "TIL_I" -> "til_i"
        "CENT_S" -> "cents_s"
        "EDUMIO_ORIGINAL" -> "edumio_original"
        else -> null
    }

    /** Convenience for the Daily-Challenge [ExamType] enum. */
    fun assetDir(exam: ExamType): String? = assetDir(exam.name)

    /** The solution for one question by its stored examType string, or null when none is shipped. */
    suspend fun solutionFor(context: Context, examTypeName: String, questionId: String): Solution? =
        index(context, assetDir(examTypeName))?.get(questionId)

    /** The solution for one question, or null when none is shipped. Off the main thread. */
    suspend fun solutionFor(context: Context, exam: ExamType, questionId: String): Solution? =
        index(context, assetDir(exam))?.get(questionId)

    /** Number of shipped solutions for an exam (used by coverage diagnostics). */
    suspend fun count(context: Context, exam: ExamType): Int = index(context, assetDir(exam))?.size ?: 0

    private suspend fun index(context: Context, dir: String?): Map<String, Solution>? {
        if (dir == null) return null
        cache[dir]?.let { return it }
        return withContext(Dispatchers.IO) {
            cache[dir] ?: try {
                val json = context.applicationContext.assets.open("$dir/solutions.json")
                    .bufferedReader().use { it.readText() }
                val arr = JSONArray(json)
                val map = HashMap<String, Solution>(arr.length())
                for (i in 0 until arr.length()) {
                    Solution.fromJson(arr.optJSONObject(i) ?: continue)?.let { map[it.questionId] = it }
                }
                map.also { cache[dir] = it }
            } catch (_: Throwable) {
                null // asset absent or unreadable → no solutions for this exam (graceful)
            }
        }
    }
}
