package com.brainbuddy.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.core.OnboardingPrefs
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.DbSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.CrashRecoveryPrefs
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.PermissionMonitorLauncher
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.resilience.AccessibilityCheckWorker
import com.brainbuddy.app.resilience.AccessibilityMonitorService
import com.brainbuddy.app.league.LeagueScheduler
import com.brainbuddy.app.report.ReportScheduler
import com.google.android.gms.ads.MobileAds
import java.io.File

class BrainBuddyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        logStartupPersistenceSync(this)
        MobileAds.initialize(this)
        // Ensure we always have a valid active profile on app start.
        ActiveProfileManager.getActiveProfileId(this)
        AppModeManager.registerLifecycle(this)
        PermissionMonitorLauncher.scheduleCheck(this)
        if (ProtectionPrefs(this).isProtectionEnabledRaw()) {
            AccessibilityMonitorService.start(this)
            AccessibilityCheckWorker.schedulePeriodic(this)
        }
        ReportScheduler.schedule(this)
        LeagueScheduler.scheduleNextReset(this)

        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            Log.i(PERSISTENCE_LOG_TAG, "Seed started ASYNC (not awaited); UI may show before seed completes")
            withContext(Dispatchers.IO) {
                logStartupPersistenceAsync(this@BrainBuddyApp)
                val didSeed = DbSeeder.seedIfNeeded(this@BrainBuddyApp)
                Log.i(PERSISTENCE_LOG_TAG, "Seed finished async: didSeed=$didSeed")
                logRuntimeQuestionPoolSnapshot(this@BrainBuddyApp)
            }
        }

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                CrashRecoveryPrefs.recordCrash(this)
                if (CrashRecoveryPrefs.shouldDisableProtectionDueToCrashes(this)) {
                    CrashRecoveryPrefs.setProtectionDisabledByCrash(this, true)
                    ProtectionPrefs(this).setProtectionEnabled(false)
                    KillSwitchPrefs(this).deactivateKillSwitch()
                }
                val text = buildFullCrashReport(throwable)

                val i = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("crash_text", text)
                    putExtra("stack_trace", text)
                    putExtra("error_details", text)
                }
                startActivity(i)

                // Uygulamayı “temiz” kapatıp CrashActivity’nin görünmesini sağlıyoruz
                Thread.sleep(400)
            } catch (_: Exception) {
                // ignore
            }

            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}

private const val PERSISTENCE_LOG_TAG = "BrainBuddyPersistence"

/** Logs persistence values available on main thread (SharedPreferences, file existence). */
private fun logStartupPersistenceSync(context: Context) {
    try {
        val onboardingDone = OnboardingPrefs.isDone(context)
        val profileCount = ProfileStore(context).getProfiles().size
        val dataDir = context.applicationInfo?.dataDir ?: context.filesDir?.parent ?: "?"
        val sharedPrefsDir = File(dataDir, "shared_prefs")
        val prefsExists = sharedPrefsDir.exists() && sharedPrefsDir.isDirectory
        val onboardingPrefsFile = File(sharedPrefsDir, "bb_onboarding_prefs.xml")
        Log.i(PERSISTENCE_LOG_TAG, "Persistence at startup (sync): onboardingDone=$onboardingDone profileCount=$profileCount dataDir=$dataDir sharedPrefsDirExists=$prefsExists onboardingPrefsFileExists=${onboardingPrefsFile.exists()}")
    } catch (e: Exception) {
        Log.w(PERSISTENCE_LOG_TAG, "logStartupPersistenceSync failed", e)
    }
}

private suspend fun logStartupPersistenceAsync(context: Context) {
    try {
        val db = DatabaseProvider.get(context)
        val dbPath = context.getDatabasePath("brainbuddy.db")?.absolutePath ?: "?"
        val dbExists = context.getDatabasePath("brainbuddy.db")?.exists() ?: false
        val meta = db.appMetaDao()
        val dbSeeded = meta.get("db_seeded")
        val dbSeedVersion = meta.get("db_seed_version")
        val questionCount = db.questionDao().countAll()
        Log.i(PERSISTENCE_LOG_TAG, "Persistence at startup (async): dbPath=$dbPath dbExists=$dbExists db_seeded=$dbSeeded db_seed_version=$dbSeedVersion questionCount=$questionCount")
    } catch (e: Exception) {
        Log.w(PERSISTENCE_LOG_TAG, "logStartupPersistenceAsync failed", e)
    }
}

/**
 * DEBUG-ONLY: logs a snapshot of the real Room question pool after seeding.
 *
 * This runs only on app startup and has no functional impact; it just prints:
 * - total DB row count
 * - active row count
 * - sample candidate pool sizes for:
 *   grade 6 MAT, grade 4 ING, LGS MAT.
 */
private suspend fun logRuntimeQuestionPoolSnapshot(context: Context) {
    if (!BuildConfig.DEBUG) return
    try {
        val db = DatabaseProvider.get(context)
        val dao = db.questionDao()
        val total = dao.countAll()
        val active = dao.countAllActive()
        val g6Mat = dao.getCandidatePoolByGradeSubject(6, "mat").size
        val g4Ing = dao.getCandidatePoolByGradeSubject(4, "ing").size
        val lgsMat = dao.getCandidatePoolByLgsSubject("mat").size
        Log.i(
            PERSISTENCE_LOG_TAG,
            "RUNTIME_SEED_DB total=$total active=$active g6_mat_candidates=$g6Mat g4_ing_candidates=$g4Ing lgs_mat_candidates=$lgsMat"
        )
    } catch (e: Exception) {
        Log.w(PERSISTENCE_LOG_TAG, "logRuntimeQuestionPoolSnapshot failed", e)
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