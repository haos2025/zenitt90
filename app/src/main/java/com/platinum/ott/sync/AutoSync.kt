package com.platinum.ott.sync

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.data.local.ZenithDatabase
import com.platinum.ott.worker.AutoSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Расписание автосинхронизации (план «Мои устройства», п. 3.6):
 *  - раз в 30 минут (минимум WorkManager — 15) при наличии сети;
 *  - при запуске приложения;
 *  - вскоре после изменения избранного/истории/удалений: любое изменение этих таблиц
 *    запускает одну синхронизацию через ~45 секунд (пока она ждёт или идёт, новые
 *    изменения новых запусков не создают — их подхватит следующий запуск).
 * Если нет сети, WorkManager сам дождётся её появления.
 */
object AutoSync {
    private const val PERIODIC = "auto_sync_periodic"
    private const val SOON = "auto_sync_soon"
    private const val PERIOD_MINUTES = 30L
    private const val DELAY_SECONDS = 45L

    private val needNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun enqueuePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoSyncWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(needNetwork)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Одна синхронизация «скоро». KEEP: уже ожидающий/идущий запуск не сбрасываем и не отменяем. */
    fun requestSoon(context: Context, delaySeconds: Long = DELAY_SECONDS) {
        val request = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(needNetwork)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.KEEP, request)
    }

    /** Подписка на изменения таблиц. Вызывать НЕ с главного потока (Room открывает базу). */
    fun observeChanges(context: Context, database: ZenithDatabase, prefs: AuthPreferences) {
        val appContext = context.applicationContext
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("favorites", "watch_history", "sync_tombstones") {
                override fun onInvalidated(tables: Set<String>) {
                    // Собственные записи синхронизации новый запуск не порождают.
                    if (SyncRepositoryImpl.isRunning) return
                    if (!prefs.autoSyncEnabled || prefs.v2Token.isNullOrBlank()) return
                    requestSoon(appContext)
                }
            }
        )
    }
}
