package com.mioacademy.app.core

import android.content.Context
import com.mioacademy.app.quiz.WrongQuestionPoolStore
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Real-data engagement/insights engine.
 *
 * Every value here is computed from the user's actual recorded activity
 * (AnalyticsStore sessions & test performances, GamificationStore streak/xp,
 * WrongQuestionPoolStore). Nothing is invented: when there is not enough data,
 * the relevant field is null / [hasAnyData] is false and callers must show an
 * honest "yeterli veri yok" empty state instead of a fabricated number.
 *
 * All stores it reads are already scoped to the active study area via
 * [ProfileScopedPrefs], so switching areas yields that area's own insights and
 * areas never mix.
 */
class ProgressInsights(private val context: Context) {

    private val analytics = AnalyticsStore(context)
    private val gam = GamificationStore(context)
    private val wrongPool = WrongQuestionPoolStore(context)

    private val nowMs = System.currentTimeMillis()
    private val weekMs = TimeUnit.DAYS.toMillis(7)
    private val sessions = analytics.getSessions()
    private val performances = analytics.getTestPerformances()

    // ── Weekly stats ──────────────────────────────────────────────────────────

    data class WeeklyStats(
        val questions: Int,
        val questionsPrev: Int,
        val xp: Int,
        val activeDays: Int,
        /** null when fewer than [MIN_QUESTIONS_FOR_ACCURACY] questions this week. */
        val accuracy: Int?,
        val accuracyPrev: Int?
    ) {
        val hasData: Boolean get() = questions > 0
        val questionsDelta: Int get() = questions - questionsPrev
        /** null unless BOTH weeks have enough data to compare honestly. */
        val accuracyDelta: Int? get() =
            if (accuracy != null && accuracyPrev != null) accuracy - accuracyPrev else null
    }

    fun weekly(): WeeklyStats {
        val thisWeek = sessions.filter { it.tsMs >= nowMs - weekMs }
        val prevWeek = sessions.filter { it.tsMs in (nowMs - 2 * weekMs) until (nowMs - weekMs) }
        return WeeklyStats(
            questions = thisWeek.sumOf { it.total },
            questionsPrev = prevWeek.sumOf { it.total },
            xp = thisWeek.sumOf { it.pointsEarned },
            activeDays = thisWeek.map { TimeUnit.MILLISECONDS.toDays(it.tsMs) }.distinct().size,
            accuracy = windowAccuracy(thisWeek),
            accuracyPrev = windowAccuracy(prevWeek)
        )
    }

    private fun windowAccuracy(list: List<QuizSession>): Int? {
        val total = list.sumOf { it.total }
        if (total < MIN_QUESTIONS_FOR_ACCURACY) return null
        val correct = list.sumOf { it.correct }
        return (100.0 * correct / total).roundToInt()
    }

    // ── Streak ──────────────────────────────────────────────────────────────

    data class StreakInfo(
        val days: Int,
        val completedToday: Boolean,
        val daysSinceLastStudy: Int,   // -1 if never studied
        val freezeTokens: Int
    ) {
        /** Studied before but not today → the streak is exposed. */
        val atRisk: Boolean get() = days > 0 && !completedToday
    }

    fun streak(): StreakInfo {
        val lastMs = gam.lastCompletedMs()
        val today = TimeUnit.MILLISECONDS.toDays(nowMs)
        val lastDay = if (lastMs > 0) TimeUnit.MILLISECONDS.toDays(lastMs) else -1
        return StreakInfo(
            days = gam.streakDays(),
            completedToday = lastDay == today,
            daysSinceLastStudy = if (lastDay < 0) -1 else (today - lastDay).toInt(),
            freezeTokens = gam.freezeTokens()
        )
    }

    // ── Topic trend ───────────────────────────────────────────────────────────

    data class TopicTrend(
        val mostImproved: String?,
        val mostImprovedDelta: Int,     // percentage points, > 0
        val strongest: Pair<String, Int>?,  // topic, accuracy%
        val weakest: Pair<String, Int>?
    )

