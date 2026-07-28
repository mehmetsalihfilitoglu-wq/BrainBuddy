package com.edumio.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.edumio.app.R
import com.edumio.app.core.AnalyticsStore
import com.edumio.app.core.CareerPath
import com.edumio.app.core.UserGoalPrefs
import com.edumio.app.quiz.QuizActivity
import com.edumio.app.quiz.WrongPoolLauncher
import com.edumio.app.quiz.WrongQuestionPoolStore
import kotlin.math.roundToInt

class StudyHubActivity : AppCompatActivity() {

    private lateinit var analytics: AnalyticsStore
    private lateinit var goalPrefs: UserGoalPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_hub)

        analytics = AnalyticsStore(this)
        goalPrefs = UserGoalPrefs(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        // v1 has exactly one new-question action — the Daily Challenge. The generic "Pratik Yap" here
        // started a separate quiz session, so it is removed. The Study Coach is not part of v1 either.
        findViewById<MaterialButton>(R.id.btnPracticeAll).visibility = View.GONE
        // V1 focus: university discovery + info/tools live on web (Instagram/blog/ISEEmio),
        // not in the exam-prep app. DiscoverActivity / ToolsActivity are kept in the codebase
        // for a future major version; only their in-app entry points are removed.

        val content = findViewById<View>(R.id.scrollContent)
        val origBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origBottom + navBottom)
            insets
        }

        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        refreshWrongPool()
    }

    private fun refreshAll() {
        refreshExamContext()
        refreshStats()
        refreshWrongPool()
    }

    private fun refreshExamContext() {
        // v1 is exam-framed: show ONLY the active exam (IMAT / TIL-I / CEnT-S). Never the career name
        // ("Mühendislik") or the Italian degree/exam name — consistent with Home and Profile.
        val exam = goalPrefs.getGoal().careerPath.examType
        findViewById<TextView>(R.id.tvCareerLabel).text = "🇮🇹 ${exam.code}"
        findViewById<TextView>(R.id.tvExamLabel).visibility = android.view.View.GONE
    }

    /** The learning-statistics card has exactly two states — never a placeholder. */
    internal enum class StatsState { EMPTY, LOADED }

    private fun refreshStats() {
        val counts = analytics.getOverallCounts()
        val testCount = analytics.getTestPerformances().size
        val accuracy = analytics.getOverallAccuracy()

        val row = findViewById<View>(R.id.statsRow)
        val empty = findViewById<View>(R.id.statsEmpty)

        when (statsState(counts.total)) {
            StatsState.LOADED -> {
                row.visibility = View.VISIBLE
                empty.visibility = View.GONE
                findViewById<TextView>(R.id.tvStatsAccuracy).text = "%${accuracy.roundToInt()}"
                findViewById<TextView>(R.id.tvStatsTests).text = "$testCount"
                findViewById<TextView>(R.id.tvStatsQuestions).text = "${counts.total}"
            }
            // No dashes, no fabricated zeros: the card explains itself and offers the one action that
            // can produce data. Purely data-driven, so it disappears on its own once stats exist.
            StatsState.EMPTY -> {
                row.visibility = View.GONE
                empty.visibility = View.VISIBLE
                findViewById<View>(R.id.btnStatsEmptyCta).setOnClickListener {
                    startActivity(
                        Intent(this, com.edumio.app.dailychallenge.DailyChallengeActivity::class.java)
                    )
                }
            }
        }
    }

    internal companion object {
        /**
         * Pure state decision, unit-testable without a device. The threshold is deliberately "any real
         * answered question" rather than a hardcoded test count — the card must light up as soon as
         * there is something true to show.
         */
        internal fun statsState(answeredQuestions: Int): StatsState =
            if (answeredQuestions > 0) StatsState.LOADED else StatsState.EMPTY
    }


    private fun refreshWrongPool() {
        val card = findViewById<MaterialCardView>(R.id.cardWrongPool)
        val pool = WrongQuestionPoolStore(this)
        card.visibility = if (pool.isNotEmpty()) View.VISIBLE else View.GONE
        if (pool.isNotEmpty()) {
            card.setOnClickListener { WrongPoolLauncher.launch(this) }
        }
    }

}
