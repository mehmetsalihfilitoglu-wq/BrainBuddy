package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.core.DailyAdQuotaStore
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.QuestionMapper
import com.brainbuddy.app.ui.AdLimitReachedActivity
import com.brainbuddy.app.ui.WatchAdToUnlockLastTestActivity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Test detay ekranı: skor, tarih, konu dağılımı, yanlış soru listesi, Tekrar Çöz.
 * Premium: sınırsız replay. Non-premium: RewardedAd + günlük max 3.
 */
class PastTestDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_ID = "test_id"
        const val EXTRA_QUESTION_IDS = "question_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_past_test_detail)

        val testId = intent.getStringExtra(EXTRA_TEST_ID) ?: ""
        val questionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS) ?: arrayListOf()

        if (testId.isBlank() || questionIds.isEmpty()) {
            finish()
            return
        }

        val db = DatabaseProvider.get(this)
        val snapshot = kotlinx.coroutines.runBlocking {
            db.snapshotDao().getSnapshot(testId)
        } ?: run {
            finish()
            return
        }

        val dateStr = SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Date(snapshot.createdAt))
        findViewById<android.widget.TextView>(R.id.tvScore).text = "${snapshot.score}/${snapshot.total}"
        findViewById<android.widget.TextView>(R.id.tvDate).text = dateStr

        val breakdown = snapshot.subjectBreakdownJson
        if (!breakdown.isNullOrBlank()) {
            findViewById<android.widget.TextView>(R.id.tvSubjectBreakdown).apply {
                visibility = View.VISIBLE
                text = breakdown
            }
        }

        val wrongIds = try {
            if (snapshot.wrongQuestionIdsJson.isNullOrBlank()) emptyList()
            else (0 until JSONArray(snapshot.wrongQuestionIdsJson).length()).map { JSONArray(snapshot.wrongQuestionIdsJson).getString(it) }
        } catch (_: Exception) { emptyList() }

        if (wrongIds.isNotEmpty()) {
            val questions = kotlinx.coroutines.runBlocking {
                db.questionDao().getQuestionsByIds(wrongIds).map { QuestionMapper.toQuestion(it) }
            }
            val items = wrongIds.mapNotNull { id ->
                questions.find { it.id == id }?.let { q ->
                    QuizResultActivity.WrongItem(q.stem, "-", q.choices.getOrNull(q.correctIndex) ?: "?", q.hint, showCorrect = true)
                }
            }
            findViewById<View>(R.id.tvWrongLabel).visibility = View.VISIBLE
            findViewById<RecyclerView>(R.id.recyclerWrong).apply {
                visibility = View.VISIBLE
                layoutManager = LinearLayoutManager(this@PastTestDetailActivity)
                adapter = QuizResultActivity.WrongAnswersAdapter(items)
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReplay).setOnClickListener {
            if (questionIds.size < QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                android.widget.Toast.makeText(this, "Bu test tekrar çözülemez.", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val premium = PremiumStore(this).isPremium()
            if (premium) {
                startActivity(Intent(this, QuizActivity::class.java).apply {
                    putExtra(QuizActivity.EXTRA_REPLAY_FROM_LAST_TEST, true)
                    putExtra(QuizActivity.EXTRA_QUIZ_ID, testId)
                    putStringArrayListExtra(QuizActivity.EXTRA_QUESTION_IDS_FOR_REPLAY, ArrayList(questionIds))
                })
                finish()
            } else {
                val quotaStore = DailyAdQuotaStore(this)
                quotaStore.resetIfNewDay()
                if (quotaStore.getRemainingToday() > 0) {
                    startActivity(Intent(this, WatchAdToUnlockLastTestActivity::class.java).apply {
                        putExtra(WatchAdToUnlockLastTestActivity.EXTRA_QUIZ_ID, testId)
                        putStringArrayListExtra(WatchAdToUnlockLastTestActivity.EXTRA_QUESTION_IDS, ArrayList(questionIds))
                    })
                } else {
                    startActivity(Intent(this, AdLimitReachedActivity::class.java))
                }
                finish()
            }
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
    }
}
