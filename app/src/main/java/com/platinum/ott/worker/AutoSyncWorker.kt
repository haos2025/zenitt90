package com.platinum.ott.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.SessionGraph
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Фоновая синхронизация избранного и истории через сервер («Мои устройства»).
 * Тот же принцип @HiltWorker + @AssistedInject, что и у SeriesUpdateWorker.
 *
 * Работает только если устройство уже зарегистрировано на сервере (то есть пользователь
 * хотя бы раз синхронизировал вручную или связал устройства) и автосинхронизация не выключена —
 * сама по себе, без действия пользователя, приложение ничего на сервер не отправляет.
 */
@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val sessionGraph: SessionGraph,
    private val prefs: AuthPreferences
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!prefs.autoSyncEnabled || prefs.v2Token.isNullOrBlank()) return Result.success()
        val result = sessionGraph.syncRepository.sync("auto")
        // Нет сети / сервер просыпается — пробуем ещё пару раз с нарастающей паузой, дальше ждём следующего срока.
        return if (result.isSuccess || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
