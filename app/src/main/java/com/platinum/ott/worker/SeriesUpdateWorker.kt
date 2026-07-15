package com.platinum.ott.worker

import android.content.Context
import androidx.work.*
import com.platinum.ott.core.ServiceLocator
import java.util.concurrent.TimeUnit

class SeriesUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val tmdb = ServiceLocator.tmdbRepository
            val dao = ServiceLocator.database.seriesScheduleDao()
            // Update series schedules from TMDB
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SeriesUpdateWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("series_update", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
