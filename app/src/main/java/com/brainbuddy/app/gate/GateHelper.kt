package com.brainbuddy.app.gate

import android.content.Context
import android.content.Intent

object GateHelper {

    /** Delegates to GateManager (single source of truth). */
    fun gateRequiredNow(context: Context): Boolean = GateManager.gateRequiredNow(context)

    fun openGate(context: Context, blockedPackage: String) {
        val intent = Intent(context, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
        }
        context.startActivity(intent)
    }
}
