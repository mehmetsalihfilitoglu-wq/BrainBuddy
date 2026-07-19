package com.edumio.app.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Firebase implementation of [AnalyticsTracker], activated by [AnalyticsProvider] when Firebase is
 * configured. Every event is passed through [AnalyticsSafety] first, so PII / question / solution content
 * can never reach Firebase. Params are marshalled to a [Bundle] with Firebase-supported types.
 */
class FirebaseAnalyticsTracker(context: Context) : AnalyticsTracker {

    private val fa = FirebaseAnalytics.getInstance(context.applicationContext)

    override fun track(event: String, params: Map<String, Any?>) {
        val bundle = Bundle()
        for ((k, v) in AnalyticsSafety.sanitize(params)) {
            when (v) {
                is String -> bundle.putString(k, v)
                is Long -> bundle.putLong(k, v)
                is Int -> bundle.putLong(k, v.toLong())
                is Boolean -> bundle.putLong(k, if (v) 1 else 0)
                is Double -> bundle.putDouble(k, v)
                is Float -> bundle.putDouble(k, v.toDouble())
                null -> { /* skip */ }
                else -> bundle.putString(k, v.toString().take(100))
            }
        }
        fa.logEvent(AnalyticsSafety.safeEventName(event), bundle)
    }

    override fun setUserId(userId: String?) = fa.setUserId(userId)

    override fun setUserProperty(key: String, value: String?) {
        if (AnalyticsSafety.isKeyAllowed(key)) fa.setUserProperty(key.take(24), value?.take(36))
    }
}
