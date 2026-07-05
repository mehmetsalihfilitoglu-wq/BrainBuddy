package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.EmailReportPrefs
import java.io.File

/** Opens share sheet with report content. Launched from ReportWorker notification. */
class ReportShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(filesDir, "pending_report.txt")
        val subjFile = File(filesDir, "pending_report_subject.txt")
        val to = EmailReportPrefs(this).reportEmail().ifBlank { null }
            ?: com.brainbuddy.app.auth.AuthProvider.get(this).currentEmail()
            ?: ""
        val subject = subjFile.takeIf { it.exists() }?.readText() ?: "MioAcademy Rapor"
        val body = file.takeIf { it.exists() }?.readText() ?: ""
        file.delete()
        subjFile.delete()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, getString(com.brainbuddy.app.R.string.report_share_prompt)))
        finish()
    }
}
