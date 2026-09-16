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
 * PROMPT_IPTV_FOUNDATION.md, подзадача "health-check" — тот же принцип
 * @HiltWorker + @AssistedInject, что и SeriesUpdateWorker.kt (см. его же
 * комментарий про HiltWorkerFactory/отключённый автоинициализатор
 * WorkManager — эта настройка на всё приложение одна, второй раз её
 * описывать не нужно).
 *
 * 30 минут, не 15 (BASE_RECHECK_INTERVAL_MS в ChannelHealthChecker) —
 * сам воркер не обязан бегать с частотой самого короткого возможного
 * интервала проверки: isDue() внутри checker'а и так пропустит стримы,
 * которым ещё рано, лишний более частый запуск воркера просто чаще делал
 * бы одно и то же "почти ничего не due" вхолостую.
 */
@HiltWorker
class ChannelHealthCheckWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    sessionGraph: SessionGraph
) : CoroutineWorker(ctx, params) {
    private val checker = sessionGraph.channelHealthChecker

    override suspend fun doWork(): Result = try {
        checker.checkSubscribedChannels()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChannelHealthCheckWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("channel_health_check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
