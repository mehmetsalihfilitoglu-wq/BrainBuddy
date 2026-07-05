package com.brainbuddy.app.coach

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
class CoachScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coach)

        val analytics = AnalyticsStore(this)
        val engine = CoachEngine(analytics)

        val daily = engine.getDailyRecommendation()
        val weekly = engine.getWeeklyPlan()

        findViewById<android.widget.TextView>(R.id.tvCoachTitle).text = "Koç Önerisi"
        findViewById<android.widget.TextView>(R.id.tvDailyRec).text = daily.text
        findViewById<android.widget.TextView>(R.id.tvWeeklyPlan).text = weekly.summary

        if (daily.isRemedialSuggestion && daily.topic != null) {
            findViewById<android.widget.Button>(R.id.btnRemedial).apply {
                visibility = android.view.View.VISIBLE
                text = "Mini Tekrar Testi"
                setOnClickListener {
                    startActivity(Intent(this@CoachScreen, com.brainbuddy.app.quiz.QuizActivity::class.java)
                        .putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_REMEDIAL, true))
                    finish()
                }
            }
        } else {
            findViewById<android.widget.Button>(R.id.btnRemedial).visibility = android.view.View.GONE
        }

        findViewById<android.widget.Button>(R.id.btnCoachBack).setOnClickListener { finish() }
    }
}
