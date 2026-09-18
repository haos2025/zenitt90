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
 *
 * ДОБАВЛЕНО (PROMPT_IPTV_FOUNDATION.md): tvg-id тоже раньше не читался,
 * хотя это стандартный M3U-атрибут именно для различения одинаковых по
 * названию каналов разных регионов или, наоборот, надёжного опознания
 * одного и того же канала у разных провайдеров — без него дедуп каналов
 * между несколькими источниками по одному лишь названию небезопасен.
 */
object M3uPlaylistParser {
    private val YEAR_REGEX = Regex("\\((\\d{4})\\)")
    private val ATTR_REGEX = Regex("(tvg-id|tvg-logo|group-title|catchup|catchup-days|catchup-source|tvg-chno)=\"([^\"]*)\"")
    private val VLCOPT_REGEX = Regex("#EXTVLCOPT:(http-user-agent|http-referrer)=(.*)", RegexOption.IGNORE_CASE)
    // PROMPT_EPG.md, подзадача 2 — стандартный атрибут шапки плейлиста
    // (строка "#EXTM3U url-tvg="http://...""), не строки #EXTINF, поэтому
    // отдельный regex и отдельная функция ниже, а не добавление в ATTR_REGEX.
    private val URL_TVG_REGEX = Regex("url-tvg=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    // У M3U, в отличие от Xtream, нет структурированного API сериалов —
    // единственный источник "это серия N сезона M" — сам текст названия.
    // Это ЭВРИСТИКА, не гарантия: сработает на "Шоу S01E02", не сработает
    // на "Шоу 1 сезон 2 серия" или нестандартных форматах провайдера.
    private val EPISODE_REGEX = Regex("S(\\d{1,2})E(\\d{1,3})", RegexOption.IGNORE_CASE)
    // См. комментарий у вычисления catchupDays в parse() ниже.
    private const val DEFAULT_CATCHUP_DAYS_WHEN_UNSPECIFIED = 1

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

                // PROMPT_EPG.md, подзадача 5 — catchup-days="N" однозначен;
                // просто catchup="default"/"shift"/"append" БЕЗ catchup-days
                // тоже реально встречается (провайдер поддерживает архив, но
                // не пишет срок явно). ДОПУЩЕНИЕ (честно, не проверено на
                // реальном плейлисте без catchup-days): в этом случае берём
                // консервативный DEFAULT_CATCHUP_DAYS_WHEN_UNSPECIFIED, а не
                // считаем, что архива нет вообще — это не единственно
                // возможная трактовка, если окажется не так, поправить
                // только эту константу.
                val catchupAttr = attrs["catchup"]?.ifBlank { null }
                val catchupDaysAttr = attrs["catchup-days"]?.toIntOrNull()
                val catchupDays = when {
                    catchupDaysAttr != null && catchupDaysAttr > 0 -> catchupDaysAttr
                    catchupAttr != null -> DEFAULT_CATCHUP_DAYS_WHEN_UNSPECIFIED
                    else -> null
                }
                val catchupTemplate = attrs["catchup-source"]?.ifBlank { null }
                val channelNumber = attrs["tvg-chno"]?.toIntOrNull()

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
                            episodeNumber = episodeNumber,
                            tvgId = attrs["tvg-id"]?.ifBlank { null },
                            catchupDays = catchupDays,
                            catchupTemplate = catchupTemplate,
                            channelNumber = channelNumber
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

    /**
     * Достаёт "url-tvg=" из шапки плейлиста (первая строка "#EXTM3U ...",
     * если она вообще есть — не все плейлисты её пишут). Не часть parse()
     * намеренно: это метаданные ИСТОЧНИКА целиком (PlaylistSourceEntity.epgUrl),
     * а не какой-то отдельной записи — вызывается один раз на refresh(),
     * не по одной на каждый канал, как ATTR_REGEX внутри цикла выше.
     *
     * ДОПУЩЕНИЕ (не проверено на реальном плейлисте с несколькими url-tvg
     * через запятую — такой вариант тоже встречается в дикой природе):
     * берём только первый адрес. Мульти-источник XMLTV на один плейлист —
     * не тема этой подзадачи, если реально понадобится, это отдельное
     * расширение парсинга, не архитектурное решение.
     */
    fun parseEpgUrl(raw: String): String? {
        val headerLine = raw.lineSequence().firstOrNull { it.trim().startsWith("#EXTM3U") } ?: return null
        return URL_TVG_REGEX.find(headerLine)?.groupValues?.get(1)?.ifBlank { null }
    }
}
