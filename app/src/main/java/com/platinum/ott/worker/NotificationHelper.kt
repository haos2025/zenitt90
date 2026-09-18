package com.platinum.ott.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// PROMPT_HOME_LOADING_FIX.md, п.2 — раньше здесь были ДВА параллельных
// канала уведомлений "о новых сериях": этот класс (CHANNEL_NEW_EPISODES /
// showNewEpisode(), мёртвый код, никем не вызывался) и отдельно
// SeriesUpdateWorker.notifyNewEpisode() со своим каналом "series_updates"
// (реально работает, вызывается из doWork()). Сравнили обе реализации —
// версия SeriesUpdateWorker полнее (проверяет "тихие часы", разрешение на
// уведомления, диплинк на сериал) — оставлена только она,
// CHANNEL_NEW_EPISODES/showNewEpisode() отсюда удалены как дубликат хуже
// качеством, а не как второй источник тех же уведомлений.
//
// CHANNEL_NEW_CONTENT остаётся — это единственный канал, реально нужный
// этому классу (PluginApi.showNewContent() зовёт именно его), но канал
// физически нигде не регистрировался (createChannels() не вызывался ни
// откуда — проверено grep по всему проекту), поэтому уведомление молча не
// показывалось на Android 8+ (создание в незарегистрированный канал не
// падает и не логируется). Вызов добавлен в ZenithApplication.onCreate().
object NotificationHelper {
    const val CHANNEL_NEW_CONTENT = "new_content"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_NEW_CONTENT, "Новый контент", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun showNewContent(context: Context, title: String) {
        // Раньше вызывался raw NotificationManager.notify() без проверки
        // разрешения — на Android 13+ (API 33) без POST_NOTIFICATIONS это
        // кидает SecurityException прямо в момент прихода уведомления от
        // плагина, а не мягко "не показывает". Та же защита, что уже есть
        // в SeriesUpdateWorker.notifyNewEpisode().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_CONTENT)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("Новинка")
            .setContentText(title)
            .setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
    }
}
