package com.platinum.ott.domain.model

// seriesId/seasonNumber/episodeNumber — раньше в проекте вообще не было
// понятия "серия сериала" ни у одной модели: у backend-каталога этих полей
// нет и не будет без правки на стороне zenith-backend (отдельный проект).
// Реально заполняются только для контента из Xtream (get_series_info) и,
// эвристически, из M3U (парсинг SxxEyy из названия) — см.
// XtreamVodClient.kt / M3uPlaylistParser.kt. Для backend-фильмов всегда null.
data class Movie(val id: String, val year: Int, val title: String, val poster: String,
    val description: String = "", val genre: String = "", val duration: String = "",
    val rating: Double = 0.0, val streamUrl: String = "",
    val seriesId: String? = null, val seriesTitle: String? = null,
    val seasonNumber: Int? = null, val episodeNumber: Int? = null)
