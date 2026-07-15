package com.platinum.ott.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_NEW_EPISODES = "new_episodes"
    const val CHANNEL_NEW_CONTENT = "new_content"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL_NEW_EPISODES, "Новые серии", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_NEW_CONTENT, "Новый контент", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun showNewEpisode(context: Context, seriesName: String, season: Int, episode: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_EPISODES)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("Новая серия $seriesName")
            .setContentText("S${"%02d".format(season)}E${"%02d".format(episode)} выходит завтра!")
            .setAutoCancel(true).build()
        nm.notify(seriesName.hashCode(), notification)
    }

    fun showNewContent(context: Context, title: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_NEW_CONTENT)
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentTitle("Новинка")
            .setContentText(title)
            .setAutoCancel(true).build()
        nm.notify(title.hashCode(), notification)
    }
}
