package com.platinum.ott.data.playlist

import com.platinum.ott.data.local.entity.PlaylistMovieEntity

/**
 * Парсер M3U-плейлиста в список PlaylistMovieEntity. Формат — построчный
 * текстовый стандарт: строка "#EXTINF:-1 tvg-logo="..." group-title="...",Название"
 * затем следующей непустой строкой — сама ссылка на поток.
 *
 * id строится как "m3u_" + порядковый индекс — стабилен, пока провайдер не
 * переупорядочит плейлист; если переупорядочит — избранное/история,
 * привязанные к старому id, перестанут находить совпадение. Осознанный
 * компромисс ради простоты первой версии, не хэш от URL.
 *
 * ДОБАВЛЕНО: между #EXTINF и URL многие реальные плейлисты (особенно
 * русскоязычные, проверено на реальном примере) вставляют
 * "#EXTVLCOPT:http-user-agent=..."/"http-referrer=..." — конкретный канал
 * без ЭТОГО заголовка отдаёт 404/403 от источника, общий User-Agent на все
 * каналы сразу это не покрывает. Раньше эти строки просто пропускались
 * как обычные комментарии — теперь читаются и сохраняются на канал.
 */
object M3uPlaylistParser {
    private val YEAR_REGEX = Regex("\\((\\d{4})\\)")
    private val ATTR_REGEX = Regex("(tvg-logo|group-title)=\"([^\"]*)\"")
    private val VLCOPT_REGEX = Regex("#EXTVLCOPT:(http-user-agent|http-referrer)=(.*)", RegexOption.IGNORE_CASE)
    // У M3U, в отличие от Xtream, нет структурированного API сериалов —
    // единственный источник "это серия N сезона M" — сам текст названия.
    // Это ЭВРИСТИКА, не гарантия: сработает на "Шоу S01E02", не сработает
    // на "Шоу 1 сезон 2 серия" или нестандартных форматах провайдера.
    private val EPISODE_REGEX = Regex("S(\\d{1,2})E(\\d{1,3})", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<PlaylistMovieEntity> {
        val lines = raw.lines()
        val result = mutableListOf<PlaylistMovieEntity>()
        var index = 0
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF")) {
                val attrs = ATTR_REGEX.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                val title = line.substringAfterLast(",", "").trim().ifBlank { "Без названия" }
                val year = YEAR_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val episodeMatch = EPISODE_REGEX.find(title)
                val seasonNumber = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                val episodeNumber = episodeMatch?.groupValues?.get(2)?.toIntOrNull()
                // seriesId — не ID из какого-то реестра (M3U его не даёт),
                // а название БЕЗ SxxEyy-части, нормализованное — чтобы у
                // "Шоу S01E01" и "Шоу S01E02" совпал ключ группировки.
                val seriesId = if (seasonNumber != null) "m3u_series_" + title.replace(EPISODE_REGEX, "").trim().lowercase() else null
                val seriesTitle = if (seasonNumber != null) title.replace(EPISODE_REGEX, "").trim().trimEnd('-', '—', ' ') else null

                // Между #EXTINF и URL могут быть #EXTVLCOPT (заголовки для
                // этого конкретного канала) и другие строки-комментарии —
                // собираем первые, пропускаем вторые, пока не дойдём до URL.
                var userAgent: String? = null
                var referrer: String? = null
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) {
                    val trimmed = lines[j].trim()
                    val vlcMatch = VLCOPT_REGEX.find(trimmed)
                    if (vlcMatch != null) {
                        val (key, value) = vlcMatch.destructured
                        if (key.equals("http-user-agent", ignoreCase = true)) userAgent = value.trim()
                        if (key.equals("http-referrer", ignoreCase = true)) referrer = value.trim()
                    }
                    j++
                }
                val url = if (j < lines.size) lines[j].trim() else null

                if (!url.isNullOrBlank()) {
                    result.add(
                        PlaylistMovieEntity(
                            id = "m3u_$index",
                            title = title,
                            year = year,
                            poster = attrs["tvg-logo"],
                            genre = attrs["group-title"]?.ifBlank { null } ?: "Мой плейлист",
                            streamUrl = url,
                            userAgent = userAgent,
                            referrer = referrer,
                            seriesId = seriesId,
                            seriesTitle = seriesTitle,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber
                        )
                    )
                    index++
                }
                i = j + 1
            } else {
                i++
            }
        }
        return result
    }
}
