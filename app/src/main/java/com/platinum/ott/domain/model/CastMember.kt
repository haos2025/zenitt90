package com.platinum.ott.domain.model

// Раньше TmdbMetadata.cast был плоской строкой имён через запятую ("Актёры: A, B, C") —
// без фото и без роли персонажа. Этот класс приходит из TMDB credits (/movie/{id}/credits)
// и позволяет собрать нормальную карусель с фото, см. PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4.
data class CastMember(val name: String, val character: String? = null, val profilePath: String? = null)
