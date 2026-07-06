package com.mioacademy.app.analytics

import android.content.Context
import android.util.Log

/**
 * Product-analytics boundary. Business/UI code logs events through this interface,
 * never through a vendor SDK. [LogcatAnalyticsTracker] is the local no-op-ish impl
 * (writes to Logcat) so events can be instrumented and verified today; a
 * `FirebaseAnalyticsTracker` implements the same interface once Firebase is added.
 */
interface AnalyticsTracker {
    fun track(event: String, params: Map<String, Any?> = emptyMap())

    /** Associates events with the signed-in account (Firebase setUserId later). */
    fun setUserId(userId: String?)
    fun setUserProperty(key: String, value: String?)
}

/** Local implementation: logs to Logcat, holds no PII, sends nothing off-device. */
class LogcatAnalyticsTracker : AnalyticsTracker {
    override fun track(event: String, params: Map<String, Any?>) {
        if (params.isEmpty()) Log.d(TAG, "event=$event")
        else Log.d(TAG, "event=$event ${params.entries.joinToString(" ") { "${it.key}=${it.value}" }}")
    }

    override fun setUserId(userId: String?) { Log.d(TAG, "userId=${userId ?: "(none)"}") }
    override fun setUserProperty(key: String, value: String?) { Log.d(TAG, "prop $key=$value") }

    companion object { private const val TAG = "Analytics" }
}

/**
 * Composition root + convenient static entry point. Swap the returned tracker to a
 * Firebase adapter in one place when analytics goes live.
 */
object AnalyticsProvider {
    @Volatile private var cached: AnalyticsTracker? = null

    fun tracker(context: Context? = null): AnalyticsTracker =
        cached ?: synchronized(this) { cached ?: LogcatAnalyticsTracker().also { cached = it } }

    /** Sugar so call sites read `Analytics.track(APP_OPENED)`. */
    fun track(event: String, params: Map<String, Any?> = emptyMap()) = tracker().track(event, params)
}
