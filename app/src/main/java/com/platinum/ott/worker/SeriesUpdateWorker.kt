package com.platinum.ott.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.platinum.ott.MainActivity
import com.platinum.ott.core.ServiceLocator
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// Раньше doWork() читал tmdbRepository/seriesScheduleDao в локальные val
// и ничего с ними не делал (Result.success() сразу же) — по факту это
// была пустая функция. И enqueue() нигде не вызывался, так что даже
// пустая версия никогда не запускалась.
class SeriesUpdateWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val notifPrefs = ServiceLocator.notificationPreferences
        if (!notifPrefs.isNewEpisodesEnabled()) return Result.success()
        return try {
            val tracker = ServiceLocator.seriesTrackerUseCase
            // "Новые серии" отслеживает избранные сериалы (contentType == "SERIES"),
            // не весь каталог целиком — как и подписки на сериалы в других
            // подобных приложениях.
            val seriesFavorites = ServiceLocator.favoritesUseCase.getByType("SERIES").first()
            for (fav in seriesFavorites) {
                val updated = tracker.updateSchedule(fav.contentId, fav.title) ?: continue
                val now = System.currentTimeMillis()
                // Уведомляем только если серия вышла недавно (в пределах суток
                // с последнего опроса раз в 12 часов) — а не о любой дате в
                // будущем, которую видим впервые.
                if (updated.nextEpisodeDate in (now - TimeUnit.HOURS.toMillis(24))..now) {
                    notifyNewEpisode(fav.title, updated.seasonNum, updated.episodeNum, fav.contentId)
                }
            }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    private fun notifyNewEpisode(seriesTitle: String, season: Int, episode: Int, contentId: String) {
        if (ServiceLocator.notificationPreferences.isQuietNow()) return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // Открывает просто главный экран, а не сразу карточку сериала —
        // диплинк на конкретный контент потребовал бы отдельной обработки
        // intent-экстра в NavHost, которой сейчас в проекте нет; не стал
        // симулировать переход, которого на самом деле не будет.
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(ctx, contentId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(seriesTitle)
            .setContentText("Новая серия: сезон $season, серия $episode")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(contentId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "series_updates"
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<SeriesUpdateWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("series_update", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
