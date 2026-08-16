package com.platinum.ott.presentation.screens.home

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.core.platform.TmdbImage
import com.platinum.ott.domain.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionGraph: SessionGraph
) : ViewModel() {
    private val getCatalog = sessionGraph.getCatalogUseCase
    private val getPlaylistCatalog = sessionGraph.getPlaylistCatalogUseCase
    private val tmdbRepository = sessionGraph.tmdbRepository
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    // ROADMAP.md п.11 (TMDB-постеры в сетке каталога) — раньше сознательно
    // не делалось: 30-50 карточек одновременно на экране означало бы TMDB-
    // запрос на каждую разом. LazyRow/LazyColumn в CatalogRow.kt/
    // PhoneCatalogRow.kt и так композируют только карточки рядом с
    // видимой областью (плюс небольшой запас на прокрутку) — LaunchedEffect
    // внутри самого элемента списка (см. CatalogRow.kt) де-факто и есть
    // "загрузка под видимые карточки" без отдельной системы отслеживания
    // видимости. Держим как SnapshotStateMap (не StateFlow<Map<>>), чтобы
    // разрешение ОДНОГО постера рекомпозировало только его карточку, а не
    // весь список — TmdbRepository.getMetadata() уже кэширует результат в
    // Room на 24ч (см. TmdbRepositoryImpl), это только сессионный кэш
    // поверх него, чтобы не перезапускать корутину на каждую рекомпозицию.
    val resolvedPosters = mutableStateMapOf<String, String>()
    private val posterAttempted = HashSet<String>()

    // Те же префиксы, что ZENITH_BACKEND_PREFIXES в GetPlayableUrlUseCase
    // (private там, дублируем константу здесь — вынести в общее место при
    // следующей структурной правке, не тянуть отдельным диффом ради двух строк).
    // Плейлист (m3u_/xt_) — собственные каналы/фильмы пользователя, TMDB-
    // поиск по их названиям (часто это имена IPTV-каналов, не фильмов) дал
    // бы случайные неверные совпадения и тратил бы запросы впустую.
    private val tmdbEligiblePrefixes = setOf("yt", "ia")

    fun resolvePosterIfNeeded(movie: Movie, targetWidthPx: Int) {
        val prefix = movie.id.substringBefore('_', missingDelimiterValue = "")
        if (prefix !in tmdbEligiblePrefixes) return
        if (!posterAttempted.add(movie.id)) return // уже запрашивали (успех/неудача) — не повторяем на каждую рекомпозицию
        viewModelScope.launch {
            val meta = try { tmdbRepository.getMetadata(movie.id, movie.title, movie.year).getOrNull() } catch (_: Exception) { null }
            val url = meta?.posterPath?.let { TmdbImage.posterUrl(it, targetWidthPx) }
            if (url != null) resolvedPosters[movie.id] = url
        }
    }

    // Плейлист подмешивается только на первую загрузку (loadCatalog) —
    // loadMore() дальше дозаписывает только страницы backend, не трогая
    // эту часть списка повторно на каждый вызов.
    private var cachedPlaylistMovies: List<Movie> = emptyList()

    init { loadCatalog() }

    fun loadCatalog(page: Int = 1) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            // Раньше M3U/Xtream-логин был "калиткой" без последствий — сохранял
            // учётные данные, но ни один экран не превращал их в контент.
            // Плейлист показывается целиком, не постранично (в отличие от
            // backend-каталога) — мешать две разные схемы пагинации в одном
            // курсоре было бы источником багов, плейлисты и не поддерживают
            // постраничность в общем случае.
            cachedPlaylistMovies = try { getPlaylistCatalog.execute() } catch (_: Exception) { emptyList() }

            getCatalog.execute(page).onSuccess {
                _uiState.value = HomeUiState.Success(cachedPlaylistMovies + it.movies, it.currentPage, it.totalPages)
            }.onFailure { error ->
                // Backend недоступен, но свой плейлист может быть жив —
                // не превращать это в полный отказ экрана, если есть хоть что-то
                if (cachedPlaylistMovies.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(cachedPlaylistMovies, 1, 1)
                } else {
                    _uiState.value = HomeUiState.Error(error.message ?: "Ошибка загрузки")
                }
            }
        }
    }

    /**
     * Раньше HomeScreen ни разу не вызывал loadCatalog() со страницей > 1 —
     * пользователь всегда видел только первую страницу backend (у
     * Archive.org это 20 фильмов, rows=20 на стороне плагина), хотя backend
     * честно считает totalPages по numFound и готов отдавать следующие
     * страницы по запросу by design. HomeScreen вызывает это при приближении
     * к концу списка (см. HomeScreen.kt, LaunchedEffect по listState).
     */
    fun loadMore() {
        val current = _uiState.value as? HomeUiState.Success ?: return
        if (current.isLoadingMore || current.page >= current.totalPages) return

        // РАНЬШЕ isLoadingMore=true выставлялся ПЕРВОЙ строкой внутри
        // viewModelScope.launch{} — то есть асинхронно, на следующей
        // диспетчеризации корутины, а не сразу. HomeScreen.kt триггерит
        // loadMore() из snapshotFlow{}.collect{} на КАЖДОЕ изменение
        // последнего видимого индекса — во время быстрого скролла это
        // несколько вызовов за доли секунды, и все они успевали прочитать
        // ещё не обновлённый _uiState (isLoadingMore всё ещё false) прежде
        // чем первый launch вообще начинал выполняться — отсюда
        // "пагинация работает с перебоями" (несколько параллельных
        // загрузок одной и той же страницы, гонка за тем, чей результат
        // запишется в state последним). Флаг теперь выставляется
        // синхронно, до launch — второй вызов увидит isLoadingMore=true
        // и выйдет по guard'у выше ещё до того, как попадёт в корутину.
        _uiState.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            getCatalog.execute(current.page + 1)
                .onSuccess { result ->
                    val existing = _uiState.value as? HomeUiState.Success ?: return@onSuccess
                    _uiState.value = existing.copy(
                        movies = existing.movies + result.movies,
                        page = result.currentPage,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    // Тихо не удалось догрузить следующую страницу — не
                    // рушим уже показанный и рабочий список ошибкой на весь экран.
                    val existing = _uiState.value as? HomeUiState.Success ?: return@onFailure
                    _uiState.value = existing.copy(isLoadingMore = false)
                }
        }
    }
}