    fun topicTrend(): TopicTrend {
        val thisWeek = aggregateTopics(performances.filter { it.tsMs >= nowMs - weekMs })
        val prevWeek = aggregateTopics(performances.filter { it.tsMs in (nowMs - 2 * weekMs) until (nowMs - weekMs) })

        var bestTopic: String? = null
        var bestDelta = 0
        thisWeek.forEach { (topic, tc) ->
            val prev = prevWeek[topic]
            if (prev != null && tc.total >= MIN_TOPIC_QUESTIONS && prev.total >= MIN_TOPIC_QUESTIONS) {
                val delta = (tc.accuracy - prev.accuracy).roundToInt()
                if (delta > bestDelta) { bestDelta = delta; bestTopic = topic }
            }
        }

        val strongest = analytics.getStrongestTopicsWithCounts(1)
            .firstOrNull { it.second.total >= MIN_TOPIC_QUESTIONS }
            ?.let { it.first to it.second.accuracy.roundToInt() }
        val weakest = analytics.getWeakestTopicsWithCounts(1)
            .firstOrNull { it.second.total >= MIN_TOPIC_QUESTIONS }
            ?.let { it.first to it.second.accuracy.roundToInt() }

        return TopicTrend(bestTopic, bestDelta, strongest, weakest)
    }

    private fun aggregateTopics(perfs: List<TestPerformance>): Map<String, TopicCounts> {
        val agg = HashMap<String, TopicCounts>()
        perfs.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                val cur = agg[topic] ?: TopicCounts(0, 0, 0, 0)
                agg[topic] = TopicCounts(
                    correct = cur.correct + tc.correct,
                    wrong = cur.wrong + tc.wrong,
                    blank = cur.blank + tc.blank,
                    total = cur.total + tc.total
                )
            }
        }
        return agg
    }

    fun wrongPoolCount(): Int = wrongPool.size()

    /** True when there is at least one recorded test — the gate for any analysis. */
    fun hasAnyData(): Boolean = sessions.isNotEmpty() || performances.isNotEmpty()

    // ── Dynamic insight messages (all real-data gated) ─────────────────────────

    enum class Tone { POSITIVE, NEUTRAL, WARNING }
    data class Insight(val text: String, val tone: Tone)

    /**
     * Builds the ordered list of insights that the data actually supports.
     * Empty when there is no data — callers show the honest empty state.
     */
    fun allInsights(): List<Insight> {
        val out = ArrayList<Insight>()
        val w = weekly()
        val s = streak()
        val t = topicTrend()
        val pool = wrongPoolCount()

        // Risk / regression first (supportive tone, never judgmental).
        if (s.daysSinceLastStudy >= 3) {
            out.add(Insight(
                "Son ${s.daysSinceLastStudy} gündür çalışma yapmadın. Bugün kısa bir görevle yeniden başlayabilirsin.",
                Tone.WARNING))
        } else if (s.atRisk) {
            out.add(Insight("Streak riskte. Bugün kısa bir görev yeterli.", Tone.WARNING))
        }
        if (w.questionsPrev > 0 && w.questions < w.questionsPrev) {
            out.add(Insight("Bu hafta çözüm sayın geçen haftaya göre düştü.", Tone.WARNING))
        }

        // Real progress.
        if (t.mostImproved != null && t.mostImprovedDelta > 0) {
            out.add(Insight(
                "Bu hafta ${t.mostImproved} doğruluk oranın %${t.mostImprovedDelta} arttı.",
                Tone.POSITIVE))
        }
        if (w.questions > 0) {
            out.add(Insight("Son 7 günde ${w.questions} soru çözdün.", Tone.NEUTRAL))
        }
        if (s.days > 0 && s.completedToday) {
            out.add(Insight("Bu hafta streak'ini koruyorsun. Bugünkü çalışman tamam.", Tone.POSITIVE))
        }
        if (w.activeDays >= 3) {
            out.add(Insight(
                "Bu hafta ${w.activeDays} gün çalıştın. Bu düzen seni hedef sınavına daha hazır yapıyor.",
                Tone.POSITIVE))
        }
        if (pool > 0) {
            out.add(Insight("Yanlış havuzunda tekrar bekleyen $pool soru var.", Tone.NEUTRAL))
        }
        t.strongest?.let { (topic, acc) ->
            out.add(Insight("Son sorularında en güçlü alanın $topic (%$acc).", Tone.POSITIVE))
        }
        return out
    }

    /** The single most relevant insight for the compact Home slot (null if none). */
    fun homeInsight(): Insight? = allInsights().firstOrNull()

    companion object {
        const val MIN_QUESTIONS_FOR_ACCURACY = 5
        const val MIN_TOPIC_QUESTIONS = 5
    }
}
