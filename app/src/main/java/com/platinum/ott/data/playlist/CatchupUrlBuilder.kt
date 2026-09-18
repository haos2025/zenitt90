package com.platinum.ott.data.playlist

import com.platinum.ott.data.local.entity.ChannelStreamEntity
import com.platinum.ott.data.local.entity.PlaylistSourceEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * PROMPT_EPG.md, подзадача 5 (timeshift/catch-up) — строит ссылку на архив
 * ОДНОГО стрима на ОДИН промежуток времени. catchupDays == 0 — стрим не
 * заявляет поддержку архива, build() вернёт null (GetPlayableUrlUseCase уже
 * фильтрует такие стримы заранее, эта проверка — на случай прямого вызова).
 *
 * ДОПУЩЕНИЕ (честно: ни одна из двух веток не проверена на реальной
 * панели/плейлисте, доступа к провайдеру с архивом под рукой не было) —
 * оба формата ниже распространённые, документированные конвенции в
 * экосистеме M3U/Xtream-плееров, но провайдеров с несовместимыми
 * вариантами в этой экосистеме достаточно много. Если реальная ссылка не
 * воспроизводится — первое, что стоит сверить, это как именно ВАША
 * панель/плейлист ожидает такую ссылку, и поправить только этот файл, не
 * архитектуру вокруг него (GetPlayableUrlUseCase просто просит "ссылку на
 * такой-то стрим на такой-то промежуток", не знает, как она строится).
 */
object CatchupUrlBuilder {
    fun build(stream: ChannelStreamEntity, source: PlaylistSourceEntity, startMillis: Long, endMillis: Long): String? {
        if (stream.catchupDays <= 0) return null
        return when (source.type) {
            "xtream" -> buildXtream(stream, source, startMillis, endMillis)
            "m3u" -> buildM3u(stream, startMillis, endMillis)
            else -> null
        }
    }

    // "/streaming/timeshift.php?username=U&password=P&stream=ID&start=YYYY-MM-DD:HH-MM&duration=MIN"
    // — документированный эндпоинт Xtream Codes для архива живого эфира.
    // Существует и альтернативный путь у части панелей
    // ("/timeshift/U/P/DURATION/YYYY-MM-DD:HH-MM/ID.ts") — реализован
    // только этот, более распространённый в документации; если у
    // конкретной панели именно он не работает, стоит попробовать второй.
    private fun buildXtream(stream: ChannelStreamEntity, source: PlaylistSourceEntity, startMillis: Long, endMillis: Long): String? {
        val host = source.host ?: return null
        val user = source.username ?: return null
        val pass = source.password ?: return null
        val streamId = stream.externalStreamId ?: return null
        val base = host.trimEnd('/')
        val startFormat = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(endMillis - startMillis).coerceAtLeast(1)
        return "$base/streaming/timeshift.php?username=$user&password=$pass&stream=$streamId&start=${startFormat.format(startMillis)}&duration=$durationMinutes"
    }

    // Два варианта: явный catchup-source из M3U (плейсхолдеры ${start}/${end}
    // в unix-секундах — стандартные имена плейсхолдеров для catchup-source
    // в этой экосистеме) — подставляются как есть; без catchup-source —
    // обобщённая "shift"-конвенция (&utc=<start>&lutc=<end> к обычной
    // ссылке потока), самый частый вариант для плейлистов без явного
    // catchup-source.
    private fun buildM3u(stream: ChannelStreamEntity, startMillis: Long, endMillis: Long): String {
        val startSec = TimeUnit.MILLISECONDS.toSeconds(startMillis)
        val endSec = TimeUnit.MILLISECONDS.toSeconds(endMillis)
        val template = stream.catchupTemplate
        return if (!template.isNullOrBlank()) {
            template.replace("\${start}", startSec.toString()).replace("\${end}", endSec.toString())
        } else {
            val separator = if (stream.streamUrl.contains("?")) "&" else "?"
            "${stream.streamUrl}${separator}utc=$startSec&lutc=$endSec"
        }
    }
}
