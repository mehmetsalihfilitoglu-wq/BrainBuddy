package com.edumio.app.coach

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.R
import com.edumio.app.core.AnalyticsStore
import com.edumio.app.core.UserGoalPrefs

class CoachScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coach)

        val analytics = AnalyticsStore(this)
        val goalPrefs = UserGoalPrefs(this)
        val insights = com.edumio.app.core.ProgressInsights(this)
        val engine = CoachEngine(
            analytics, goalPrefs,
            daysSinceLastStudy = insights.streak().daysSinceLastStudy,
            wrongPoolCount = insights.wrongPoolCount()
        )

        val daily = engine.getDailyRecommendation()
        val weekly = engine.getWeeklyPlan()

        findViewById<TextView>(R.id.tvDailyRec).text = daily.text
        findViewById<TextView>(R.id.tvWeeklyPlan).text = weekly.summary

        // v1's only new-question action is the Daily Challenge, so the coach no longer starts a separate
        // remedial quiz — it stays advice-only. The remedial button is always hidden.
        findViewById<View>(R.id.btnRemedial).visibility = View.GONE

        findViewById<View>(R.id.btnCoachBack).setOnClickListener { finish() }
    }
}
