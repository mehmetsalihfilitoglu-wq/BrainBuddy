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
import com.brainbuddy.app.db.StartupRuntimeState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * On-device audit: DB counts, seed/quota summary, distributions. No Logcat required.
 * Shows counts only after [StartupRuntimeState] reports startup complete (no partial flicker).
 */
class SeedAuditActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seed_audit)

        val tvStatus = findViewById<TextView>(R.id.tvAuditStatus)
        val tvBody = findViewById<TextView>(R.id.tvAuditBody)
        val tvPath = findViewById<TextView>(R.id.tvAuditPath)
        val btnCopy = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCopyAudit)
        val btnRefresh = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRefreshAudit)

        btnCopy.setOnClickListener {
            val text = tvBody.text?.toString().orEmpty()
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BrainBuddy audit", text))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnRefresh.setOnClickListener {
            if (!StartupRuntimeState.startupInitializationComplete) {
                Toast.makeText(this, R.string.seed_audit_initializing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val text = StartupAuditRecorder.buildLiveReport(this@SeedAuditActivity)
                tvBody.text = text
                tvPath.text = StartupAuditRecorder.auditFileAbsolutePath
                    ?: getString(R.string.seed_audit_path_unknown)
            }
        }

        lifecycleScope.launch {
            StartupRuntimeState.phase.collect { phase ->
                when (phase) {
                    is StartupRuntimeState.StartupPhase.Initializing -> {
                        tvStatus.text = "STATUS: INITIALIZING"
                        tvBody.text = getString(R.string.seed_audit_initializing)
                        tvPath.text = ""
                        btnCopy.isEnabled = false
                        btnRefresh.isEnabled = false
                    }
                    is StartupRuntimeState.StartupPhase.Ready -> {
                        tvStatus.text = "STATUS: FINALIZED"
                        tvBody.text = phase.payload.auditText
                        tvPath.text = phase.payload.auditPath
                            ?: getString(R.string.seed_audit_path_unknown)
                        btnCopy.isEnabled = true
                        btnRefresh.isEnabled = true
                    }
                }
            }
        }
    }
}
