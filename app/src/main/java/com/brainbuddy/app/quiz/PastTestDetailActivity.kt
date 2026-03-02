package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.DailyAdQuotaStore
import com.brainbuddy.app.core.DailyParentViewQuotaStore
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.QuestionMapper
import com.brainbuddy.app.ui.AdLimitReachedActivity
import com.brainbuddy.app.ui.WatchAdForParentViewActivity
import com.brainbuddy.app.ui.WatchAdToUnlockLastTestActivity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Test detay ekranı: skor, tarih, konu dağılımı, yanlış soru listesi, Tekrar Çöz.
 * - Öğrenci doğru cevabı asla görmez.
 * - Veli: doğru cevap görme kotası (Premium: sınırsız, değilse günde 3 ücretsiz + reklam = +1).
 * - Replay: Premium sınırsız, değilse günlük max 3 reklam.
 */
class PastTestDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_ID = "test_id"
        const val EXTRA_QUESTION_IDS = "question_ids"
    }

    private lateinit var testId: String
    private lateinit var questionIds: ArrayList<String>
    private var wrongIds: List<String> = emptyList()
    private var questionsForWrong: List<Question> = emptyList()

    private val adForViewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshWrongSection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_past_test_detail)

        testId = intent.getStringExtra(EXTRA_TEST_ID) ?: ""
        questionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS) ?: arrayListOf()

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

        wrongIds = try {
            if (snapshot.wrongQuestionIdsJson.isNullOrBlank()) emptyList()
            else (0 until JSONArray(snapshot.wrongQuestionIdsJson).length()).map {
                JSONArray(snapshot.wrongQuestionIdsJson).getString(it)
            }
        } catch (_: Exception) { emptyList() }

        if (wrongIds.isNotEmpty()) {
            questionsForWrong = kotlinx.coroutines.runBlocking {
                db.questionDao().getQuestionsByIds(wrongIds).map { QuestionMapper.toQuestion(it) }
            }
            renderWrongSection()
        }

        setupReplayButton(snapshot.total)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun renderWrongSection() {
        val showCorrect = canShowCorrectToParent()
        val items = wrongIds.mapNotNull { id ->
            questionsForWrong.find { it.id == id }?.let { q ->
                QuizResultActivity.WrongItem(
                    q.stem, "-",
                    q.choices.getOrNull(q.correctIndex) ?: "?",
                    q.hint,
                    showCorrect = showCorrect
                )
            }
        }
        findViewById<View>(R.id.tvWrongLabel).visibility = View.VISIBLE
        findViewById<RecyclerView>(R.id.recyclerWrong).apply {
            visibility = View.VISIBLE
            layoutManager = LinearLayoutManager(this@PastTestDetailActivity)
            adapter = QuizResultActivity.WrongAnswersAdapter(items)
        }

        if (showCorrect) {
            DailyParentViewQuotaStore(this).consumeOne()
        }

        val parentViewQuota = DailyParentViewQuotaStore(this)
        val layoutViewQuotaGate = findViewById<View>(R.id.layoutViewQuotaGate)
        val isParentNoQuota = AppModeManager.isParentMode() && !PremiumStore(this).isPremium() && !parentViewQuota.canView()
        if (layoutViewQuotaGate != null) {
            if (isParentNoQuota && !showCorrect) {
                layoutViewQuotaGate.visibility = View.VISIBLE
                layoutViewQuotaGate.findViewById<android.widget.Button>(R.id.btnWatchAdForView).setOnClickListener {
                    adForViewLauncher.launch(Intent(this, WatchAdForParentViewActivity::class.java))
                }
            } else {
                layoutViewQuotaGate.visibility = View.GONE
            }
        }
    }

    private fun refreshWrongSection() {
        if (wrongIds.isEmpty()) return
        renderWrongSection()
    }

    private fun canShowCorrectToParent(): Boolean {
        if (!AppModeManager.isParentMode()) return false
        if (PremiumStore(this).isPremium()) return true
        return DailyParentViewQuotaStore(this).canView()
    }

    private fun setupReplayButton(total: Int) {
        val premium = PremiumStore(this).isPremium()
        val quotaStore = DailyAdQuotaStore(this)
        quotaStore.resetIfNewDay()
        val remaining = quotaStore.getRemainingToday()

        val btnReplay = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReplay)
        val layoutLimitReached = findViewById<View>(R.id.layoutLimitReached)
        val btnPremiumCta = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPremiumCta)

        if (premium) {
            btnReplay.visibility = View.VISIBLE
            btnReplay.text = getString(R.string.btn_replay)
            layoutLimitReached.visibility = View.GONE
        } else if (remaining > 0) {
            btnReplay.visibility = View.VISIBLE
            btnReplay.text = getString(R.string.replay_with_ad_remaining, remaining)
            layoutLimitReached.visibility = View.GONE
        } else {
            btnReplay.visibility = View.GONE
            layoutLimitReached.visibility = View.VISIBLE
            findViewById<android.widget.TextView>(R.id.tvLimitReached).text = getString(R.string.ad_limit_reached_message)
            btnPremiumCta.setOnClickListener {
                android.widget.Toast.makeText(this, "Premium yakında", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        btnReplay.setOnClickListener {
            if (premium) {
                startActivity(Intent(this, QuizActivity::class.java).apply {
                    putExtra(QuizActivity.EXTRA_REPLAY_FROM_LAST_TEST, true)
                    putExtra(QuizActivity.EXTRA_QUIZ_ID, testId)
                    putStringArrayListExtra(QuizActivity.EXTRA_QUESTION_IDS_FOR_REPLAY, questionIds)
                })
            } else {
                if (remaining > 0) {
                    startActivity(Intent(this, WatchAdToUnlockLastTestActivity::class.java).apply {
                        putExtra(WatchAdToUnlockLastTestActivity.EXTRA_QUIZ_ID, testId)
                        putStringArrayListExtra(WatchAdToUnlockLastTestActivity.EXTRA_QUESTION_IDS, questionIds)
                    })
                } else {
                    startActivity(Intent(this, AdLimitReachedActivity::class.java))
                }
            }
            finish()
        }
    }
}
