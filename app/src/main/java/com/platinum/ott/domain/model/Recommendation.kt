package com.platinum.ott.domain.model

// Чужая TMDB-запись, не обязательно присутствующая в собственном каталоге
// приложения (бэкенд/плагины отдают свой ограниченный набор, не весь TMDB) —
// см. PROMPT_DETAIL_SCREEN_UPGRADE.md, п.5, вариант (б): витрина "похоже на
// это", без перехода/клика. Поэтому здесь нет streamUrl и это не Movie.
data class Recommendation(val tmdbId: Int, val title: String, val posterPath: String? = null, val year: Int? = null)
