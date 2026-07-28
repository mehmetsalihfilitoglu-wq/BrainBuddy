package com.edumio.app.firebase

import android.content.Context

/**
 * Single source of truth for "is Firebase actually configured?". The Google Services Gradle plugin only
 * generates the `google_app_id` string resource when `google-services.json` is present, so probing for that
 * resource tells us whether a real Firebase project is wired — WITHOUT referencing any Firebase SDK class,
 * so the check is safe even in a pure local-fallback build.
 *
 * Every Firebase-backed adapter (auth, sync, analytics, crashlytics, messaging) calls [isConfigured] before
 * touching the SDK; when it returns false the app uses its local fallback and nothing initializes.
 */
object FirebaseConfig {

    @Volatile private var cached: Boolean? = null

    fun isConfigured(context: Context): Boolean {
        cached?.let { return it }
        val appContext = context.applicationContext
        val id = appContext.resources.getIdentifier("google_app_id", "string", appContext.packageName)
        val present = id != 0 && runCatching { appContext.getString(id).isNotBlank() }.getOrDefault(false)
        cached = present
        return present
    }
}
