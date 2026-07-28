package com.edumio.app.quiz

import android.util.Log
import com.edumio.app.db.QuestionEntity
import org.json.JSONArray

/**
 * Detects repeated weak template shells in a batch (e.g. seed run) and suppresses excess low-quality clones.
 * Does not delete rows — only adjusts [QuestionEntity.isActive] / flags / tier.
 *
 * Also provides [normalizeStemForTemplateDedup] for template-level duplicate detection
 * that goes beyond exact-text matching: removes numbers, city names, common units and
 * boilerplate Turkish phrasing so structurally identical questions are detected as clones.
 */
object TemplateQualityDetector {

    private const val TAG = "TemplateQualityDetector"
    private const val MIN_CLUSTER = 6
    private const val WEAK_RATIO = 0.55

    private val CITY_NAMES = setOf(
        "izmir", "ankara", "konya", "istanbul", "bursa", "antalya", "adana",
        "eskişehir", "eskisehir", "kayseri", "trabzon", "samsun", "gaziantep",
        "mersin", "diyarbakır", "diyarbakir", "hatay", "kocaeli", "manisa",
        "erzurum", "malatya", "balıkesir", "balikesir", "elazığ", "elazig",
        "sivas", "van", "kahramanmaraş", "kahramanmaras", "mardin"
    )
    private val CITY_PATTERN = Regex(
        "\\b(${CITY_NAMES.joinToString("|") { Regex.escape(it) }})\\b",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val UNIT_PATTERN = Regex(
        "\\b(km|kilometre|kilometredir|saatte|kmh|km/s|km/saat|kilom[eé]tre|" +
            "litre|litredir|kg|kilogram|kilogramdır|gram|gramdır|" +
            "metre|metredir|santimetre|cm|mm|tl|lira)\\b",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val BOILERPLATE_PATTERN = Regex(
        "\\b(arasındaki|uzaklık|uzaklığı|hızla|hızıyla|kaç saatte|kaç saat|sürüyor|" +
            "yol alır|gidecek|km ile|km hızla|saatte giden|saat giden)\\b",
        setOf(RegexOption.IGNORE_CASE)
    )

    /**
     * Normalize a question stem for template-level duplicate detection.
     * Removes numbers, city names, measurement units and boilerplate phrasing
     * so that structurally identical questions are mapped to the same key.
     */
    fun normalizeStemForTemplateDedup(stem: String): String {
        return stem
            .lowercase(java.util.Locale("tr"))
            .let { CITY_PATTERN.replace(it, "CITY") }
            .let { UNIT_PATTERN.replace(it, "UNIT") }
            .let { BOILERPLATE_PATTERN.replace(it, "OP") }
            .replace(Regex("\\d+([.,]\\d+)?"), "#")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Returns a template key for an entity: same key → same structural template. */
    fun templateKey(e: QuestionEntity): String {
        val stem = e.stemNormalized.ifBlank { e.questionText }
        val normalized = normalizeStemForTemplateDedup(stem).take(60)
        return "${e.grade}|${e.subject}|$normalized"
    }

    /**
     * Reject template-duplicate clones from a batch.
     * Keeps the FIRST occurrence of each template key; logs and drops the rest.
     */
    fun deduplicateByTemplate(
        entities: List<QuestionEntity>,
        existingKeys: Set<String> = emptySet(),
    ): Pair<List<QuestionEntity>, Int> {
        val seen = existingKeys.toMutableSet()
        var dropped = 0
        val kept = entities.filter { e ->
            val key = templateKey(e)
            if (key in seen) {
                dropped++
                Log.w(TAG, "[TEMPLATE_DEDUP_REJECTED] grade=${e.grade} subj=${e.subject} key=${key.take(80)}")
                false
            } else {
                seen.add(key)
                true
            }
        }
        if (dropped > 0) Log.w(TAG, "[TEMPLATE_DEDUP] batch dropped $dropped template-duplicate(s)")
        return kept to dropped
    }

    fun applyShellClustering(entities: List<QuestionEntity>): List<QuestionEntity> {
        if (entities.size < MIN_CLUSTER) return entities
        val keyed = entities.groupBy { shellKey(it) }
        return entities.map { e ->
            val group = keyed[shellKey(e)] ?: return@map e
            if (group.size < MIN_CLUSTER) return@map e
            val weak = group.count {
                it.reasoningScore < 45 ||
                    it.qualityTier == QuestionQualityClassifier.TIER_EASY ||
                    it.qualityTier == QuestionQualityClassifier.TIER_BORDERLINE
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
        val shell = normalizeStemForTemplateDedup(stem).take(44)
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
