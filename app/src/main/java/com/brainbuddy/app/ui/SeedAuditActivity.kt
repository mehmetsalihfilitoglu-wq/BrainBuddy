package com.brainbuddy.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainbuddy.app.R
import com.brainbuddy.app.db.StartupAuditRecorder
import kotlinx.coroutines.launch

/**
 * On-device audit: DB counts, seed/quota summary, distributions. No Logcat required.
 */
class SeedAuditActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seed_audit)

        val tvBody = findViewById<TextView>(R.id.tvAuditBody)
        val tvPath = findViewById<TextView>(R.id.tvAuditPath)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCopyAudit).setOnClickListener {
            val text = tvBody.text?.toString().orEmpty()
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BrainBuddy audit", text))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRefreshAudit).setOnClickListener {
            lifecycleScope.launch {
                val text = StartupAuditRecorder.buildLiveReport(this@SeedAuditActivity)
                tvBody.text = text
                tvPath.text = StartupAuditRecorder.auditFileAbsolutePath
                    ?: getString(R.string.seed_audit_path_unknown)
            }
        }

        lifecycleScope.launch {
            val text = if (StartupAuditRecorder.lastAuditText.isNotEmpty()) {
                StartupAuditRecorder.lastAuditText
            } else {
                StartupAuditRecorder.buildLiveReport(this@SeedAuditActivity)
            }
            tvBody.text = text
            tvPath.text = StartupAuditRecorder.auditFileAbsolutePath
                ?: getString(R.string.seed_audit_path_unknown)
        }
    }
}
