package com.brainbuddy.app

import android.app.Application
import android.content.Intent
import android.os.Process
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.db.DbSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.CrashRecoveryPrefs
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.PermissionMonitorLauncher
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.league.LeagueScheduler
import com.brainbuddy.app.report.ReportScheduler
import com.google.android.gms.ads.MobileAds

class BrainBuddyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        // Ensure we always have a valid active profile on app start.
        ActiveProfileManager.getActiveProfileId(this)
        AppModeManager.registerLifecycle(this)
        PermissionMonitorLauncher.scheduleCheck(this)
        ReportScheduler.schedule(this)
        LeagueScheduler.scheduleNextReset(this)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            DbSeeder.seedIfNeeded(this@BrainBuddyApp)
        }

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                CrashRecoveryPrefs.recordCrash(this)
                if (CrashRecoveryPrefs.shouldDisableProtectionDueToCrashes(this)) {
                    CrashRecoveryPrefs.setProtectionDisabledByCrash(this, true)
                    ProtectionPrefs(this).setProtectionEnabled(false)
                    KillSwitchPrefs(this).deactivateKillSwitch()
                }
                val text = buildString {
                    append("CRASH!\n\n")
                    append(throwable.toString())
                    append("\n\n")
                    throwable.stackTrace.take(80).forEach {
                        append(it.toString()).append("\n")
                    }
                }.take(50_000) // Avoid TransactionTooLargeException

                val i = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("crash_text", text)
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

private fun exitProcess(code: Int): Nothing {
    kotlin.system.exitProcess(code)
}