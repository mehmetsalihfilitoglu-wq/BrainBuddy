package com.mioacademy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.core.EmailReportPrefs
import java.io.File

/** Opens share sheet with report content. Launched from ReportWorker notification. */
class ReportShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(filesDir, "pending_report.txt")
        val subjFile = File(filesDir, "pending_report_subject.txt")
        val to = EmailReportPrefs(this).reportEmail().ifBlank { null }
            ?: com.mioacademy.app.auth.AuthProvider.currentUser(this)?.email
            ?: ""
        val subject = subjFile.takeIf { it.exists() }?.readText() ?: "Mioitalia Rapor"
        val body = file.takeIf { it.exists() }?.readText() ?: ""
        file.delete()
        subjFile.delete()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, getString(com.mioacademy.app.R.string.report_share_prompt)))
        finish()
    }
}
