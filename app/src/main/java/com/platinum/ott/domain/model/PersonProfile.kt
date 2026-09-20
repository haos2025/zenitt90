package com.platinum.ott.domain.model

// Экран актёра/фильмографии (подзадача 4 прогона про фокус/оверлей/актёров).
// Собирает /person/{id} и /person/{id}/combined_credits в один объект — тот
// же принцип, что и getMetadata() для фильма: вызывающей стороне (ViewModel)
// не нужно знать, что это два отдельных сетевых запроса.
//
// department/birthday/placeOfBirth/biography — TMDB не гарантирует их
// заполненными для каждой персоны, все Nullable, экран сам решает, какие
// строки показывать, а какие пропустить (см. PersonDetailScreen.kt).
data class PersonProfile(
    val id: Int,
    val name: String,
    val profilePath: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val placeOfBirth: String? = null,
    val department: String? = null,
    val filmography: List<PersonCreditItem> = emptyList()
)

// mediaType — "movie"/"tv", определяет секцию (Фильмы/Сериалы) на экране
// актёра. tmdbId — TMDB id самого фильма/сериала (не персоны) — по нему
// работает findInCatalog(), тот же приём, что и у Recommendation в
// DetailViewModel: TMDB-объект необязательно есть в собственном каталоге
// приложения, прямого movieId нет, сопоставление по названию — на
// стороне ViewModel, не здесь.
data class PersonCreditItem(
    val tmdbId: Int,
    val title: String,
    val posterPath: String? = null,
    val character: String? = null,
    val year: Int? = null,
    val mediaType: String
)
