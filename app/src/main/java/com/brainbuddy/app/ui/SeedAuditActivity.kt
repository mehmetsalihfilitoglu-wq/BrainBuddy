package com.brainbuddy.app.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainbuddy.app.BuildConfig
import com.brainbuddy.app.R
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.DbSeeder
import com.brainbuddy.app.db.PoolQuotaEnforcer
import com.brainbuddy.app.db.StartupAuditRecorder
import com.brainbuddy.app.db.StartupRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * On-device audit: DB counts, seed/quota summary, distributions. No Logcat required.
 * Shows counts only after [StartupRuntimeState] reports startup complete (no partial flicker).
 *
 * Also shows build identity and seed version info so installed-build mismatches are visible.
 */
class SeedAuditActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seed_audit)

        val tvStatus = findViewById<TextView>(R.id.tvAuditStatus)
        val tvCounts = findViewById<TextView>(R.id.tvAuditCounts)
        val tvBody = findViewById<TextView>(R.id.tvAuditBody)
        val tvPath = findViewById<TextView>(R.id.tvAuditPath)
        val btnCopy = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCopyAudit)
        val btnRefresh = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRefreshAudit)

        // Show build identity immediately — no async needed
        tvStatus.text = buildIdentityLine()

        btnCopy.setOnClickListener {
            val text = (buildIdentityLine() + "\n\n" + StartupAuditRecorder.lastAuditText)
                .ifEmpty { tvBody.text?.toString().orEmpty() }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("BrainBuddy audit", text))
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnRefresh.setOnClickListener {
            lifecycleScope.launch {
                val storedVer = withContext(Dispatchers.IO) {
                    DbSeeder.readStoredSeedVersion(this@SeedAuditActivity)
                }
                tvStatus.text = buildIdentityLine(storedVer)

                if (!StartupRuntimeState.startupInitializationComplete) {
                    // Still read DB directly — do not block on startup gate
                    val liveText = withContext(Dispatchers.IO) {
                        StartupAuditRecorder.buildLiveReport(this@SeedAuditActivity)
                    }
                    val snap = StartupAuditRecorder.lastSnapshot
                    if (snap != null) {
                        tvCounts.text = formatPrimaryCounts(snap.total, snap.active, snap.inactive)
                        tvCounts.visibility = View.VISIBLE
                        tvBody.text = liveText
                    }
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    StartupAuditRecorder.buildLiveReport(this@SeedAuditActivity)
                }
                val snap = StartupAuditRecorder.lastSnapshot
                if (snap != null) {
                    tvCounts.text = formatPrimaryCounts(snap.total, snap.active, snap.inactive)
                    tvCounts.visibility = View.VISIBLE
                    tvBody.text = StartupAuditRecorder.formatAuditBodyForDisplay(
                        snap,
                        StartupAuditRecorder.auditFileAbsolutePath,
                        "REFRESHED"
                    )
                    tvPath.text = StartupAuditRecorder.auditFileAbsolutePath
                        ?: getString(R.string.seed_audit_path_unknown)
                }
            }
        }

        // Nuclear reset button — debug only, always visible in this screen
        val btnNuclear = try {
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNuclearReset)
        } catch (_: Exception) { null }

        btnNuclear?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("☢ Nuclear Reset")
                .setMessage("This will:\n1. Clear stored seed version\n2. Full deleteAll()\n3. Re-insert from assets\n4. Re-run quota enforcer\n5. Rebuild audit snapshot\n\nProceed?")
                .setPositiveButton("YES — WIPE & RESEED") { _, _ ->
                    lifecycleScope.launch {
                        tvStatus.text = "☢ NUCLEAR RESET IN PROGRESS..."
                        tvCounts.text = ""
                        tvBody.text = "Wiping DB and reseeding from assets..."

                        val result = withContext(Dispatchers.IO) {
                            // Step 1: explicitly clear stored version to force version-check bypass
                            try {
                                val meta = DatabaseProvider.get(this@SeedAuditActivity).appMetaDao()
                                meta.set(com.brainbuddy.app.db.AppMetaEntity("db_seed_version", "0"))
                                meta.set(com.brainbuddy.app.db.AppMetaEntity("db_seeded", "false"))
                                android.util.Log.w("NUCLEAR_RESET", "Stored version cleared to 0")
                            } catch (e: Exception) {
                                android.util.Log.e("NUCLEAR_RESET", "Failed to clear stored version: ${e.message}")
                            }

                            // Step 2: full wipe + reseed
                            val countBefore = try { DatabaseProvider.get(this@SeedAuditActivity).questionDao().countAll() } catch (_: Exception) { -1 }
                            android.util.Log.w("NUCLEAR_RESET", "countBefore=$countBefore")
                            val ok = DbSeeder.forceReseed(this@SeedAuditActivity)
                            val countAfterSeed = try { DatabaseProvider.get(this@SeedAuditActivity).questionDao().countAll() } catch (_: Exception) { -1 }
                            android.util.Log.w("NUCLEAR_RESET", "countAfterSeed=$countAfterSeed ok=$ok")

                            // Step 3: quota enforcer
                            PoolQuotaEnforcer.enforceCoreQuotas(this@SeedAuditActivity)

                            // Step 4: rebuild audit snapshot
                            val liveText = StartupAuditRecorder.buildLiveReport(this@SeedAuditActivity)
                            val snap = StartupAuditRecorder.lastSnapshot
                            val storedVer = DbSeeder.readStoredSeedVersion(this@SeedAuditActivity)

                            NuclearResult(
                                ok = ok,
                                countBefore = countBefore,
                                countAfterSeed = countAfterSeed,
                                total = snap?.total ?: -1,
                                active = snap?.active ?: -1,
                                inactive = snap?.inactive ?: -1,
                                inserted = DbSeeder.lastInsertedThisRun,
                                skipped = DbSeeder.lastSeedSkipped,
                                storedVer = storedVer,
                                liveText = liveText
                            )
                        }

                        tvStatus.text = buildIdentityLine(result.storedVer)
                        tvCounts.text = formatPrimaryCounts(result.total, result.active, result.inactive)
                        tvCounts.visibility = View.VISIBLE
                        tvBody.text = buildString {
                            appendLine("☢ NUCLEAR RESET ${if (result.ok) "COMPLETE ✅" else "FAILED ❌"}")
                            appendLine()
                            appendLine("countBefore=${result.countBefore}")
                            appendLine("countAfterSeed=${result.countAfterSeed}")
                            appendLine("insertedThisRun=${result.inserted}")
                            appendLine("seedSkipped=${result.skipped}")
                            appendLine("TOTAL=${result.total}")
                            appendLine("ACTIVE=${result.active}")
                            appendLine("INACTIVE=${result.inactive}")
                            appendLine()
                            appendLine("--- Live Audit ---")
                            appendLine(result.liveText)
                        }
                        tvPath.text = DbSeeder.getDatabasePath(this@SeedAuditActivity)
                    }
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .show()
        }

        // Populate stored seed version asynchronously
        lifecycleScope.launch {
            val storedVer = withContext(Dispatchers.IO) {
                DbSeeder.readStoredSeedVersion(this@SeedAuditActivity)
            }
            tvStatus.text = buildIdentityLine(storedVer)
            tvPath.text = DbSeeder.getDatabasePath(this@SeedAuditActivity)
        }

        lifecycleScope.launch {
            StartupRuntimeState.phase.collect { phase ->
                when (phase) {
                    is StartupRuntimeState.StartupPhase.Initializing -> {
                        tvCounts.text = ""
                        tvCounts.visibility = View.GONE
                        tvBody.text = getString(R.string.seed_audit_initializing)
                        btnCopy.isEnabled = false
                        btnRefresh.isEnabled = true // allow refresh even while initializing
                    }
                    is StartupRuntimeState.StartupPhase.Ready -> {
                        val snap = phase.payload.auditSnapshot
                        tvCounts.text = formatPrimaryCounts(snap.total, snap.active, snap.inactive)
                        tvCounts.visibility = View.VISIBLE
                        tvBody.text = StartupAuditRecorder.formatAuditBodyForDisplay(
                            snap,
                            phase.payload.auditPath,
                            "FINALIZED"
                        )
                        tvPath.text = phase.payload.auditPath
                            ?: getString(R.string.seed_audit_path_unknown)
                        btnCopy.isEnabled = true
                        btnRefresh.isEnabled = true
                    }
                }
            }
        }
    }

    private data class NuclearResult(
        val ok: Boolean,
        val countBefore: Int,
        val countAfterSeed: Int,
        val total: Int,
        val active: Int,
        val inactive: Int,
        val inserted: Int,
        val skipped: Boolean,
        val storedVer: String,
        val liveText: String
    )

    /**
     * Returns a single line showing exactly which build is running and seed version state.
     * This is the FIRST thing visible on screen — no async required for the build fields.
     */
    private fun buildIdentityLine(storedSeedVer: String = "loading..."): String = buildString {
        appendLine("=== INSTALLED BUILD ===")
        appendLine("VERSION_NAME=${BuildConfig.VERSION_NAME}")
        appendLine("VERSION_CODE=${BuildConfig.VERSION_CODE}")
        appendLine("BUILD_TYPE=${BuildConfig.BUILD_TYPE}")
        appendLine("CURRENT_DB_SEED_VERSION=${BuildConfig.DB_SEED_VERSION}")
        appendLine("STORED_DB_SEED_VERSION=$storedSeedVer")
        appendLine("seedSkipped=${DbSeeder.lastSeedSkipped}")
        appendLine("insertedThisRun=${DbSeeder.lastInsertedThisRun}")
        appendLine("=======================")
    }

    private fun formatPrimaryCounts(total: Int, active: Int, inactive: Int): String =
        "TOTAL: $total\nACTIVE: $active\nINACTIVE: $inactive"
}
