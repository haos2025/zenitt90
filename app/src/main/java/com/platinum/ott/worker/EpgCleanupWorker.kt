package com.platinum.ott.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum.ott.core.SessionGraph
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * PROMPT_EPG.md, подзадача 1 — тот же принцип @HiltWorker + @AssistedInject,
 * что и SeriesUpdateWorker.kt/ChannelHealthCheckWorker.kt (см. комментарий в
 * SeriesUpdateWorker.kt про HiltWorkerFactory/отключённый автоинициализатор
 * WorkManager, на всё приложение он один).
 *
 * 6 часов, не чаще — прошлый край окна двигается медленно (−2ч), незачем
 * гонять чистку так же часто, как health-check каналов (30 минут); не реже
 * тоже нежелательно — до появления парсеров (подзадача 3) таблица пустая,
 * но как только они начнут писать сюда полное расписание при каждом
 * refresh() источника, старые слоты не удаляются сами по себе
 * (см. комментарий в EpgProgramDao.kt про upsert vs delete), и без
 * регулярной чистки строки за пределами окна копились бы бессрочно.
 */
@HiltWorker
class EpgCleanupWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    sessionGraph: SessionGraph
) : CoroutineWorker(ctx, params) {
    private val epgProgramRepository = sessionGraph.epgProgramRepository

    override suspend fun doWork(): Result = try {
        epgProgramRepository.cleanupOutsideWindow()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpgCleanupWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("epg_cleanup", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
