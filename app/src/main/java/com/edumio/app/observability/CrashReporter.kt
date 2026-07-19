package com.edumio.app.observability

import android.content.Context
import android.util.Log
import com.edumio.app.firebase.FirebaseConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crash-reporting boundary. Business code reports non-fatal errors + breadcrumbs through this interface,
 * never a vendor SDK. [CrashlyticsReporter] activates when Firebase is configured; [NoOpCrashReporter]
 * (Logcat only) is the local default. **Never pass PII / question / solution content** to these methods —
 * keys/values must be identifiers and short diagnostics.
 */
interface CrashReporter {
    fun recordException(t: Throwable)
    fun log(message: String)
    fun setKey(key: String, value: String)
    fun setUserId(userId: String?)
}

/** Local default — writes to Logcat, sends nothing off-device. */
class NoOpCrashReporter : CrashReporter {
    override fun recordException(t: Throwable) { Log.w(TAG, "non-fatal", t) }
    override fun log(message: String) { Log.d(TAG, message) }
    override fun setKey(key: String, value: String) { Log.d(TAG, "key $key=$value") }
    override fun setUserId(userId: String?) { Log.d(TAG, "user=${userId ?: "(none)"}") }
    companion object { private const val TAG = "Crash" }
}

/** Firebase Crashlytics adapter. */
class CrashlyticsReporter : CrashReporter {
    private val c = FirebaseCrashlytics.getInstance()
    override fun recordException(t: Throwable) = c.recordException(t)
    override fun log(message: String) = c.log(message)
    override fun setKey(key: String, value: String) = c.setCustomKey(key, value)
    override fun setUserId(userId: String?) = c.setUserId(userId ?: "")
}

/** Composition root — Crashlytics when configured, else the local no-op. */
object CrashReporterProvider {
    @Volatile private var cached: CrashReporter? = null
    fun get(context: Context): CrashReporter {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val r: CrashReporter =
                    if (FirebaseConfig.isConfigured(context)) CrashlyticsReporter() else NoOpCrashReporter()
                cached = r
                r
            }
        }
    }
}
