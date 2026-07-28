package com.edumio.app.report

import android.content.Context
import java.io.File

/**
 * The delivery boundary for weekly/monthly learning reports. Report *content* is
 * already generated on-device (ReportGenerator); this contract is about getting it
 * to the student.
 *
 * Today [LocalReportDeliveryService] queues the report for manual share (the current
 * behaviour). A `CloudReportDeliveryService` — a Firebase Cloud Function invoking an
 * email provider (e.g. SendGrid) every Sunday — will implement the same interface to
 * send the beautifully formatted HTML email automatically, with no caller changes.
 */
interface ReportDeliveryService {
    suspend fun deliver(payload: ReportPayload): DeliveryResult
}

enum class ReportType { WEEKLY, MONTHLY }

data class ReportPayload(
    val type: ReportType,
    val subject: String,
    val html: String,
    val text: String,
    val recipientEmail: String?
)

sealed class DeliveryResult {
    /** A real email was sent (cloud adapter). */
    object Sent : DeliveryResult()
    /** Saved on-device for manual share (today's on-device behaviour). */
    object QueuedLocally : DeliveryResult()
    object NoRecipient : DeliveryResult()
    data class Error(val message: String) : DeliveryResult()
}

/**
 * On-device delivery: writes the report to the pending-share files that
 * [com.edumio.app.ui.ReportShareActivity] reads, so the student can send it
 * themselves. Honest stand-in until server-side email exists.
 */
class LocalReportDeliveryService(context: Context) : ReportDeliveryService {

    private val appContext = context.applicationContext

    override suspend fun deliver(payload: ReportPayload): DeliveryResult {
        return try {
            File(appContext.filesDir, PENDING_BODY).writeText(payload.text)
            File(appContext.filesDir, PENDING_SUBJECT).writeText(payload.subject)
            DeliveryResult.QueuedLocally
        } catch (e: Exception) {
            DeliveryResult.Error(e.message ?: "delivery error")
        }
    }

    companion object {
        private const val PENDING_BODY = "pending_report.txt"
        private const val PENDING_SUBJECT = "pending_report_subject.txt"
    }
}

/** Composition root for report delivery — returns the cloud adapter once configured. */
object ReportDeliveryProvider {
    fun service(context: Context): ReportDeliveryService =
        LocalReportDeliveryService(context.applicationContext)
}
