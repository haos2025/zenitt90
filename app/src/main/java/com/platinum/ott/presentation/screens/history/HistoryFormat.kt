package com.platinum.ott.presentation.screens.history

import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.domain.model.Movie

/** Тип записи истории — по нему экран сам собирает фильтр («Все / Фильмы / Сериалы / Каналы»). */
enum class HistoryKind(val label: String) { MOVIE("Фильмы"), SERIES("Сериалы"), CHANNEL("Каналы") }

/** Запись истории вместе с типом и готовой подписью. */
data class HistoryItem(val entry: WatchHistoryEntity, val kind: HistoryKind, val subtitle: String)

/**
 * Где остановились в сериале: сезон и серия, сколько серий в сезоне, сколько всего сезонов
 * и сколько серий осталось после текущей.
 */
data class SeriesProgress(val season: Int?, val episode: Int?, val episodesInSeason: Int, val seasons: Int, val episodesLeft: Int)

/**
 * Тип по самой записи, без обращений к базе: у серий есть seriesId, у каналов id начинается с «ch_»
 * (см. ChannelMatchingRepository), всё остальное — фильмы.
 */
fun historyKindOf(entry: WatchHistoryEntity): HistoryKind = when {
    entry.seriesId != null -> HistoryKind.SERIES
    entry.contentId.startsWith("ch_") -> HistoryKind.CHANNEL
    else -> HistoryKind.MOVIE
}

/** «1 ч 12 мин», «23 мин», «меньше минуты». Минуты округляются вверх: 30 секунд — это «1 мин». */
fun formatTimeLeft(leftMs: Long): String {
    if (leftMs <= 0) return "меньше минуты"
    val totalMin = (leftMs + 59_999) / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h ч $m мин"
        h > 0 -> "$h ч"
        else -> "$m мин"
    }
}

private fun pluralEpisodes(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "серий"
        mod10 == 1 -> "серия"
        mod10 in 2..4 -> "серии"
        else -> "серий"
    }
}

/** Подпись записи истории: «Просмотрено» или «Осталось 1 ч 12 мин» (без процентов). Общая для ТВ и телефона. */
fun historySubtitle(entry: WatchHistoryEntity): String {
    if (entry.completed) return "Просмотрено"
    if (entry.durationMs <= 0) return "Продолжить просмотр"
    return "Осталось " + formatTimeLeft(entry.durationMs - entry.positionMs)
}

/**
 * Подпись для сериала: «Сезон 2 · серия 5 из 10 · осталось 23 мин» и второй строкой «Дальше ещё 5 серий · сезонов: 3».
 * Если по базе серий найти не удалось ([series] == null) — обычная подпись.
 */
fun historySubtitle(entry: WatchHistoryEntity, series: SeriesProgress?): String {
    if (series == null) return historySubtitle(entry)
    val first = buildString {
        val season = series.season
        val episode = series.episode
        if (season != null && episode != null) {
            append("Сезон $season · серия $episode")
            if (series.episodesInSeason > 0) append(" из ${series.episodesInSeason}")
        } else if (episode != null) {
            append("Серия $episode")
            if (series.episodesInSeason > 0) append(" из ${series.episodesInSeason}")
        }
        val status = when {
            entry.completed -> "просмотрена"
            entry.durationMs > 0 -> "осталось " + formatTimeLeft(entry.durationMs - entry.positionMs)
            else -> null
        }
        if (status != null) {
            if (isNotEmpty()) append(" · ")
            append(status)
        }
    }
    if (first.isEmpty()) return historySubtitle(entry)
    val second = buildString {
        if (series.episodesLeft > 0) append("Дальше ещё ${series.episodesLeft} ${pluralEpisodes(series.episodesLeft)}")
        else append("Последняя серия")
        if (series.seasons > 1) append(" · сезонов: ${series.seasons}")
    }
    return first + "\n" + second
}

/** Положение серии [currentId] среди всех серий сериала ([episodes] — по порядку сезонов и серий). */
fun seriesProgressOf(episodes: List<Movie>, currentId: String): SeriesProgress? {
    val index = episodes.indexOfFirst { it.id == currentId }
    if (index < 0) return null
    val current = episodes[index]
    val season = current.seasonNumber
    val inSeason = if (season != null) episodes.count { it.seasonNumber == season } else episodes.size
    val seasons = episodes.mapNotNull { it.seasonNumber }.distinct().size
    return SeriesProgress(
        season = season,
        episode = current.episodeNumber ?: (index + 1),
        episodesInSeason = inSeason,
        seasons = seasons,
        episodesLeft = episodes.size - (index + 1)
    )
}
