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

    /** Asset directory per production exam; null for exams without a solution overlay. */
    fun assetDir(exam: ExamType): String? = when (exam) {
        ExamType.IMAT -> "imat"
        ExamType.TIL_I -> "til_i"
        ExamType.CENT_S -> "cents_s"
        else -> null
    }

    /** The solution for one question, or null when none is shipped. Off the main thread. */
    suspend fun solutionFor(context: Context, exam: ExamType, questionId: String): Solution? =
        index(context, exam)?.get(questionId)

    /** Number of shipped solutions for an exam (used by coverage diagnostics). */
    suspend fun count(context: Context, exam: ExamType): Int = index(context, exam)?.size ?: 0

    private suspend fun index(context: Context, exam: ExamType): Map<String, Solution>? {
        val dir = assetDir(exam) ?: return null
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
