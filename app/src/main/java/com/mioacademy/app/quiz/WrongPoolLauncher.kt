package com.mioacademy.app.quiz

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mioacademy.app.R
import com.mioacademy.app.core.PremiumStore

object WrongPoolLauncher {
    fun launch(context: Context) {
        if (!PremiumStore(context).isPremium()) {
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
