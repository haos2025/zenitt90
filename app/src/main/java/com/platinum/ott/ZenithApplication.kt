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
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.platinum.ott.worker.SeriesUpdateWorker
import com.platinum.ott.worker.ChannelHealthCheckWorker
import com.platinum.ott.worker.EpgCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class ZenithApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    // Field injection, не конструктор — как и все @Inject lateinit var в
    // Application/Activity, заполняется Hilt-ом ДО выполнения тела onCreate()
    // ниже, за счёт сгенерированного базового класса (см. официальную
    // документацию Hilt "Use Hilt with other Jetpack libraries").
    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    // Дефолтный автоинициализатор WorkManager отключён в AndroidManifest.xml
    // (androidx.startup.InitializationProvider, tools:node="remove" на
    // androidx.work.WorkManagerInitializer) — без этого WorkManager успел бы
    // проинициализироваться со штатной WorkerFactory (без DI) раньше, чем
    // сработает on-demand-инициализация через Configuration.Provider ниже, и
    // SeriesUpdateWorker остался бы без внедрённых зависимостей.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // SessionGraph (замена ServiceLocator) — Hilt-синглтон, создаётся
        // лениво сам при первом инжекте (первый экран, который его
        // запросит — обычно HomeViewModel, т.к. Home стартовый экран), явный
        // init()-вызов здесь больше не нужен.
        setupCrashHandler()
        reportLastCrash()
        // Раньше ни канал уведомлений не создавался, ни воркер не ставился
        // в очередь — SeriesUpdateWorker.enqueue() существовал, но был
        // мёртвым кодом, вызывавшимся из НИГДЕ.
        createNotificationChannel()
        // PROMPT_HOME_LOADING_FIX.md, п.2 — CHANNEL_NEW_CONTENT (уведомления
        // о новом контенте от плагинов, PluginApi.showNewContent()) нигде не
        // регистрировался, поэтому такие уведомления молча не показывались.
        com.platinum.ott.worker.NotificationHelper.createChannels(this)
        SeriesUpdateWorker.enqueue(this)
        ChannelHealthCheckWorker.enqueue(this)
        EpgCleanupWorker.enqueue(this)
        com.platinum.ott.worker.EpgRefreshWorker.enqueue(this)
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
                pruneOldCrashLogs(logDir)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    // Краш-логи лежат во внутренней памяти (filesDir/crash_logs) — без компьютера их не достать.
    // При запуске копируем самый свежий ещё не показанный отчёт в буфер обмена, чтобы его можно
    // было вставить в чат. Java-обработчик не видит native-падения и убийства по памяти — для них
    // берём причину последнего завершения процесса (Android 11+).
    // Номера причин ApplicationExitInfo: 2 SIGNALED, 3 LOW_MEMORY, 4 CRASH, 5 CRASH_NATIVE,
    // 6 ANR, 7 INITIALIZATION_FAILURE.
    private fun reportLastCrash() {
        try {
            val prefs = getSharedPreferences("crash_report", MODE_PRIVATE)
            val lastReported = prefs.getLong("last_reported", 0L)
            var newest = lastReported
            val sb = StringBuilder()
            val latest = File(filesDir, "crash_logs").listFiles()?.maxByOrNull { it.lastModified() }
            if (latest != null && latest.lastModified() > lastReported) {
                sb.append("JAVA CRASH ").append(latest.name).append('\n')
                    .append(latest.readText().take(3500)).append('\n')
                newest = maxOf(newest, latest.lastModified())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val am = getSystemService(android.app.ActivityManager::class.java)
                for (info in am.getHistoricalProcessExitReasons(packageName, 0, 3)) {
                    if (info.timestamp > lastReported && info.reason in intArrayOf(2, 3, 4, 5, 6, 7)) {
                        sb.append("EXIT reason=").append(info.reason).append(" status=").append(info.status)
                            .append(" desc=").append(info.description).append(" at=").append(Date(info.timestamp)).append('\n')
                        newest = maxOf(newest, info.timestamp)
                    }
                }
            }
            if (sb.isEmpty()) return
            prefs.edit().putLong("last_reported", newest).apply()
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Zenith crash", sb.toString()))
            android.os.Handler(mainLooper).post {
                android.widget.Toast.makeText(this, "Отчёт об аварии скопирован в буфер обмена", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {}
    }

    // Раньше файлов в crash_logs/ писалось сколько угодно, ничего никогда
    // не удалялось — на устройстве, которое годами крашится время от
    // времени (например, слабый TV-чип), папка растёт неограниченно.
    // Держим только последние MAX_CRASH_LOGS, старые удаляются сразу после
    // записи нового — не нужен отдельный шаг при старте приложения, момент
    // краша и так уже "фоновый" код.
    private fun pruneOldCrashLogs(logDir: File) {
        val files = logDir.listFiles() ?: return
        if (files.size <= MAX_CRASH_LOGS) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CRASH_LOGS)
            .forEach { it.delete() }
    }

    companion object {
        private const val MAX_CRASH_LOGS = 20
    }
}
