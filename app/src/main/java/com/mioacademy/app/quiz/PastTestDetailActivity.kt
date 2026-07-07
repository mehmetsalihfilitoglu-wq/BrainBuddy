package com.mioacademy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mioacademy.app.R
import com.mioacademy.app.ads.RewardedAdManager
import com.mioacademy.app.core.PremiumStore
import com.mioacademy.app.db.DatabaseProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Test detay ekranı: skor, tarih, konu dağılımı, yanlış soru listesi (kilitli), Tekrar Çöz.
 * - Yanlış sorular varsayılan olarak KİLİTLİ (soru metni görünmez).
 * - Premium: tıklayınca anında aç (reklamsız).
 * - Non-premium: reklam izleyince o soru açılır.
 * - Doğru cevap öğrenci ekranında asla gösterilmez; veli modunda gösterilir.
 */
class PastTestDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TEST_ID = "test_id"
        const val EXTRA_QUESTION_IDS = "question_ids"
    }

    private lateinit var testId: String
    private lateinit var questionIds: ArrayList<String>
    private lateinit var viewModel: PastTestDetailViewModel
    private var pendingUnlockQuestionId: String? = null

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

        viewModel = ViewModelProvider(this)[PastTestDetailViewModel::class.java]
        viewModel.load(testId)
        RewardedAdManager.preload(this)

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
            else (0 until org.json.JSONArray(snapshot.wrongQuestionIdsJson).length()).map {
                org.json.JSONArray(snapshot.wrongQuestionIdsJson).getString(it)
            }
        } catch (_: Exception) { emptyList() }

        if (wrongIds.isNotEmpty()) {
            findViewById<View>(R.id.tvWrongLabel).visibility = View.VISIBLE
            findViewById<RecyclerView>(R.id.recyclerWrong).apply {
                visibility = View.VISIBLE
                layoutManager = LinearLayoutManager(this@PastTestDetailActivity)
            }
            lifecycleScope.launch {
                viewModel.state.collectLatest { state ->
                    (findViewById<RecyclerView>(R.id.recyclerWrong).adapter as? WrongQuestionDetailAdapter)?.updateItems(state.wrongItems)
                        ?: run {
                            findViewById<RecyclerView>(R.id.recyclerWrong).adapter = WrongQuestionDetailAdapter(
                                state.wrongItems,
                                onItemClick = ::onWrongQuestionClick
                            )
                        }
                }
            }
        }

        val layoutViewQuotaGate = findViewById<View>(R.id.layoutViewQuotaGate)
        layoutViewQuotaGate?.visibility = View.GONE

        setupReplayButton(snapshot.total)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun onWrongQuestionClick(item: WrongQuestionUiItem) {
        if (item.isUnlocked) return
        viewModel.unlockWrongAnswer(
            questionId = item.questionId,
            onNeedAd = {
                showRewardedAdToUnlock(item.questionId)
            },
            onUnlocked = { /* UI updates via Flow */ }
        )
    }

    private fun showRewardedAdToUnlock(questionId: String) {
        pendingUnlockQuestionId = questionId
        RewardedAdManager.show(
            activity = this,
            onReward = {
                pendingUnlockQuestionId?.let { viewModel.performUnlockAfterAd(it) }
                pendingUnlockQuestionId = null
            },
            onFail = { msg ->
                val err = RewardedAdManager.lastLoadError?.let { "$msg ($it)" } ?: msg
                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                pendingUnlockQuestionId = null
            }
        )
    }

    private fun setupReplayButton(total: Int) {
        val premium = PremiumStore(this).isPremium()
        val btnReplay = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnReplay)
        val layoutLimitReached = findViewById<View>(R.id.layoutLimitReached)
        val btnPremiumCta = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPremiumCta)

        if (premium) {
            // Premium: replay the exact test freely.
            btnReplay.visibility = View.VISIBLE
            btnReplay.text = getString(R.string.btn_replay)
            layoutLimitReached.visibility = View.GONE
            btnReplay.setOnClickListener {
                startActivity(Intent(this, QuizActivity::class.java).apply {
                    putExtra(QuizActivity.EXTRA_REPLAY_FROM_LAST_TEST, true)
                    putExtra(QuizActivity.EXTRA_QUIZ_ID, testId)
                    putStringArrayListExtra(QuizActivity.EXTRA_QUESTION_IDS_FOR_REPLAY, questionIds)
                })
                finish()
            }
        } else {
            // Free: replaying an exact past test is a Premium feature (no ads).
            btnReplay.visibility = View.GONE
            layoutLimitReached.visibility = View.VISIBLE
            findViewById<android.widget.TextView>(R.id.tvLimitReached).text =
                getString(R.string.replay_premium_hint)
            btnPremiumCta.setOnClickListener {
                PremiumPaywallSheet().show(supportFragmentManager, PremiumPaywallSheet.TAG)
            }
        }
    }
}

private class WrongQuestionDetailAdapter(
    private var items: List<WrongQuestionUiItem>,
    private val onItemClick: (WrongQuestionUiItem) -> Unit
) : RecyclerView.Adapter<WrongQuestionDetailAdapter.VH>() {

    fun updateItems(newItems: List<WrongQuestionUiItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_wrong_question_detail, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.view.context

        val layoutLocked = holder.view.findViewById<View>(R.id.layoutLocked)
        val layoutUnlocked = holder.view.findViewById<View>(R.id.layoutUnlocked)

        if (item.isUnlocked) {
            layoutLocked.visibility = View.GONE
            layoutUnlocked.visibility = View.VISIBLE
            holder.view.findViewById<android.widget.TextView>(R.id.tvQuestion).text = item.question?.stem ?: "-"
            holder.view.findViewById<android.widget.TextView>(R.id.tvUserChoice).apply {
                visibility = View.VISIBLE
                text = "Senin cevabın: ${item.userChoiceText}\n❌ Yanlış"
            }
            holder.view.findViewById<android.widget.TextView>(R.id.tvCorrect).apply {
                visibility = if (item.showCorrect) View.VISIBLE else View.GONE
                text = "✓ Doğru: ${item.correctAnswerText}"
            }
            holder.view.findViewById<android.widget.TextView>(R.id.tvHint).apply {
                visibility = if (item.showCorrect && !item.hint.isNullOrBlank()) View.VISIBLE else View.GONE
                text = "💡 ${item.hint}"
            }
            holder.view.isClickable = false
        } else {
            layoutLocked.visibility = View.VISIBLE
            layoutUnlocked.visibility = View.GONE
            holder.view.isClickable = true
            holder.view.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount() = items.size
}
