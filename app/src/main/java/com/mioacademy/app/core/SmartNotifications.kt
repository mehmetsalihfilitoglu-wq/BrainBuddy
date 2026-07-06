package com.mioacademy.app.core

import android.content.Context
import com.mioacademy.app.R
import com.mioacademy.app.quiz.WrongQuestionScheduler

/**
 * Chooses the single most relevant, real-data-specific nudge for the active study
 * area — never a generic "come back" ping. It reflects what the student actually
 * needs right now: due reviews, a streak one session from growing, or their real
 * weakest topic. Falls back to the plain reminder only when there's nothing
 * specific and honest to say.
 */
object SmartNotifications {

    data class Message(val title: String, val text: String)

    /** The daily "keep going" nudge, personalised from real activity. */
    fun dailyReminder(context: Context): Message {
        val scheduler = WrongQuestionScheduler(context)
        val due = scheduler.dueCount()
        if (due > 0) {
            return Message(
                context.getString(R.string.notif_smart_review_title),
                context.getString(R.string.notif_smart_review_text, due)
            )
        }

        val streak = GamificationStore(context).streakDays()
        if (streak in intArrayOf(6, 29, 99)) {
            return Message(
                context.getString(R.string.notif_smart_streak_title),
                context.getString(R.string.notif_smart_streak_text, streak + 1)
            )
        }

        val weakest = AnalyticsStore(context).getWeakestTopicsWithCounts(1)
            .firstOrNull { it.second.total >= ProgressInsights.MIN_TOPIC_QUESTIONS }?.first
        if (weakest != null) {
            return Message(
                context.getString(R.string.notif_smart_weak_title),
                context.getString(R.string.notif_smart_weak_text, weakest)
            )
        }

        return Message(
            context.getString(R.string.notif_daily_reminder_title),
            context.getString(R.string.notif_daily_reminder_text)
        )
    }
}
