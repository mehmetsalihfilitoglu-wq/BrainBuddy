package com.edumio.app.quiz

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.edumio.app.R
import com.edumio.app.core.FeatureAccess

object WrongPoolLauncher {
    fun launch(context: Context) {
        // Wrong-question review is an MVP keep-list feature and v1.0 is entirely free. While the Premium
        // UI is off this never blocks — previously every entry point (Home, Öğren, İlerleme, next-step)
        // dead-ended in a "premium üyelik gerekiyor" toast.
        if (!FeatureAccess.hasFullAccess(context)) {
            Toast.makeText(context, R.string.wrong_pool_premium_message, Toast.LENGTH_SHORT).show()
            return
        }
        val poolStore = WrongQuestionPoolStore(context)
        if (poolStore.isEmpty()) {
            Toast.makeText(context, R.string.wrong_pool_empty, Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(Intent(context, WrongPoolActivity::class.java))
    }
}
