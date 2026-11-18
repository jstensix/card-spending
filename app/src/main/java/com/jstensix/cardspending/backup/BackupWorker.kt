package com.jstensix.cardspending.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jstensix.cardspending.CardSpendingApp

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CardSpendingApp).container
        return when (container.backupManager.backupNow()) {
            // Network hiccups get a few retries; auth problems wait for the user (the main screen shows a warning).
            is BackupResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}
