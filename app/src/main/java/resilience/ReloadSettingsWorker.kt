package com.brainbuddy.app.resilience

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

/**
 * Şimdilik no-op.
 * İleride: protection ayarlarını / JSON cache’i / blok listelerini burada yeniden yükleyebilirsin.
 */
class ReloadSettingsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        // Burada ileride ayarları restore edeceksin.
        return ListenableWorker.Result.success()
    }
}