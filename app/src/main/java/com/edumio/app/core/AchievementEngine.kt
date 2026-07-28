package com.edumio.app.core

import android.content.Context

/**
 * Real-data achievement / milestone system.
 *
 * Every achievement's unlocked state and progress is derived from the user's
 * actual recorded activity (streak, total questions solved, review corrections,
 * per-subject accuracy, weekly consistency, XP). Nothing unlocks without the
 * data to back it. All source stores are active-area scoped, so achievements are
 * per study area and never mix.
 *
 * Newly-unlocked detection is persisted (per area) so a milestone is celebrated
 * exactly once — e.g. on the quiz result screen.
 */
class AchievementEngine(private val context: Context) {

    private val gam = GamificationStore(context)
    private val analytics = AnalyticsStore(context)
    private val insights = ProgressInsights(context)
    // Reuse the area-scoped gamification prefs for the "already celebrated" set.
    private val prefs = ProfileScopedPrefs.gamification(context)

    data class Achievement(
        val id: String,
        val title: String,
        val description: String,
        val icon: String,
        val unlocked: Boolean,
        val current: Int,
        val target: Int
    ) {
        val progress: Float
            get() = when {
                unlocked -> 1f
                target > 0 -> (current.toFloat() / target).coerceIn(0f, 1f)
                else -> 0f
            }
    }

    /** All achievements with live, real-data state. Ordered by definition. */
    fun all(): List<Achievement> {
        val streak = gam.streakDays()
        val totalQuestions = analytics.getOverallCounts().total
        val reviewCorrections = analytics.getTotalReviewCorrections()
        val xp = gam.xp()
        val activeDaysThisWeek = insights.weekly().activeDays
        val bestSubjectAcc = analytics.getStrongestTopicsWithCounts(1)
            .firstOrNull { it.second.total >= ProgressInsights.MIN_TOPIC_QUESTIONS }
            ?.second?.accuracy?.toInt() ?: 0

        return listOf(
            milestone("streak_3", "🔥", "3 Gün Seri", "3 gün üst üste çalış", streak, 3),
            milestone("streak_7", "🔥", "7 Gün Seri", "7 gün üst üste çalış", streak, 7),
            milestone("q_50", "✏️", "50 Soru", "Toplam 50 soru çöz", totalQuestions, 50),
            milestone("q_100", "📚", "100 Soru", "Toplam 100 soru çöz", totalQuestions, 100),
            milestone("review_10", "🔁", "İlk Tekrarlar", "10 yanlışını tekrar edip düzelt", reviewCorrections, 10),
            milestone("subject_70", "🎯", "Ustalaşma", "Bir derste %70 başarıyı geç", bestSubjectAcc, 70),
            milestone("weekly_5", "🗓️", "Haftalık Hedef", "Bir haftada 5 gün çalış", activeDaysThisWeek, 5),
            milestone("xp_500", "⭐", "500 XP", "Aktif alanında 500 XP topla", xp, 500)
        )
    }

    private fun milestone(id: String, icon: String, title: String, desc: String, current: Int, target: Int) =
        Achievement(id, title, desc, icon, unlocked = current >= target, current = current.coerceAtMost(target), target = target)

    fun unlocked(): List<Achievement> = all().filter { it.unlocked }

    /** The closest not-yet-unlocked achievement (highest real progress), or null if all done. */
    fun nextMilestone(): Achievement? =
        all().filter { !it.unlocked }.maxByOrNull { it.progress }

    /**
     * Returns achievements that are unlocked now but have not been celebrated
     * yet, and marks them celebrated. Call once from a result/summary screen.
     */
    fun consumeNewlyUnlocked(): List<Achievement> {
        val celebrated = prefs.getStringSet(KEY_CELEBRATED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val fresh = unlocked().filter { it.id !in celebrated }
        if (fresh.isNotEmpty()) {
            celebrated.addAll(fresh.map { it.id })
            prefs.edit().putStringSet(KEY_CELEBRATED, HashSet(celebrated)).apply()
        }
        return fresh
    }

    companion object {
        private const val KEY_CELEBRATED = "celebrated_achievements"
    }
}
