package com.controlremoto.companion

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WatchdogScheduler {
    private const val TAG = "WatchdogScheduler"

    fun schedule(context: Context) {
        Log.i(TAG, "schedule() called")
        try {
            val request = PeriodicWorkRequestBuilder<TailscaleWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TailscaleWatchdogWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "enqueueUniquePeriodicWork call completed without exception")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule watchdog: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}