package com.platinum.ott.presentation.screens.home

import com.platinum.ott.domain.model.Movie

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val movies: List<Movie>,
        val page: Int,
        val totalPages: Int,
        val isOffline: Boolean = false,
        val isLoadingMore: Boolean = false,
        // Решение 1 (PROMPT_HOME_FEED_REDESIGN.md): ротация НЕДАВНО
        // ДОБАВЛЕННОГО контента для hero-баннера — заполняется в
        // HomeViewModel из первой страницы backend-каталога (тот уже отдаёт
        // свежее первым; отдельного поля "дата добавления" на Movie нет и
        // не заводится ради этого). Не меняется в loadMore() — баннер не
        // должен перетасовываться при подгрузке следующих страниц.
        val heroMovies: List<Movie> = emptyList()
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

// Решение 5 (вкладки-фильтры Всё/Фильмы/Сериалы/Каналы) — фильтр по уже
// известному на клиенте типу контента, ничего нового не считаем и не
// запрашиваем повторно с сервера.
enum class HomeContentFilter(val label: String) {
    ALL("Всё"), MOVIES("Фильмы"), SERIES("Сериалы"), CHANNELS("Каналы")
}

fun Movie.matchesFilter(filter: HomeContentFilter): Boolean = when (filter) {
    HomeContentFilter.ALL -> true
    // seriesId заполняется только для эпизодов из Xtream/M3U (см. комментарий
    // в Movie.kt) — для backend-фильмов всегда null, поэтому "не эпизод"
    // здесь и есть практическое определение "Фильмы".
    HomeContentFilter.MOVIES -> seriesId == null
    HomeContentFilter.SERIES -> seriesId != null
    // "Канал" как отдельный тип контента ещё не существует в проекте —
    // Channel/ChannelStream (EXECUTION_ORDER.md, Группа 4,
    // PROMPT_IPTV_FOUNDATION.md) не реализованы. Вкладка в UI уже есть
    // заранее (решение 5 промта), но честно ничего не находит — придумывать
    // эвристику на несуществующем поле хуже, чем показать пустой список.
    // Как только появится Channel-модель, здесь появится реальная проверка.
    HomeContentFilter.CHANNELS -> false
}

// Решение 4 ("Мой плейлист" визуально отличается) — тот же признак, что уже
// разводит источники в GetPlayableUrlUseCase.PLAYLIST_PREFIXES (private там,
// дублируем здесь по той же причине, что и tmdbEligiblePrefixes в
// HomeViewModel — вынести в общее место при следующей структурной правке).
val Movie.isPlaylistSourced: Boolean
    get() = id.substringBefore('_', missingDelimiterValue = "") in setOf("m3u", "xt")
