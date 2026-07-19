package com.edumio.app.firebase

import android.content.Context
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Installs Firebase App Check (Play Integrity provider) so the backend can reject requests that don't come
 * from a genuine, unmodified EDUmio app. No-op unless Firebase is configured; safe to call unconditionally
 * from `Application.onCreate` (guarded + try/caught). App Check is a trust signal, **not** perfect
 * anti-tamper — the server verifications (challenge identity, entitlement) remain the real guarantees.
 *
 * Owner step: register the Play Integrity provider + (for debug) a debug token in the Firebase console
 * (App Check), and enforce App Check on Firestore/Functions when ready.
 */
object AppCheckInitializer {

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed || !FirebaseConfig.isConfigured(context)) return
        try {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance(),
            )
            installed = true
        } catch (_: Throwable) {
            // App Check is best-effort hardening; never block app startup on it.
        }
    }
}
