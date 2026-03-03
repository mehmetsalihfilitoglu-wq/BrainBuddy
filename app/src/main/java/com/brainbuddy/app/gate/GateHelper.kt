package com.brainbuddy.app.gate

import android.content.Context
import android.content.Intent

object GateHelper {

    /** True if gate quiz required for this blocked package. Only used from Accessibility flow. */
    fun gateRequiredNow(context: Context, blockedPackage: String): Boolean =
        GateManager.gateRequiredNow(context, blockedPackage)

    fun openGate(context: Context, blockedPackage: String) {
        val intent = Intent(context, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
        }
        context.startActivity(intent)
    }
}
