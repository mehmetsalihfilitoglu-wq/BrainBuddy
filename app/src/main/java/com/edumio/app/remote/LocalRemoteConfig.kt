package com.edumio.app.remote

import android.content.Context
import com.edumio.app.firebase.FirebaseConfig

/**
 * Serves the bundled [RemoteConfigKeys.defaults]. This keeps every consumer written
 * against RemoteConfig from day one, so enabling server control later is a provider
 * swap — no call sites change.
 */
class LocalRemoteConfig : RemoteConfig {

    private val values: Map<String, Any> = RemoteConfigKeys.defaults

    override fun getString(key: String, default: String): String =
        (values[key] as? String) ?: default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        (values[key] as? Boolean) ?: default

    override fun getLong(key: String, default: Long): Long =
        when (val v = values[key]) {
            is Long -> v
            is Int -> v.toLong()
            else -> default
        }

    override suspend fun refresh(): Boolean = false
}

/** Composition root for [RemoteConfig] — returns the Firebase adapter once configured, else local defaults.
 *  Pass a [Context] on the first call so config can be detected; the no-arg [get] returns whatever is cached
 *  (local defaults until an install-context call installs the Firebase adapter). */
object RemoteConfigProvider {
    @Volatile private var cached: RemoteConfig? = null

    fun get(context: Context): RemoteConfig {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val rc: RemoteConfig =
                    if (FirebaseConfig.isConfigured(context)) FirebaseRemoteConfigAdapter() else LocalRemoteConfig()
                cached = rc
                rc
            }
        }
    }

    fun get(): RemoteConfig = cached ?: synchronized(this) {
        cached ?: LocalRemoteConfig().also { cached = it }
    }
}
