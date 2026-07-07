package com.mioacademy.app.quiz

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mioacademy.app.R
import com.mioacademy.app.analytics.AnalyticsEvents
import com.mioacademy.app.analytics.AnalyticsProvider
import com.mioacademy.app.billing.BillingProvider
import com.mioacademy.app.billing.PlanOffer
import com.mioacademy.app.billing.PurchaseResult
import com.mioacademy.app.billing.SubscriptionPlan
import com.mioacademy.app.billing.SubscriptionState
import com.mioacademy.app.core.WrongReviewAnalytics
import kotlinx.coroutines.launch

/**
 * Premium value + subscription screen. Sells intelligence (each row pairs a feature
 * with its educational value), then shows the real monthly/yearly offers, current
 * subscription status, purchase, and restore — all through [com.mioacademy.app.billing.BillingRepository],
 * never Play Billing directly. Honest: while billing isn't connected, purchase reports
 * that clearly instead of faking a charge. No ads, ever.
 */
class PremiumPaywallSheet : BottomSheetDialogFragment() {

    private val billing by lazy { BillingProvider.repository(requireContext()) }
    private var selectedPlan: SubscriptionPlan = SubscriptionPlan.YEARLY
    private var purchasing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_premium_paywall, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WrongReviewAnalytics.logPaywallOpened()
        AnalyticsProvider.track(AnalyticsEvents.PREMIUM_PAYWALL_VIEWED)

        populateBenefits(view.findViewById(R.id.benefitsContainer))
        renderStatus()

        view.findViewById<View>(R.id.btnPaywallPremium).setOnClickListener { onPurchaseClicked() }
        view.findViewById<View>(R.id.tvRestore).setOnClickListener { onRestoreClicked() }
        view.findViewById<View>(R.id.tvPaywallDismiss).setOnClickListener { dismiss() }

