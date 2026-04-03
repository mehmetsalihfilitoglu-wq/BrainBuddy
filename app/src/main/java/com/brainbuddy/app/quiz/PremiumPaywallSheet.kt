package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.brainbuddy.app.R
import com.brainbuddy.app.core.WrongReviewAnalytics
import com.brainbuddy.app.ui.TestSettingsActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Premium upsell paywall shown when daily review limit is reached.
 * BottomSheet — no AlertDialogs for monetization.
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

        view.findViewById<View>(R.id.btnPaywallPremium).setOnClickListener {
            WrongReviewAnalytics.logPremiumClick()
            startActivity(Intent(requireContext(), TestSettingsActivity::class.java))
            dismiss()
        }

        view.findViewById<View>(R.id.tvPaywallDismiss).setOnClickListener {
            dismiss()
        }
    }

    companion object {
        const val TAG = "PremiumPaywallSheet"
    }
}
