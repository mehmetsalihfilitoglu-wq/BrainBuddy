package com.brainbuddy.app

import android.app.Application
import android.content.Intent
import android.os.Process

class BrainBuddyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val text = buildString {
                    append("CRASH!\n\n")
                    append(throwable.toString())
                    append("\n\n")
                    throwable.stackTrace.take(80).forEach {
                        append(it.toString()).append("\n")
                    }
                }

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

            // default handler'a bırakmadan direkt çıkıyoruz (yoksa sistem ekranı basıyor)
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}

private fun exitProcess(code: Int): Nothing {
    kotlin.system.exitProcess(code)
}