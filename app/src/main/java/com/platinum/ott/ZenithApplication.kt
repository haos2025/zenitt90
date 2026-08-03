package com.platinum.ott

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.worker.SeriesUpdateWorker
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ZenithApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        setupCrashHandler()
        // Раньше ни канал уведомлений не создавался, ни воркер не ставился
        // в очередь — SeriesUpdateWorker.enqueue() существовал, но был
        // мёртвым кодом, вызывавшимся из НИГДЕ.
        createNotificationChannel()
        SeriesUpdateWorker.enqueue(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(SeriesUpdateWorker.CHANNEL_ID, "Новые серии", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Уведомления о выходе новых серий избранных сериалов"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Единая точка настройки загрузки постеров на всё приложение (TV и телефон).
    // Без своего ImageLoader Coil использует дефолтные лимиты кэша, которые на
    // TV-экранах с сеткой из десятков карточек одновременно на экране приводят
    // к частым перезагрузкам одних и тех же постеров с сервера при скролле туда-обратно.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25) // до 25% доступной памяти приложения под декодированные постеры
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("poster_cache"))
                .maxSizeBytes(250L * 1024 * 1024) // 250 МБ на диске — постеры не перекачиваются каждый запуск
                .build()
        }
        .build()
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logDir = File(filesDir, "crash_logs"); logDir.mkdirs()
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val sw = StringWriter(); throwable.printStackTrace(PrintWriter(sw))
                File(logDir, "crash_$ts.txt").writeText("Thread: ${thread.name}\\n$sw")
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
