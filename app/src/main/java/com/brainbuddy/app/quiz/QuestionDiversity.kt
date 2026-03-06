package com.brainbuddy.app.quiz

import java.util.Locale

/**
 * Centralized helpers for question type/skill classification.
 *
 * Used for:
 * - Seeding Room from JSON/synthetic packs
 * - Imported questions
 * - Diversity-aware pickers (via Question.type / Question.skill)
 *
 * Types are intentionally coarse and human-readable so debug text is clear.
 */
object QuestionDiversity {

    fun inferType(subject: Subject, stem: String): String {
        val lower = stem.lowercase(Locale("tr"))
        val length = stem.length

        return when (subject) {
            Subject.MAT -> inferMatType(lower, length)
            Subject.TURKCE -> inferTurkceType(lower, length)
            Subject.FEN -> inferFenType(lower, length)
            Subject.SOSYAL -> inferSosyalType(lower, length)
            Subject.ING -> inferIngType(lower, length)
            Subject.INKILAP -> inferSosyalType(lower, length)
            Subject.DIN -> inferSosyalType(lower, length)
        }
    }

    fun inferSkill(subject: Subject, grade: Int, type: String, stem: String): String {
        // Stable pseudo-bucket per (subject, type, stem) to get multiple distinct skills per type.
        val base = (subject.name + "|" + type + "|" + stem)
        val bucket = (base.hashCode().toLong() and 0x7fffffff).toInt() % 4 // 0..3
        return "${subject.name}_${type}_G${grade}_S$bucket"
    }

    private fun inferMatType(lower: String, length: Int): String {
        return when {
            lower.contains("tablo") || lower.contains("grafik") -> "TABLE_GRAPH"
            lower.contains("yüzde") || lower.contains("%") || lower.contains("oran") -> "RATIO_PERCENT"
            listOf("üçgen", "dikdörtgen", "çember", "yarıçap", "açı", "kenar", "alan", "çevre")
                .any { it in lower } -> "GEOMETRY"
            length >= 160 || listOf("çünkü", "neden", "sonuç", "buna göre").any { it in lower } ->
                "REASONING"
            else -> "PROBLEM"
        }
    }

    private fun inferTurkceType(lower: String, length: Int): String {
        val isParagraph = length >= 200 || listOf("bu parçaya göre", "bu paragrafa göre", "bu metne göre").any { it in lower }
        if (isParagraph) return "PARAGRAPH"

        return when {
            listOf("anlamı", "eş anlamlı", "zıt anlamlı", "deyim", "atasözü").any { it in lower } ->
                "MEANING"
            listOf("bu cümlede", "aşağıdaki cümlelerin hangisinde", "çıkarılamaz", "çıkarılabilir", "anlaşılmaktadır")
                .any { it in lower } -> "LOGIC"
            listOf("noktalama", "yazım", "imla", "büyük harf", "bağlaç olan ki", "ek olan ki")
                .any { it in lower } -> "GRAMMAR_IN_CONTEXT"
            else -> "MEANING"
        }
    }

    private fun inferFenType(lower: String, length: Int): String {
        return when {
            listOf("deney", "düzeneği", "deneyde", "ısıtma işlemi").any { it in lower } ->
                "EXPERIMENT"
            lower.contains("tablo") || lower.contains("grafik") || lower.contains("çizelge") ->
                "TABLE_GRAPH"
            listOf("çünkü", "nedeni", "sebebi", "sonucu", "buna göre").any { it in lower } ->
                "REASONING"
            else -> "CONCEPT_APPLICATION"
        }
    }

    private fun inferSosyalType(lower: String, length: Int): String {
        return when {
            listOf("harita", "haritaya bakarak", "yandaki harita").any { it in lower } ->
                "MAP"
            lower.contains("tablo") || lower.contains("grafik") || lower.contains("çizelge") ->
                "TABLE_GRAPH"
            listOf("neden", "sonuç", "sebep", "çünkü", "bu nedenle").any { it in lower } ->
                "CAUSE_EFFECT"
            else -> "COMMENTARY"
        }
    }

    private fun inferIngType(lower: String, length: Int): String {
        return when {
            // Simple dialogue detection: multiple quotes or speaker markers.
            lower.count { it == '"' } >= 4 ||
                listOf("—", " - ", ":", "said", "asked").any { it in lower } ->
                "DIALOGUE"
            listOf("fill in the blank", "fill in the blanks", "complete the sentence", "______", "___")
                .any { it in lower } -> "CLOZE"
            length >= 180 -> "READING"
            else -> "VOCAB_IN_CONTEXT"
        }
    }
}

