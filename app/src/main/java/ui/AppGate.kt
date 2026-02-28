package com.brainbuddy.app.ui

import android.content.Context
import android.content.Intent
import com.brainbuddy.app.security.ProtectedNav

object AppGate {

    /**
     * Use this everywhere you navigate to parent/config screens.
     * This ensures a PIN is required to change protected settings.
     */
    fun openParent(context: Context) {
        ProtectedNav.open(context, ParentActivity::class.java, "Enter parent PIN")
    }

    fun openSettings(context: Context) {
        ProtectedNav.open(context, SettingsActivity::class.java, "Enter parent PIN")
    }

    fun openProtectionInactive(context: Context) {
        context.startActivity(
            Intent(context, ProtectionInactiveActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}