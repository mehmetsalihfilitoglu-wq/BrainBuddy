package com.brainbuddy.app.security

import android.app.Activity
import android.content.Context
import android.content.Intent

object ProtectedNav {

    /**
     * Protected screens should be opened via this method.
     * Later you can route to PIN screen here (PinLockActivity etc).
     */
    fun open(context: Context, target: Class<*>, prompt: String) {
        val i = Intent(context, target)

        // If called from non-Activity context (e.g. service/app context), this is required
        if (context !is Activity) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // (Optional) pass prompt to the target screen if you want to show it
        i.putExtra("protected_prompt", prompt)

        context.startActivity(i)
    }
}