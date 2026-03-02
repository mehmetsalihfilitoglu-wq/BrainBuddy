package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.quiz.QuestionRepository
import com.brainbuddy.app.core.DailyAdQuotaStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.TestPerformance
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.ui.AdLimitReachedActivity
import com.brainbuddy.app.ui.WatchAdToUnlockLastTestActivity
import com.brainbuddy.app.databinding.ActivityStatsBinding
import com.brainbuddy.app.ui.BarChartView
import com.brainbuddy.app.ui.LineChartView
import com.brainbuddy.app.ui.ProgressRingView
import com.google.android.material.chip.Chip

class StatsActivity : AppCompatActivity() {

    private lateinit var b: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        b = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)

        b.tvLevel.text = "Seviye ${gam.level()}"
        b.tvXp.text = "${gam.xp()} XP"
        b.tvStreak.text = "🔥 ${gam.streakDays()} gün seri"
        val counts = analytics.getOverallCounts()
        val accuracyPct = if (counts.total > 0) 100.0 * counts.correct / counts.total else 0.0
        b.tvOverallAccuracy.text = if (counts.total > 0) {
            "${counts.correct}/${counts.total} doğru (${"%.1f".format(accuracyPct)}%)"
        } else "Henüz veri yok"

        b.progressRing.progress = accuracyPct.toFloat()

        val topicCounts = analytics.getTopicMasteryWithCounts()
        val barData = topicCounts.map { (topic, tc) ->
            BarChartView.BarData(topic, tc.correct, tc.total)
        }.take(6)
        b.barChart.data = barData

        val repo = QuestionRepository(this)
        val snapshots = repo.getLastSnapshots(limit = 10)
        val recent = snapshots.map { s ->
            val qIds = try { (0 until org.json.JSONArray(s.questionIdsJson).length()).map { org.json.JSONArray(s.questionIdsJson).getString(it) } } catch (_: Exception) { emptyList() }
            TestPerformance(
                quizId = s.testId,
                tsMs = s.createdAt,
                accuracy = if (s.total > 0) 100f * s.score / s.total else 0f,
                correctCount = s.score,
                wrongCount = s.total - s.score,
                blankCount = 0,
                totalQuestions = s.total,
                passed = s.score >= (s.total * 0.6).toInt(),
                wrongQuestionIds = try { (0 until org.json.JSONArray(s.wrongQuestionIdsJson ?: "[]").length()).map { org.json.JSONArray(s.wrongQuestionIdsJson).getString(it) } } catch (_: Exception) { emptyList() },
                questionIds = qIds
            )
        }
        val trendValues = recent.map { it.accuracy }
        b.lineChart.values = trendValues

        val strongest = analytics.getStrongestTopicsWithCounts(3)
        b.chipGroupStrong.removeAllViews()
        if (strongest.isEmpty()) {
            val chip = Chip(this, null, com.brainbuddy.app.R.style.Widget_BrainBuddy_Chip_Stat).apply {
                text = "-"
                isClickable = false
            }
            b.chipGroupStrong.addView(chip)
        } else {
            strongest.forEach { (topic, tc) ->
                val chip = Chip(this, null, com.brainbuddy.app.R.style.Widget_BrainBuddy_Chip_Stat).apply {
                    text = "$topic ${tc.correct}/${tc.total}"
                    isClickable = false
                }
                b.chipGroupStrong.addView(chip)
            }
        }

        val weakest = analytics.getWeakestTopicsWithCounts(3)
        b.chipGroupWeak.removeAllViews()
        if (weakest.isEmpty()) {
            val chip = Chip(this, null, com.brainbuddy.app.R.style.Widget_BrainBuddy_Chip_Stat).apply {
                text = "-"
                isClickable = false
            }
            b.chipGroupWeak.addView(chip)
        } else {
            weakest.forEach { (topic, tc) ->
                val chip = Chip(this, null, com.brainbuddy.app.R.style.Widget_BrainBuddy_Chip_Stat).apply {
                    text = "$topic ${tc.correct}/${tc.total}"
                    isClickable = false
                }
                b.chipGroupWeak.addView(chip)
            }
        }

        val recentList = recent.takeLast(5).reversed()
        b.recyclerRecentTests.layoutManager = LinearLayoutManager(this)
        b.recyclerRecentTests.adapter = RecentTestsAdapter(recentList) { perf ->
            startActivity(Intent(this, com.brainbuddy.app.quiz.PastTestDetailActivity::class.java).apply {
                putExtra(com.brainbuddy.app.quiz.PastTestDetailActivity.EXTRA_TEST_ID, perf.quizId)
                putStringArrayListExtra(com.brainbuddy.app.quiz.PastTestDetailActivity.EXTRA_QUESTION_IDS, ArrayList(perf.questionIds))
            })
        }

        b.btnBack.setOnClickListener { finish() }
    }
}

class RecentTestsAdapter(
    private val items: List<TestPerformance>,
    private val onItemClick: (TestPerformance) -> Unit
) : RecyclerView.Adapter<RecentTestsAdapter.VH>() {
    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_stats_test_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.view.findViewById<TextView>(R.id.tvTestIndex).text = "Test ${items.size - position}"
        holder.view.findViewById<TextView>(R.id.tvTestScore).text = "${p.correctCount}/${p.effectiveTotal}"
        holder.view.setOnClickListener { onItemClick(p) }
    }

    override fun getItemCount() = items.size
}
