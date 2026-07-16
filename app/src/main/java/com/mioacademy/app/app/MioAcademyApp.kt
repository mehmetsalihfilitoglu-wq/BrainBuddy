package com.mioacademy.app.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.mioacademy.app.CrashActivity
import com.mioacademy.app.core.ActiveProfileManager
import com.mioacademy.app.core.OnboardingPrefs
import com.mioacademy.app.core.ProfileStore
import com.mioacademy.app.db.DataIntegrityChecker
import com.mioacademy.app.db.DatabaseProvider
import com.mioacademy.app.db.DbSeeder
import com.mioacademy.app.db.StartupAuditRecorder
import com.mioacademy.app.db.StartupRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mioacademy.app.league.LeagueScheduler
import java.io.File

class MioAcademyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        logStartupPersistenceSync(this)
        // Ensure a valid profile ID exists on first launch (single-profile mode).
        ActiveProfileManager.getActiveProfileId(this)
        LeagueScheduler.scheduleNextReset(this)
        // Safe local study reminders (real-data gated inside the worker).
        com.mioacademy.app.notification.NotificationScheduler.schedule(this)

        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            StartupRuntimeState.markInitializing()
            Log.i(STARTUP_LOG_TAG, "Startup: seed → audit → publish (sequential IO)")
            val payload = withContext(Dispatchers.IO) {
                logStartupPersistenceAsync(this@MioAcademyApp)
                val didSeed = DbSeeder.seedIfNeeded(this@MioAcademyApp)
                Log.i(STARTUP_LOG_TAG, "Seed finished: didSeed=$didSeed")
                val integrityResult = DataIntegrityChecker.runCleanup(this@MioAcademyApp)
                Log.i(STARTUP_LOG_TAG, "IntegrityCheck: hardDeleted=${integrityResult.hardDeleted} totalMarked=${integrityResult.totalMarked}")
                // Seed the isolated official IMAT bank AFTER integrity cleanup so those
                // official items are never touched by the K-12/LGS-tuned cleanup pass.
                val imatSeeded = DbSeeder.seedImatIfNeeded(this@MioAcademyApp)
                Log.i(STARTUP_LOG_TAG, "IMAT seed: inserted=$imatSeeded")
                // Seed the isolated Mioitalia ORIGINAL bank the same way (own pool, own version).
                val mioitaliaSeeded = DbSeeder.seedMioitaliaIfNeeded(this@MioAcademyApp)
                Log.i(STARTUP_LOG_TAG, "Mioitalia seed: inserted=$mioitaliaSeeded")
                // Seed the isolated TIL-I & CEnT-S Daily Challenge banks (own pools, own versions).
                // Only production-eligible, semantically-verified questions ship in these assets.
                val tilSeeded = DbSeeder.seedTilIIfNeeded(this@MioAcademyApp)
                val centsSeeded = DbSeeder.seedCentsIfNeeded(this@MioAcademyApp)
                Log.i(STARTUP_LOG_TAG, "TIL-I seed: inserted=$tilSeeded | CEnT-S seed: inserted=$centsSeeded")
                val p = StartupAuditRecorder.finalizeStartupAudit(this@MioAcademyApp)
                Log.i(
                    "AppStartupAudit",
                    "AppStartupAudit: total=${p.auditSnapshot.total} active=${p.auditSnapshot.active} inactive=${p.auditSnapshot.inactive}"
                )
                p
            }
            StartupRuntimeState.publishReady(payload)
        }

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val text = buildFullCrashReport(throwable)
                val i = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("crash_text", text)
                    putExtra("stack_trace", text)
                    putExtra("error_details", text)
                }
                startActivity(i)
                Thread.sleep(400)
            } catch (_: Exception) {
                // ignore
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}

private const val STARTUP_LOG_TAG = "MioAcademyStartup"

private fun logStartupPersistenceSync(context: Context) {
    try {
        val onboardingDone = OnboardingPrefs.isDone(context)
        val profileCount = ProfileStore(context).getProfiles().size
        val dataDir = context.applicationInfo?.dataDir ?: context.filesDir?.parent ?: "?"
        val sharedPrefsDir = File(dataDir, "shared_prefs")
        val prefsExists = sharedPrefsDir.exists() && sharedPrefsDir.isDirectory
        val onboardingPrefsFile = File(sharedPrefsDir, "bb_onboarding_prefs.xml")
        Log.i(STARTUP_LOG_TAG, "Persistence at startup (sync): onboardingDone=$onboardingDone profileCount=$profileCount dataDir=$dataDir sharedPrefsDirExists=$prefsExists onboardingPrefsFileExists=${onboardingPrefsFile.exists()}")
    } catch (e: Exception) {
        Log.w(STARTUP_LOG_TAG, "logStartupPersistenceSync failed", e)
    }
}

private suspend fun logStartupPersistenceAsync(context: Context) {
    try {
        val db = DatabaseProvider.get(context)
        // Note: database file keeps legacy name "brainbuddy.db" for existing install compatibility
        val dbPath = context.getDatabasePath("brainbuddy.db")?.absolutePath ?: "?"
        val dbExists = context.getDatabasePath("brainbuddy.db")?.exists() ?: false
        val meta = db.appMetaDao()
        val dbSeeded = meta.get("db_seeded")
        val dbSeedVersion = meta.get("db_seed_version")
        val questionCount = db.questionDao().countAll()
        Log.i(STARTUP_LOG_TAG, "Persistence at startup (async): dbPath=$dbPath dbExists=$dbExists db_seeded=$dbSeeded db_seed_version=$dbSeedVersion questionCount=$questionCount")
    } catch (e: Exception) {
        Log.w(STARTUP_LOG_TAG, "logStartupPersistenceAsync failed", e)
    }
}

private fun buildFullCrashReport(throwable: Throwable): String = buildString {
    val STACK_LINES = 60
    var t: Throwable? = throwable
    var depth = 0
    while (t != null) {
        val prefix = if (depth == 0) "" else "Caused by: "
        append(prefix).append(t.javaClass.name)
        t.message?.let { msg -> append(": ").append(msg) }
        append("\n\n")
        val trace = t.stackTrace
        val linesToShow = minOf(STACK_LINES, trace.size)
        for (i in 0 until linesToShow) {
            append("\tat ").append(trace[i].toString()).append("\n")
        }
        if (trace.size > STACK_LINES) {
            append("\t... ").append(trace.size - STACK_LINES).append(" more\n")
        }
        append("\n")
        t = t.cause
        depth++
    }
}.take(50_000)

private fun exitProcess(code: Int): Nothing {
    kotlin.system.exitProcess(code)
}
