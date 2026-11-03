package com.bnyro.wallpaper.util

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bnyro.wallpaper.sync.auth.NurvaRepository
import java.util.concurrent.TimeUnit

/**
 * Periodically discovers newer group revisions and applies them when this device is set to
 * automatic mode. Network constraints defer work while offline; the Room outbox preserves
 * work and the worker applies bounded exponential retry after transient failures.
 */
class NurvaSyncWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(applicationContext, workerParameters) {

    override suspend fun doWork(): Result {
        val repository = NurvaRepository(applicationContext)
        if (!repository.isConfigured() || repository.currentSession() == null) return Result.success()
        return try {
            repository.syncAndApplyAvailableWallpapers()
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Nurva background sync failed; WorkManager will retry.", error)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NurvaSyncWorker"
        private const val PERIODIC_NAME = "nurva-automatic-wallpaper-sync"
        private const val IMMEDIATE_NAME = "nurva-immediate-wallpaper-sync"

        private fun connectedConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** WorkManager's minimum periodic interval is 15 minutes. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<NurvaSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connectedConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Called on sign-in and when device sync preferences are changed. */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NurvaSyncWorker>()
                .setConstraints(connectedConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
