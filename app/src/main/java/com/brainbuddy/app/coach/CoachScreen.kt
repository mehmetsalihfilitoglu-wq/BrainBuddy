package com.brainbuddy.app.coach

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.UserGoalPrefs

class CoachScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coach)

        val analytics = AnalyticsStore(this)
        val goalPrefs = UserGoalPrefs(this)
        val engine = CoachEngine(analytics, goalPrefs)

        val daily = engine.getDailyRecommendation()
        val weekly = engine.getWeeklyPlan()

        findViewById<TextView>(R.id.tvDailyRec).text = daily.text
        findViewById<TextView>(R.id.tvWeeklyPlan).text = weekly.summary

        if (daily.isRemedialSuggestion && daily.topic != null) {
            findViewById<View>(R.id.btnRemedial).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    startActivity(
                        Intent(this@CoachScreen, com.brainbuddy.app.quiz.QuizActivity::class.java)
                            .putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_REMEDIAL, true)
                    )
                    finish()
                }
            }
        } else {
            findViewById<View>(R.id.btnRemedial).visibility = View.GONE
        }

        findViewById<View>(R.id.btnCoachBack).setOnClickListener { finish() }
    }
}
