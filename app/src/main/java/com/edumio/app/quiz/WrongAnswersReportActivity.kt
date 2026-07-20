package com.edumio.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edumio.app.R
import com.edumio.app.ads.RewardedAdManager
import com.edumio.app.core.PremiumStore
import com.edumio.app.databinding.ActivityWrongAnswersReportBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Raporlar ekranından açılan yanlış cevaplar raporu.
 * wrong_answers tablosundan beslenir (PastTestDetail ile aynı kaynak).
 * - Soru metni varsayılan kilitli: "Yanlış cevap verilmiş soru"
 * - Tıklayınca: Premium ise direkt aç; değilse rewarded izlenince aç
 * - Doğru cevap gösterilmez (sadece kullanıcının verdiği cevap)
 */
class WrongAnswersReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SINCE_MILLIS = "extra_since_millis"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }

    private lateinit var binding: ActivityWrongAnswersReportBinding
    private lateinit var repository: WrongAnswersReportRepository
    private lateinit var premiumStore: PremiumStore
    private var pendingUnlockItem: WrongReportUiItem? = null

    private var sinceMillis: Long = 0
    private var profileId: String = "default"
    private var items: List<WrongReportUiItem> = emptyList()
    private val expandedIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sinceMillis = intent.getLongExtra(EXTRA_SINCE_MILLIS, 0)
        profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: com.edumio.app.core.ActiveProfileManager.getActiveProfileId(this)

        binding = ActivityWrongAnswersReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        repository = WrongAnswersReportRepository(this)
        premiumStore = PremiumStore(this)
        RewardedAdManager.preload(this)

        loadItems()
    }

    override fun onStart() {
        super.onStart()
        RewardedAdManager.preload(this)
    }

    private fun loadItems() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                repository.loadWrongItems(sinceMillis, profileId)
            }
            items = list
            if (list.isEmpty()) {
                binding.recyclerWrongReport.visibility = View.GONE
                binding.emptyState.visibility = View.VISIBLE
            } else {
                binding.recyclerWrongReport.visibility = View.VISIBLE
                binding.emptyState.visibility = View.GONE
                binding.recyclerWrongReport.layoutManager = LinearLayoutManager(this@WrongAnswersReportActivity)
                binding.recyclerWrongReport.adapter = WrongReportAdapter(
                    items = list,
                    expandedIds = expandedIds,
                    onItemClick = ::onItemClick
                )
            }
        }
    }

    private fun onItemClick(item: WrongReportUiItem) {
        if (item.isUnlocked) {
            toggleExpand(item)
            return
        }

        if (premiumStore.isPremium()) {
            performUnlock(item)
            return
        }

        pendingUnlockItem = item
        showUnlockAdDialog(item)
    }

    private fun toggleExpand(item: WrongReportUiItem) {
        val key = "${item.testId}_${item.questionId}"
        if (expandedIds.contains(key)) {
            expandedIds.remove(key)
        } else {
            expandedIds.add(key)
        }
        refreshAdapter()
    }

    private fun performUnlock(item: WrongReportUiItem) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.unlockWrongAnswer(item.testId, item.questionId)
            }
            expandedIds.add("${item.testId}_${item.questionId}")
            loadItems()
        }
    }

    private fun showUnlockAdDialog(item: WrongReportUiItem) {
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.wrong_review_limit_title))
            .setMessage(getString(R.string.wrong_review_limit_message))
            .setNegativeButton(getString(R.string.close)) { d, _ ->
                pendingUnlockItem = null
                d.dismiss()
            }

        if (RewardedAdManager.isLoaded()) {
            builder.setPositiveButton(getString(R.string.wrong_report_btn_watch_unlock)) { d, _ ->
                d.dismiss()
                showRewardedAd()
            }
        } else {
            builder.setPositiveButton(getString(R.string.wrong_report_btn_watch_unlock)) { d, _ ->
                d.dismiss()
                Toast.makeText(this, getString(R.string.wrong_review_ad_loading), Toast.LENGTH_SHORT).show()
                RewardedAdManager.preload(this)
                pendingUnlockItem = null
            }
        }
        builder.show()
    }

    private fun showRewardedAd() {
        val item = pendingUnlockItem ?: return
        RewardedAdManager.show(
            activity = this,
            onReward = {
                performUnlock(item)
                pendingUnlockItem = null
            },
            onFail = { msg ->
                val err = RewardedAdManager.lastLoadError?.let { "$msg ($it)" } ?: msg
                Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                pendingUnlockItem = null
            }
        )
    }

    private fun refreshAdapter() {
        (binding.recyclerWrongReport.adapter as? WrongReportAdapter)?.updateItems(items)
    }

    private class WrongReportAdapter(
        private var items: List<WrongReportUiItem>,
        private val expandedIds: MutableSet<String>,
        private val onItemClick: (WrongReportUiItem) -> Unit
    ) : RecyclerView.Adapter<WrongReportAdapter.VH>() {

        fun updateItems(newItems: List<WrongReportUiItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            val btnAction: MaterialButton = view.findViewById(R.id.btnAction)
            val collapsedSection: View = view.findViewById(R.id.collapsedSection)
            val expandedSection: View = view.findViewById(R.id.expandedSection)
            val tvQuestionFull: TextView = view.findViewById(R.id.tvQuestionFull)
            val tvUserChoice: TextView = view.findViewById(R.id.tvUserChoice)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_wrong_report, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val key = "${item.testId}_${item.questionId}"
            val isExpanded = expandedIds.contains(key)

            if (item.isUnlocked) {
                holder.tvTitle.text = item.questionStem?.take(80)?.let { if (it.length >= 80) "$it…" else it } ?: "Yanlış cevap verilmiş soru"
                holder.btnAction.text = holder.itemView.context.getString(R.string.wrong_review_btn_show_detail)
                holder.collapsedSection.visibility = View.VISIBLE
                holder.expandedSection.visibility = if (isExpanded) View.VISIBLE else View.GONE
                if (isExpanded) {
                    holder.tvQuestionFull.text = item.questionStem ?: ""
                    holder.tvUserChoice.text = "Senin cevabın: ${item.userChoiceText}"
                }
            } else {
                holder.tvTitle.text = holder.itemView.context.getString(R.string.wrong_report_locked_title)
                holder.btnAction.text = holder.itemView.context.getString(R.string.wrong_report_btn_watch_unlock)
                holder.collapsedSection.visibility = View.VISIBLE
                holder.expandedSection.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.btnAction.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
