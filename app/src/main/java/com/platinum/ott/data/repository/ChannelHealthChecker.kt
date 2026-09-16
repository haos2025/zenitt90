package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.ChannelDao
import com.platinum.ott.data.local.dao.ChannelStreamDao
import com.platinum.ott.data.local.entity.ChannelStreamEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Базовый интервал повторной проверки для "unknown"/"alive" стрима — не
// имеет смысла проверять чаще, живой эфир не меняет статус каждую минуту.
private val BASE_RECHECK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)
// Потолок бэкоффа для стрима, который уже много раз подряд не ответил —
// не проверяем чаще раза в сутки, сколько бы неудач подряд ни накопилось.
private val MAX_RECHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24)
// Health-check должен быть быстрым по своей природе (это не факт
// воспроизведения, только проверка доступности) — отдельный клиент с
// коротким таймаутом, независимый от пользовательской настройки таймаута
// API (networkPreferences), которая рассчитана на реальные запросы каталога.
private const val CHECK_TIMEOUT_SECONDS = 6L
// Не проверяем больше нескольких стримов параллельно одновременно — на
// слабом TV-чипе/плохом Wi-Fi десятки одновременных соединений на разные
// хосты создают собственную нагрузку, которая мешает точно тому
// воспроизведению, ради которого весь health-check и затевался.
private const val MAX_CONCURRENT_CHECKS = 4

/**
 * PROMPT_IPTV_FOUNDATION.md, подзадача "health-check" — тот же принцип
 * backoff/изоляции, что и на бэкенде (plugins/health.py, call_with_isolation),
 * продублированный на Kotlin-стороне (решение сессии: offline-first, без
 * переноса личных M3U/Xtream-данных пользователя на бэкенд).
 *
 * Намеренно НЕ проверяет каждый ChannelStream каждого канала подряд —
 * только у ПОДПИСАННЫХ каналов (isSubscribed = true): непросмотренные
 * кандидаты на подписку/слияние не нужны для воспроизведения прямо сейчас,
 * тратить на них сетевые запросы и время устройства смысла нет.
 */
class ChannelHealthChecker(
    private val channelDao: ChannelDao,
    private val channelStreamDao: ChannelStreamDao
) {
    // retryOnConnectionFailure(true) — тот же аргумент, что уже применён
    // для mediaHttpClient в PlayerViewModel.kt (архивная нестабильность
    // некоторых IPTV-хостов, не только archive.org).
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Проверяет стримы всех подписанных каналов, уважая бэкофф (см.
     * isDue() ниже) — вызывается и из ChannelHealthCheckWorker (по
     * расписанию), и напрямую из UI ("Проверить сейчас", следующий
     * коммит ChannelsViewModel).
     */
    suspend fun checkSubscribedChannels() = withContext(Dispatchers.IO) {
        val subscribed = channelDao.getSubscribed()
        val due = subscribed
            .flatMap { channelStreamDao.getByChannelId(it.id) }
            .filter { isDue(it) }

        // Чанками по MAX_CONCURRENT_CHECKS, не все сразу через
        // awaitAll(due.map{...}) — тот же аргумент, что и в комментарии
        // у константы выше: ограничиваем реальный параллелизм, а не
        // просто запускаем и забываем.
        due.chunked(MAX_CONCURRENT_CHECKS).forEach { chunk ->
            chunk.map { stream -> async { checkOne(stream) } }.awaitAll()
        }
    }

    /** Разовая проверка одного канала — открыть его настройки/попытаться воспроизвести уже достаточный сигнал "проверь сейчас". */
    suspend fun checkChannel(channelId: String) = withContext(Dispatchers.IO) {
        channelStreamDao.getByChannelId(channelId).map { stream -> async { checkOne(stream) } }.awaitAll()
    }

    private suspend fun checkOne(stream: ChannelStreamEntity) {
        val alive = probe(stream)
        val newFailures = if (alive) 0 else stream.consecutiveFailures + 1
        channelStreamDao.updateCheckResult(
            id = stream.id,
            timestamp = System.currentTimeMillis(),
            status = if (alive) "alive" else "dead",
            consecutiveFailures = newFailures
        )
    }

    /**
     * "Живой" — это HTTP 200/206 на попытку начать читать поток, не
     * успешная докачка целиком (это бы качало сами каналы вхолостую).
     * Range с самого первого байта: большинство IPTV-раздатчиков (Xtream/
     * nginx-rtmp/обычный nginx для .ts-сегментов) поддерживают Range и
     * отвечают 206 без отдачи всего файла; если Range не поддержан —
     * обычный 200 тоже считается успехом (см. isSuccessful ниже, не
     * привязано к конкретному коду). HEAD не используется — часть
     * реальных IPTV-раздатчиков на него не отвечает вовсе (405/таймаут),
     * хотя GET у них рабочий.
     */
    private fun probe(stream: ChannelStreamEntity): Boolean {
        return try {
            val builder = Request.Builder().url(stream.streamUrl).header("Range", "bytes=0-1")
            stream.userAgent?.let { builder.header("User-Agent", it) }
            stream.referrer?.let { builder.header("Referer", it) }
            client.newCall(builder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Экспоненциальный бэкофф от consecutiveFailures, тот же принцип
     * "изоляции", что и на бэкенде — стрим, который уже много раз подряд
     * не ответил, проверяется всё реже, не наравне со свежим/живым.
     * Ни разу не проверенный (lastCheckedAt == null) — всегда due сразу.
     */
    private fun isDue(stream: ChannelStreamEntity): Boolean {
        val lastChecked = stream.lastCheckedAt ?: return true
        val interval = (BASE_RECHECK_INTERVAL_MS * (1L shl stream.consecutiveFailures.coerceAtMost(10)))
            .coerceAtMost(MAX_RECHECK_INTERVAL_MS)
        return System.currentTimeMillis() - lastChecked >= interval
    }
}
