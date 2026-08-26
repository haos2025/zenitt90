package com.platinum.ott.domain.model

// cast раньше был String? (плоское "A, B, C" через join) — нигде, кроме
// одной строки текста на детальном экране, не использовался, поэтому
// заменён на список CastMember (фото + роль персонажа), а не добавлен
// вторым параллельным полем. См. PROMPT_DETAIL_SCREEN_UPGRADE.md, п.4.
//
// originalLanguage добавлен для авто-определения аниме при добавлении в
// избранное (см. PROMPT_FAVORITES_REDESIGN.md, п.1) — getMetadata() работает
// только с фильмами (searchMovie/getMovieDetails, см. TmdbApiService.kt), у
// эндпоинта /movie/{id} это поле называется original_language (ISO 639-1,
// "ja" для японского), не origin_country (то поле только у /tv/{id}).
data class TmdbMetadata(val tmdbId: Int? = null, val posterPath: String? = null, val backdropPath: String? = null,
    val overview: String? = null, val voteAverage: Double? = null, val genres: String? = null,
    val trailerUrl: String? = null, val cast: List<CastMember> = emptyList(), val originalLanguage: String? = null)

/**
 * Эвристика "это, вероятно, аниме" — используется ТОЛЬКО как начальное
 * значение FavoriteEntity.isAnime в момент добавления в избранное (см.
 * DetailViewModel.addFavorite()). Один жанр "Мультфильм"/"Animation"
 * недостаточен — под него попадает и западная анимация (Pixar/Disney), не
 * только аниме, поэтому дополнительно требуется оригинальный язык "ja".
 * Осознанно неидеально (не для всего контента, см. промт) — ручной оверрайд
 * всегда доступен через меню на карточке в FavoritesScreen/PhoneFavoritesScreen.
 * Для контента без TMDB-метаданных (M3U/Xtream, metadata == null) всегда
 * возвращает false — авто-определение здесь физически невозможно.
 */
fun TmdbMetadata?.looksLikeAnime(): Boolean {
    if (this == null) return false
    val hasAnimationGenre = genres?.let { g ->
        g.contains("Мультфильм", ignoreCase = true) || g.contains("Animation", ignoreCase = true)
    } ?: false
    val isJapanese = originalLanguage?.equals("ja", ignoreCase = true) == true
    return hasAnimationGenre && isJapanese
}
