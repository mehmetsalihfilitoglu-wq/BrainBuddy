package com.mioacademy.app.dailychallenge

import android.content.Context
import com.mioacademy.app.auth.AuthProvider
import java.util.UUID

/**
 * Resolves a STABLE per-device user id for Daily Challenge state.
 *
 * Prefers the authenticated user id when signed in; otherwise a persisted anonymous id so the
 * daily habit, streaks, and review history survive before (and independently of) sign-in. When the
 * user later signs in, migration of anonymous state is a backend/sync concern (see sync seam).
 */
object DailyChallengeUser {
    private const val PREFS = "dc_user_prefs"
    private const val KEY_ANON_ID = "anon_user_id"

    fun resolve(context: Context): String {
        AuthProvider.currentUser(context)?.userId?.let { if (it.isNotBlank()) return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ANON_ID, null)?.let { return it }
        val fresh = "anon_" + UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ANON_ID, fresh).apply()
        return fresh
    }
}