        loadOffers()
    }

    // ── Status ────────────────────────────────────────────────────────────────
    private fun renderStatus() {
        val tv = view?.findViewById<TextView>(R.id.tvSubscriptionStatus) ?: return
        val status = billing.status()
        tv.text = when {
            status.isPremium -> getString(R.string.paywall_status_active, planLabel(status.plan))
            status.state == SubscriptionState.EXPIRED -> getString(R.string.paywall_status_expired)
            else -> getString(R.string.paywall_status_free)
        }
        tv.setTextColor(resources.getColor(
            if (status.isPremium) R.color.emeraldDark else R.color.bb_text_muted, requireContext().theme))
    }

    // ── Offers ────────────────────────────────────────────────────────────────
    private fun loadOffers() {
        viewLifecycleOwner.lifecycleScope.launch {
            val offers = runCatching { billing.offers() }.getOrDefault(emptyList())
            renderPlans(offers)
        }
    }

    private fun renderPlans(offers: List<PlanOffer>) {
        val container = view?.findViewById<LinearLayout>(R.id.plansContainer) ?: return
        container.removeAllViews()
        offers.forEach { offer -> container.addView(planCard(offer)) }
        highlightSelection()
    }

    private fun planCard(offer: PlanOffer): com.google.android.material.card.MaterialCardView {
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d + 0.5f).toInt()

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        val texts = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(makeText(planLabel(offer.plan), 16f, R.color.bb_text_dark, bold = true))
        if (offer.plan == SubscriptionPlan.YEARLY) {
            titleRow.addView(makeText(getString(R.string.paywall_best_value), 10f, R.color.emeraldDark, bold = true).apply {
                setPadding(dp(8f), 0, 0, 0)
            })
        }
        texts.addView(titleRow)
        texts.addView(makeText(
            getString(R.string.paywall_price_period, offer.priceLabel, offer.periodLabel),
            13f, R.color.bb_text_muted).apply { setPadding(0, dp(2f), 0, 0) })
        row.addView(texts)

        return com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(10f) }
            radius = 14 * d
            cardElevation = 0f
            setCardBackgroundColor(resources.getColor(R.color.white, requireContext().theme))
            tag = offer.plan
            isClickable = true; isFocusable = true
            addView(row)
            setOnClickListener {
                selectedPlan = offer.plan
                highlightSelection()
            }
        }
    }

    private fun highlightSelection() {
        val container = view?.findViewById<LinearLayout>(R.id.plansContainer) ?: return
        val d = resources.displayMetrics.density
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i) as? com.google.android.material.card.MaterialCardView ?: continue
            val selected = card.tag == selectedPlan
            card.strokeWidth = ((if (selected) 2f else 1f) * d + 0.5f).toInt()
            card.setStrokeColor(resources.getColor(
                if (selected) R.color.emerald else R.color.border, requireContext().theme))
        }
    }

    // ── Purchase / restore (BillingRepository only) ─────────────────────────────
    private fun onPurchaseClicked() {
        if (purchasing) return
        purchasing = true
        AnalyticsProvider.track(AnalyticsEvents.PREMIUM_CLICKED)
        WrongReviewAnalytics.logPremiumClick()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { billing.purchase(requireActivity(), selectedPlan) }
                .getOrElse { PurchaseResult.Error(it.message ?: "error") }
            purchasing = false
            when (result) {
                is PurchaseResult.Success -> { renderStatus(); toast(getString(R.string.paywall_status_active, planLabel(result.status.plan))) }
                PurchaseResult.Unavailable -> toast(getString(R.string.paywall_purchase_unavailable))
                PurchaseResult.Pending -> toast(getString(R.string.paywall_purchase_unavailable))
                PurchaseResult.Cancelled -> { /* user backed out — no message */ }
                is PurchaseResult.Error -> toast(getString(R.string.paywall_purchase_unavailable))
            }
        }
    }

    private fun onRestoreClicked() {
        viewLifecycleOwner.lifecycleScope.launch {
            val status = runCatching { billing.restore() }.getOrNull()
            renderStatus()
            toast(if (status?.isPremium == true) getString(R.string.paywall_restore_done)
                  else getString(R.string.paywall_restore_none))
        }
    }

    private fun planLabel(plan: SubscriptionPlan?): String = when (plan) {
        SubscriptionPlan.YEARLY -> getString(R.string.paywall_plan_yearly)
        else -> getString(R.string.paywall_plan_monthly)
    }

    private fun toast(msg: String) {
        if (isAdded) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    // ── Benefits ────────────────────────────────────────────────────────────────
    private fun populateBenefits(container: LinearLayout) {
        val features = listOf(
            R.string.paywall_f1_title to R.string.paywall_f1_sub,
            R.string.paywall_f2_title to R.string.paywall_f2_sub,
            R.string.paywall_f3_title to R.string.paywall_f3_sub,
            R.string.paywall_f4_title to R.string.paywall_f4_sub,
            R.string.paywall_f5_title to R.string.paywall_f5_sub,
            R.string.paywall_f6_title to R.string.paywall_f6_sub,
            R.string.paywall_f7_title to R.string.paywall_f7_sub,
            R.string.paywall_f8_title to R.string.paywall_f8_sub
        )
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d + 0.5f).toInt()

        features.forEachIndexed { i, (titleRes, subRes) ->
            val block = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { if (i > 0) it.topMargin = dp(16f) }
            }
            block.addView(makeText("✦  ${getString(titleRes)}", 15f, R.color.bb_text_dark, bold = true))
            block.addView(makeText(getString(subRes), 13f, R.color.bb_text_muted).apply {
                setLineSpacing(0f, 1.35f)
                setPadding(dp(24f), dp(2f), 0, 0)
            })
            container.addView(block)
        }
    }

    private fun makeText(t: String, size: Float, colorRes: Int, bold: Boolean = false): TextView =
        TextView(requireContext()).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(resources.getColor(colorRes, requireContext().theme))
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.START
        }

    companion object {
        const val TAG = "PremiumPaywallSheet"
    }
}
