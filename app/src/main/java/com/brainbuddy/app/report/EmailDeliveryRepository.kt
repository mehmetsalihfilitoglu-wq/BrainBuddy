package com.brainbuddy.app.report

/**
 * Abstraction for sending emails. Use Cloud Function when Firebase configured;
 * otherwise fallback to share intent.
 */
interface EmailDeliveryRepository {
    suspend fun sendEmail(to: String, subject: String, htmlBody: String, textBody: String): EmailResult
}

sealed class EmailResult {
    object Success : EmailResult()
    data class Error(val message: String) : EmailResult()
    object ServerNotConfigured : EmailResult()
}
