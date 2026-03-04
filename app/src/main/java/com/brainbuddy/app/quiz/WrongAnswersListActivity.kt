package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.ads.RewardedAdManager
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.WrongReviewAnalytics
import com.brainbuddy.app.core.WrongReviewQuotaStore
import com.brainbuddy.app.databinding.ActivityWrongAnswersListBinding
import com.brainbuddy.app.ui.TestSettingsActivity
import com.google.android.material.card.MaterialCardView

/**
 * Parent-only screen showing wrong answered questions as a collapsible list.
 * Quota is consumed when a wrong question's details are REVEALED (expanded), not when opening.
 * - Premium: unlimited, no ads
 * - Free: 3/day + 1 per rewarded ad
 */
class WrongAnswersListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_SESSION_JSON = "session_json"
        private const val STATE_REVEALED_IDS = "revealed_ids"
        private const val STATE_EXPANDED_IDS = "expanded_ids"
    }

    private lateinit var b: ActivityWrongAnswersListBinding
    private lateinit var quotaStore: WrongReviewQuotaStore
    private lateinit var premiumStore: PremiumStore
    private var questions: List<Question> = emptyList()
    private var sessionAnswers: Map<String, Int> = emptyMap()
    private val revealedIds = mutableSetOf<String>()
    private val expandedIds = mutableSetOf<String>()
    private var pendingExpandQuestionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this, WrongAnswersListActivity::class.java)) return

        b = ActivityWrongAnswersListBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        quotaStore = WrongReviewQuotaStore(this)
        premiumStore = PremiumStore(this)
        quotaStore.ensureDailyReset()
        RewardedAdManager.preload(this)

        val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS) ?: arrayListOf()
        val sessionJson = intent.getStringExtra(EXTRA_SESSION_JSON)
        val session = QuizResultActivity.decodeSession(sessionJson)
        sessionAnswers = session?.answers ?: emptyMap()

        val allMap = QuestionRepository(this).loadAllQuestions().associateBy { it.id }
        questions = wrongIds.mapNotNull { allMap[it] }

        savedInstanceState?.getStringArrayList(STATE_REVEALED_IDS)?.let { ids ->
            revealedIds.clear()
            revealedIds.addAll(ids)
        }
        savedInstanceState?.getStringArrayList(STATE_EXPANDED_IDS)?.let { ids ->
            expandedIds.clear()
            expandedIds.addAll(ids)
        }

        if (questions.isEmpty()) {
            b.recyclerWrongAnswers.visibility = View.GONE
            b.emptyState.visibility = View.VISIBLE
            b.chipQuota.visibility = View.GONE
        } else {
            WrongReviewAnalytics.logOpen()
            b.recyclerWrongAnswers.visibility = View.VISIBLE
            b.emptyState.visibility = View.GONE
            b.recyclerWrongAnswers.layoutManager = LinearLayoutManager(this)
            b.recyclerWrongAnswers.adapter = WrongAnswerAdapter(
                questions = questions,
                sessionAnswers = sessionAnswers,
                revealedIds = revealedIds,
                expandedIds = expandedIds,
                onRevealRequest = { questionId -> onRevealRequest(questionId) }
            )
            updateQuotaChip()
        }
    }

    override fun onResume() {
        super.onResume()
        updateQuotaChip()
        (b.recyclerWrongAnswers.adapter as? WrongAnswerAdapter)?.notifyDataSetChanged()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_REVEALED_IDS, ArrayList(revealedIds))
        outState.putStringArrayList(STATE_EXPANDED_IDS, ArrayList(expandedIds))
    }

    private fun updateQuotaChip() {
        b.chipQuota.visibility = View.VISIBLE
        b.chipQuota.text = if (premiumStore.isPremium()) {
            getString(R.string.wrong_review_unlimited)
        } else {
            getString(R.string.wrong_review_remaining, quotaStore.getRemaining())
        }
    }

    private fun onRevealRequest(questionId: String) {
        if (revealedIds.contains(questionId)) {
            if (expandedIds.contains(questionId)) {
                expandedIds.remove(questionId)
            } else {
                expandedIds.add(questionId)
            }
            toggleExpand(questionId)
            return
        }

        questions.find { it.id == questionId } ?: return

        if (premiumStore.isPremium()) {
            revealAndExpand(questionId)
            return
        }

        quotaStore.ensureDailyReset()
        val remaining = quotaStore.getRemaining()

        if (remaining > 0) {
            if (quotaStore.consumeOne()) {
                WrongReviewAnalytics.logItemReveal()
                revealAndExpand(questionId)
                updateQuotaChip()
            }
        } else {
            WrongReviewAnalytics.logLimitHit()
            showLimitModal(questionId)
        }
    }

    private fun revealAndExpand(questionId: String) {
        revealedIds.add(questionId)
        expandedIds.add(questionId)
        (b.recyclerWrongAnswers.adapter as? WrongAnswerAdapter)?.notifyItemChanged(
            questions.indexOfFirst { it.id == questionId }
        )
        updateQuotaChip()
    }

    private fun toggleExpand(questionId: String) {
        (b.recyclerWrongAnswers.adapter as? WrongAnswerAdapter)?.notifyItemChanged(
            questions.indexOfFirst { it.id == questionId }
        )
    }

    private fun showLimitModal(pendingQuestionId: String) {
        pendingExpandQuestionId = pendingQuestionId
        WrongReviewAnalytics.logPaywallOpened()

        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.wrong_review_limit_title))
            .setMessage(getString(R.string.wrong_review_limit_message))
            .setNegativeButton(getString(R.string.close)) { dialog, _ ->
                pendingExpandQuestionId = null
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.wrong_review_btn_premium)) { _, _ ->
                WrongReviewAnalytics.logPremiumClick()
                pendingExpandQuestionId = null
                startActivity(Intent(this, TestSettingsActivity::class.java))
            }

        if (RewardedAdManager.isLoaded()) {
            builder.setPositiveButton(getString(R.string.wrong_review_btn_watch_ad)) { dialog, _ ->
                dialog.dismiss()
                showRewardedAd()
            }
        } else {
            builder.setPositiveButton(getString(R.string.wrong_review_btn_watch_ad)) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.wrong_review_ad_loading), Toast.LENGTH_SHORT).show()
                RewardedAdManager.preload(this)
                pendingExpandQuestionId = null
            }
        }
        builder.create().show()
    }

    private fun showRewardedAd() {
        RewardedAdManager.show(
            activity = this,
            onReward = {
                WrongReviewAnalytics.logAdShown()
                WrongReviewAnalytics.logAdRewarded()
                quotaStore.addOneFromReward()
                val qId = pendingExpandQuestionId
                pendingExpandQuestionId = null
                if (qId != null && quotaStore.consumeOne()) {
                    WrongReviewAnalytics.logItemReveal()
                    revealAndExpand(qId)
                }
            },
            onFail = { msg ->
                val err = RewardedAdManager.lastLoadError?.let { "$msg ($it)" } ?: msg
                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                pendingExpandQuestionId = null
            }
        )
    }

    private class WrongAnswerAdapter(
        private val questions: List<Question>,
        private val sessionAnswers: Map<String, Int>,
        private val revealedIds: Set<String>,
        private val expandedIds: MutableSet<String>,
        private val onRevealRequest: (String) -> Unit
    ) : RecyclerView.Adapter<WrongAnswerAdapter.VH>() {

        private val snippetLength = 80

        class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view as MaterialCardView
            val collapsedSection: View = view.findViewById(R.id.collapsedSection)
            val expandedSection: View = view.findViewById(R.id.expandedSection)
            val tvSnippet: TextView = view.findViewById(R.id.tvQuestionSnippet)
            val btnShowDetail: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnShowDetail)
            val tvQuestionFull: TextView = view.findViewById(R.id.tvQuestionFull)
            val tvUserChoice: TextView = view.findViewById(R.id.tvUserChoice)
            val tvCorrectAnswer: TextView = view.findViewById(R.id.tvCorrectAnswer)
            val tvExplanation: TextView = view.findViewById(R.id.tvExplanation)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_wrong_answer_list, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val q = questions[position]
            val userSel = sessionAnswers[q.id] ?: -1
            val userChoice = if (userSel in 0..3) q.choices.getOrNull(userSel) ?: "?" else "-"
            val correctChoice = q.choices.getOrNull(q.correctIndex) ?: "?"

            val snippet = if (q.stem.length > snippetLength) q.stem.take(snippetLength) + "…" else q.stem
            holder.tvSnippet.text = snippet

            val isRevealed = revealedIds.contains(q.id)
            val isExpanded = expandedIds.contains(q.id)
            holder.collapsedSection.visibility = if (isRevealed && isExpanded) View.GONE else View.VISIBLE
            holder.expandedSection.visibility = if (isRevealed && isExpanded) View.VISIBLE else View.GONE

            if (isRevealed) {
                holder.tvQuestionFull.text = q.stem
                holder.tvUserChoice.text = "Senin cevabın: $userChoice"
                holder.tvCorrectAnswer.text = "✓ Doğru: $correctChoice"
                if (!q.hint.isNullOrBlank()) {
                    holder.tvExplanation.visibility = View.VISIBLE
                    holder.tvExplanation.text = "💡 ${q.hint}"
                } else {
                    holder.tvExplanation.visibility = View.GONE
                }
            }

            holder.btnShowDetail.setOnClickListener { onRevealRequest(q.id) }
            holder.collapsedSection.setOnClickListener { onRevealRequest(q.id) }
        }

        override fun getItemCount() = questions.size
    }
}
