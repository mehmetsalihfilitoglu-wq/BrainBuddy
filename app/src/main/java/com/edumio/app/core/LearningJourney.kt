package com.edumio.app.core

import android.content.Context
import com.edumio.app.quiz.WrongQuestionScheduler

/**
 * A student's real milestone timeline for the active study area — a felt sense of
 * "look how far I've come." Every milestone is derived from monotonic real data
 * (questions answered, streaks earned, questions mastered); an unreached one is
 * simply shown as not-yet, never faked.
 */
object LearningJourney {

    data class Milestone(val label: String, val detail: String, val reached: Boolean)

    fun milestones(context: Context): List<Milestone> {
        val analytics = AnalyticsStore(context)
        val total = analytics.getOverallCounts().total
        val testsDone = analytics.getTestPerformances().size
        val streaks = GamificationStore(context).milestoneBadges()
        val mastered = WrongQuestionScheduler(context).masteredTotal

        return listOf(
            Milestone("Çalışmaya başladın", "İlk sorunu çözdün", total >= 1),
            Milestone("İlk 100 soru", "100 soru tamamlandı", total >= 100),
            Milestone("İlk görev serisi", "Sınav formatında ilk testlerin", testsDone >= 2),
            Milestone("7 günlük seri", "Bir hafta boyunca her gün", GamificationStore.MILESTONE_7 in streaks),
            Milestone("500 soru", "500 soru tamamlandı", total >= 500),
            Milestone("1000 soru", "1000 soru tamamlandı", total >= 1000),
            Milestone("İlk ustalaşılan soru", "Bir soruyu kalıcı öğrendin", mastered >= 1),
            Milestone("Bugün", "Yolculuğun devam ediyor", true)
        )
    }
}
