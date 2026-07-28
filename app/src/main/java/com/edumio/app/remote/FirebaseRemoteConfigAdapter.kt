package com.edumio.app.remote

import com.edumio.app.firebase.await
import com.google.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * Firebase Remote Config implementation of [RemoteConfig]. The bundled [RemoteConfigKeys.defaults] are set
 * as FRC defaults, so every getter has a safe value even before the first fetch and a fetch failure never
 * breaks the app. For keys FRC doesn't know, the caller's default wins. Remote Config can tune paywall copy,
 * flags, min-version, etc. — it can NEVER alter answers, question content, the five-question rule, blueprints
 * or verified solutions (those are not config-driven).
 */
class FirebaseRemoteConfigAdapter : RemoteConfig {

    private val rc: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance().apply {
        setDefaultsAsync(RemoteConfigKeys.defaults)
    }

    private fun known(key: String) = RemoteConfigKeys.defaults.containsKey(key)

    override fun getString(key: String, default: String): String =
        if (known(key)) rc.getString(key).ifEmpty { default } else default

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (known(key)) rc.getBoolean(key) else default

    override fun getLong(key: String, default: Long): Long =
        if (known(key)) rc.getLong(key) else default

    override suspend fun refresh(): Boolean = try {
        rc.fetchAndActivate().await()
    } catch (_: Throwable) {
        false // offline / error → keep current values (which fall back to safe defaults)
    }
}
