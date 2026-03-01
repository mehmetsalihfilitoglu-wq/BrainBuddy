package com.brainbuddy.app.report

import android.content.Context
import android.content.Intent

/**
 * Fallback: open share sheet with prefilled email intent when server not configured.
 */
class ShareIntentEmailDelivery(private val context: Context) : EmailDeliveryRepository {
    override suspend fun sendEmail(to: String, subject: String, htmlBody: String, textBody: String): EmailResult {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, textBody)
            }
            context.startActivity(Intent.createChooser(intent, "Raporu Gönder"))
            EmailResult.Success
        } catch (e: Exception) {
            EmailResult.Error(e.message ?: "Gönderilemedi")
        }
    }
}
