package com.brainbuddy.app

import android.app.Application
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.CrashRecoveryPrefs
import com.brainbuddy.app.report.ReportScheduler
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.ProtectionPrefs
import com.google.android.gms.ads.MobileAds
import com.brainbuddy.app.core.PermissionMonitorLauncher
import android.content.Intent
import android.os.Process

class BrainBuddyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        AppModeManager.registerLifecycle(this)
        PermissionMonitorLauncher.scheduleCheck(this)
        ReportScheduler.schedule(this)

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