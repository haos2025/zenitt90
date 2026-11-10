package com.platinum.ott.domain.model

/** Карточка «только из TMDB» для фильма/сериала, которого нет в каталоге приложения (открывается из «Смотрите также»). */
data class TmdbCard(val title: String, val year: Int, val metadata: TmdbMetadata)
