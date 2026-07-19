package com.edumio.app.messaging

import android.content.Context
import com.edumio.app.firebase.FirebaseConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Registers this device's FCM token at `users/{uid}/fcmTokens/{token}` so the server (Cloud Functions) can
 * target the signed-in user. No-op unless Firebase is configured AND a user is signed in; failures are
 * swallowed (a missing token just means no server push — the local WorkManager reminders still run).
 */
object FcmTokenRegistrar {

    fun register(context: Context, token: String) {
        try {
            if (token.isBlank() || !FirebaseConfig.isConfigured(context)) return
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("fcmTokens").document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                )
        } catch (_: Throwable) {
            // best-effort; server push is supplementary to local reminders
        }
    }
}
