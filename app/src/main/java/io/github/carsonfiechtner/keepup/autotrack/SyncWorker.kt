package io.github.carsonfiechtner.keepup.autotrack

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Hourly background sync. Does nothing if no habit uses an automatic source. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun autoTrackingSync(): AutoTrackingSync
    }

    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, SyncEntryPoint::class.java).autoTrackingSync()
        sync.syncAll()
        return Result.success()
    }

    companion object {
        private const val NAME = "auto-tracking-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
