package com.mioacademy.app.core

import android.content.Context
import com.mioacademy.app.quiz.WrongQuestionScheduler

/**
 * Builds today's ordered study plan so a premium student never wonders what to
 * do next — the coach lays out the day: today's mini-exam, then priority spaced
 * review, then targeted weak-topic practice. Ordered by learning value, derived
 * only from real, active-area-scoped activity.
 */
class StudyPlanEngine(private val context: Context) {

    data class Step(val index: Int, val title: String, val detail: String, val done: Boolean)

    fun today(): List<Step> {
        val mission = DailyMissionManager(context).getTodayMission()
        val missionDone = mission.testsDone >= mission.testsTarget
        val scheduler = WrongQuestionScheduler(context)
        val due = scheduler.dueCount()
        val pending = scheduler.reviewQueueSize()
        val weakest = AnalyticsStore(context).getWeakestTopicsWithCounts(1)
            .firstOrNull { it.second.total >= ProgressInsights.MIN_TOPIC_QUESTIONS }?.first

        val steps = ArrayList<Step>()
        var i = 1
        steps.add(Step(i++, "Günlük Mini Sınav",
            if (missionDone) "Tamamlandı" else "Sınav formatında bugünkü görev", missionDone))

        if (pending > 0) {
            steps.add(Step(i++, "Öncelikli Tekrar",
                if (due > 0) "$due soru tekrara hazır" else "Tekrar kuyruğun güncel", due == 0))
        }
        if (weakest != null) {
            steps.add(Step(i++, "Zayıf Konu Pratiği", "$weakest üzerine kısa çalışma", false))
        }
        return steps
    }
}
