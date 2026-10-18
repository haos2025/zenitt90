package com.platinum.ott.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum.ott.core.SessionGraph
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Фоновое обновление программы передач раз в 12 часов (только при наличии сети):
 * url-tvg плейлистов, xmltv.php Xtream, при их отсутствии — запасные источники
 * (EpgFallbackSources). Каналы без tvg-id сопоставляются по названию автоматически.
 * Тот же принцип @HiltWorker, что у EpgCleanupWorker.
 */
@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val sessionGraph: SessionGraph
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = try {
        sessionGraph.playlistSourceRepository.refreshEpgOnly()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("epg_refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
