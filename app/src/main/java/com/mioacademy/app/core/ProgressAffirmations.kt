package com.mioacademy.app.core

import android.content.Context
import com.mioacademy.app.quiz.WrongQuestionScheduler
import java.util.concurrent.TimeUnit

/**
 * "How far you've come" — honest, long-horizon progress statements for the active
 * study area. The emotional counterweight to daily numbers: opening the app should
 * feel like "I'm making progress", never "I'm behind".
 *
 * Every line is derived from real stored data (month-over-month volume, mastered
 * questions, consistency, review corrections). Nothing is fabricated; when there is
 * not enough history the list is empty and callers simply show nothing.
 */
class ProgressAffirmations(context: Context) {

    private val analytics = AnalyticsStore(context)
    private val scheduler = WrongQuestionScheduler(context)
    private val sessions = analytics.getSessions()
    private val now = System.currentTimeMillis()
    private val monthMs = TimeUnit.DAYS.toMillis(30)

    /** Real progress statements, strongest first. Empty when history is too thin. */
    fun statements(): List<String> {
        val out = ArrayList<String>()

        val thisMonth = sessions.filter { it.tsMs >= now - monthMs }
        val lastMonth = sessions.filter { it.tsMs in (now - 2 * monthMs) until (now - monthMs) }
        val qThis = thisMonth.sumOf { it.total }
        val qLast = lastMonth.sumOf { it.total }
        if (qLast > 0 && qThis > qLast) {
            out.add("Bu ay geçen aya göre ${qThis - qLast} soru daha çözdün.")
        }

        val mastered = scheduler.masteredTotal
        if (mastered >= 5) {
            out.add("Bir zamanlar seni zorlayan $mastered soruyu artık biliyorsun.")
        }

        val activeThis = thisMonth.map { day(it.tsMs) }.distinct().size
        val activeLast = lastMonth.map { day(it.tsMs) }.distinct().size
        if (activeLast > 0 && activeThis > activeLast) {
            out.add("Bu ay $activeThis gün çalıştın — geçen aydan daha istikrarlısın.")
        }

        val corrections = analytics.getTotalReviewCorrections()
        if (corrections >= 5) {
            out.add("Yanlışlarını tekrar ederek $corrections kez düzelttin.")
        }

        if (out.isEmpty()) {
            val total = analytics.getOverallCounts().total
            if (total >= 50) {
                out.add("Bugüne kadar toplam $total soru çözdün. Her biri seni hedefine yaklaştırdı.")
            }
        }
        return out
    }

    /** The single strongest statement, or null when there isn't enough history yet. */
    fun headline(): String? = statements().firstOrNull()

    private fun day(ms: Long) = TimeUnit.MILLISECONDS.toDays(ms)
}
