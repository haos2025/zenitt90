package com.platinum.ott.domain.model

// cast раньше был String? (плоское "A, B, C" через join) — нигде, кроме
// одной строки текста на детальном экране, не использовался, поэтому
// заменён на список CastMember (фото + роль персонажа), а не добавлен
// вторым параллельным полем. См. PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4.
data class TmdbMetadata(val tmdbId: Int? = null, val posterPath: String? = null, val backdropPath: String? = null,
    val overview: String? = null, val voteAverage: Double? = null, val genres: String? = null,
    val trailerUrl: String? = null, val cast: List<CastMember> = emptyList())
