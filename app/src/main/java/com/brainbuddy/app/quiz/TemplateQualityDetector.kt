package com.brainbuddy.app.quiz

import com.brainbuddy.app.db.QuestionEntity
import org.json.JSONArray

/**
 * Detects repeated weak template shells in a batch (e.g. seed run) and suppresses excess low-quality clones.
 * Does not delete rows — only adjusts [QuestionEntity.isActive] / flags / tier.
 */
object TemplateQualityDetector {

    private const val MIN_CLUSTER = 6
    private const val WEAK_RATIO = 0.55

    fun applyShellClustering(entities: List<QuestionEntity>): List<QuestionEntity> {
        if (entities.size < MIN_CLUSTER) return entities
        val keyed = entities.groupBy { shellKey(it) }
        return entities.map { e ->
            val group = keyed[shellKey(e)] ?: return@map e
            if (group.size < MIN_CLUSTER) return@map e
            val weak = group.count {
                it.reasoningScore < 45 || it.qualityTier == QuestionQualityClassifier.TIER_EASY
            }
            if (weak < group.size * WEAK_RATIO) return@map e
            val flags = appendFlagJson(e.qualityFlagsJson, "weak_template_cluster")
            e.copy(
                qualityTier = QuestionQualityClassifier.TIER_EASY,
                qualityFlagsJson = flags,
                isActive = false,
                deactivationReason = e.deactivationReason ?: "weak_template_cluster",
                unservableReason = e.unservableReason ?: "TRIVIAL",
            )
        }
    }

    private fun shellKey(e: QuestionEntity): String {
        val stem = e.stemNormalized.ifBlank { e.questionText }
        val shell = stem
            .lowercase(java.util.Locale("tr"))
            .replace(Regex("\\d+"), "#")
            .take(44)
        val optCount = try {
            JSONArray(e.optionsJson).length()
        } catch (_: Exception) {
            0
        }
        return "${e.grade}|${e.subject}|$shell|$optCount"
    }

    private fun appendFlagJson(existing: String, flag: String): String {
        val arr = try {
            JSONArray(existing)
        } catch (_: Exception) {
            JSONArray()
        }
        if ((0 until arr.length()).none { arr.optString(it, "") == flag }) {
            arr.put(flag)
        }
        return arr.toString()
    }
}
