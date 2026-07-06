package com.mioacademy.app.quiz

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mioacademy.app.R
import com.mioacademy.app.analytics.AnalyticsEvents
import com.mioacademy.app.analytics.AnalyticsProvider
import com.mioacademy.app.core.WrongReviewAnalytics
import com.mioacademy.app.ui.AccountSyncActivity

/**
 * Premium value screen. Sells intelligence, not quantity: each row pairs a feature
 * with the educational value it creates. No fake urgency, honest about availability —
 * the CTA leads to the account setup that premium will attach to.
 */
class PremiumPaywallSheet : BottomSheetDialogFragment() {

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

        view.findViewById<View>(R.id.btnPaywallPremium).setOnClickListener {
            WrongReviewAnalytics.logPremiumClick()
            AnalyticsProvider.track(AnalyticsEvents.PREMIUM_CLICKED)
            startActivity(Intent(requireContext(), AccountSyncActivity::class.java))
            dismiss()
        }
        view.findViewById<View>(R.id.tvPaywallDismiss).setOnClickListener { dismiss() }
    }

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
            block.addView(TextView(requireContext()).apply {
                text = "✦  ${getString(titleRes)}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                setTextColor(resources.getColor(R.color.bb_text_dark, requireContext().theme))
            })
            block.addView(TextView(requireContext()).apply {
                text = getString(subRes)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(resources.getColor(R.color.bb_text_muted, requireContext().theme))
                setLineSpacing(0f, 1.35f)
                gravity = Gravity.START
                setPadding(dp(24f), dp(2f), 0, 0)
            })
            container.addView(block)
        }
    }

    companion object {
        const val TAG = "PremiumPaywallSheet"
    }
}
