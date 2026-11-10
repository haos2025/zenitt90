package com.platinum.ott.presentation.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.model.Recommendation
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.domain.model.looksLikeAnime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val getMovie = sessionGraph.getMovieByIdUseCase
    private val favorites = sessionGraph.favoritesUseCase
    private val history = sessionGraph.watchHistoryUseCase
    private val tmdb = sessionGraph.tmdbRepository
    private val searchMovies = sessionGraph.searchMoviesUseCase
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState
    // Для пикера папки при добавлении в избранное (PROMPT_FAVORITES_REDESIGN.md,
    // п.2) — DetailScreen/PhoneDetailScreen показывают MoveToFolderDialog
    // на основе этого списка перед вызовом addFavorite().
    val folders = sessionGraph.favoritesUseCase.getAllFolders()

    // Раньше карточки в "Смотрите также" были кликабельны только визуально —
    // нажатие ничего не делало (реальный репорт с TV). Recommendation — это
    // запись из TMDB, необязательно присутствующая в собственном каталоге
    // приложения (backend/плейлист) — прямого movieId у нас нет, поэтому
    // ищем по названию через тот же SearchMoviesUseCase, что и обычный
    // поиск (ищет по backend-каталогу — семантически верная область для
    // сопоставления с TMDB-рекомендацией, в отличие от личного M3U/Xtream-
    // плейлиста пользователя, где такого совпадения обычно и не может быть).
    // Возвращает null, если совпадение не нашлось или сеть недоступна —
    // вызывающая сторона (DetailScreen.kt/PhoneDetailScreen.kt) сама решает,
    // как сообщить об этом пользователю.
    suspend fun findInCatalog(recommendation: Recommendation): String? {
        val results = searchMovies.execute(recommendation.title).getOrNull() ?: return null
        return results.firstOrNull { it.year == recommendation.year }?.id
            ?: results.firstOrNull()?.id
    }

    // Выбор озвучки/варианта потока для серии — до запуска плеера (если вариантов больше одного).
    private val _pendingVariantChoice = MutableStateFlow<Pair<String, List<StreamVariant>>?>(null)
    val pendingVariantChoice: StateFlow<Pair<String, List<StreamVariant>>?> = _pendingVariantChoice

    fun onEpisodeSelected(episodeId: String, onNavigate: (episodeId: String, variantUrl: String?) -> Unit) {
        viewModelScope.launch {
            val variants = try { sessionGraph.getPlayableUrlUseCase.execute(episodeId) } catch (_: Exception) { emptyList() }
            if (variants.size > 1) _pendingVariantChoice.value = episodeId to variants
            else onNavigate(episodeId, variants.firstOrNull()?.url)
        }
    }

    fun selectVariant(episodeId: String, variant: StreamVariant, onNavigate: (episodeId: String, variantUrl: String?) -> Unit) {
        _pendingVariantChoice.value = null
        onNavigate(episodeId, variant.url)
    }

    fun dismissVariantChoice() { _pendingVariantChoice.value = null }

    /**
     * Единое окно «Деталь» для сериала: те же шапка и метаданные TMDB, что у фильма (если нашёлся точный
     * совпадающий сериал), плюс список серий с прогрессом и цель кнопки «Смотреть».
     */
    /** Карточка фильма/сериала только из TMDB (его нет в каталоге): описание есть, воспроизведения нет. */
    fun loadTmdb(tmdbId: Int, isTv: Boolean) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            val card = tmdb.getTmdbCard(tmdbId, isTv)
            if (card == null) { _uiState.value = DetailUiState.Error("Не удалось загрузить описание"); return@launch }
            val recs = if (isTv) tmdb.getSeriesRecommendations(tmdbId) else tmdb.getRecommendations(tmdbId)
            val movie = Movie(id = "tmdb_${if (isTv) "tv" else "movie"}_$tmdbId", year = card.year, title = card.title, poster = "")
            _uiState.value = DetailUiState.Success(movie, card.metadata, false, null, false, recs, null, tmdbOnly = true)
        }
    }

    fun loadSeries(seriesId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            try {
                val episodes = sessionGraph.playlistRepository.getEpisodesForSeries(seriesId)
                if (episodes.isEmpty()) { _uiState.value = DetailUiState.Error("Серии не найдены"); return@launch }
                val first = episodes.first()
                val title = first.seriesTitle?.takeIf { it.isNotBlank() } ?: first.title
                val year = episodes.firstOrNull { it.year > 0 }?.year ?: 0
                val movie = Movie(
                    id = seriesId, year = year, title = title,
                    poster = episodes.firstOrNull { it.poster.isNotBlank() }?.poster.orEmpty(),
                    seriesId = seriesId, seriesTitle = title
                )
                val meta = try { tmdb.getSeriesMetadata(seriesId, title, year.takeIf { it > 0 }).getOrNull() } catch (_: Exception) { null }
                val favEntity = favorites.getByContentId(seriesId)
                val histMap = try {
                    history.getByContentIds(episodes.map { it.id }).associateBy { it.contentId }
                } catch (_: Exception) { emptyMap() }
                val recommendations = meta?.tmdbId?.let { tmdb.getSeriesRecommendations(it) } ?: emptyList()
                val series = SeriesInfo(episodes, histMap, computeResume(episodes, histMap))
                _uiState.value = DetailUiState.Success(movie, meta, favEntity != null, null, favEntity?.isAnime ?: false, recommendations, series)
            } catch (e: Exception) {
                _uiState.value = DetailUiState.Error(e.message ?: "Ошибка")
            }
        }
    }

    // Последнее, что смотрели: недосмотренная серия — «Продолжить»; досмотренная — «Следующая после неё»;
    // истории нет — первая серия.
    private fun computeResume(episodes: List<Movie>, hist: Map<String, com.platinum.ott.data.local.entity.WatchHistoryEntity>): SeriesResume {
        val last = hist.values.filter { h -> episodes.any { it.id == h.contentId } }.maxByOrNull { it.watchedAt }
            ?: return SeriesResume(episodes.first(), ResumeKind.START, null)
        val idx = episodes.indexOfFirst { it.id == last.contentId }
        if (!last.completed && last.positionMs > 0) {
            val p = if (last.durationMs > 0) (last.positionMs.toFloat() / last.durationMs).coerceIn(0f, 1f) else null
            return SeriesResume(episodes[idx], ResumeKind.CONTINUE, p)
        }
        val next = episodes.getOrNull(idx + 1)
        return if (next != null) SeriesResume(next, ResumeKind.NEXT, null) else SeriesResume(episodes.first(), ResumeKind.START, null)
    }

    fun load(movieId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            getMovie.execute(movieId).onSuccess { movie ->
                // Единая точка: любой movieId, у которого есть seriesId
                // (эпизод сериала из плейлиста, см. Movie.kt), уводит на
                // общий экран сериала вместо отдельной детальной карточки —
                // независимо от того, откуда пришли (лента, поиск, история).
                if (movie.seriesId != null) {
                    _uiState.value = DetailUiState.RedirectToSeries(movie.seriesId)
                    return@onSuccess
                }
                val meta = if (com.platinum.ott.domain.ContentIdKind.isZenithBackendContent(movieId)) {
                    // ФИКС (аудит): раньше tmdb.getMetadata() вызывался
                    // безусловно для ЛЮБОГО movieId, включая собственные
                    // каналы/фильмы из плейлиста — ровно тот же риск
                    // случайного совпадения, что HomeViewModel.
                    // resolvePosterIfNeeded() уже осознанно исключает для
                    // ленты Home. Детальная карточка эту защиту не
                    // повторяла: неверный постер/описание для канала с
                    // похожим на что-то в TMDB названием кэшировался на 24ч.
                    try { tmdb.getMetadata(movieId, movie.title, movie.year).getOrNull() } catch (_: Exception) { null }
                } else null
                val favEntity = favorites.getByContentId(movieId)
                val hist = history.getByContentId(movieId)
                val progress = if (hist != null && hist.durationMs > 0) hist.positionMs.toFloat() / hist.durationMs else null
                // "Смотрите также" — только когда TMDB реально нашёл фильм
                // (meta.tmdbId != null); для собственного M3U/Xtream-плейлиста
                // без совпадения с TMDB список остаётся пустым, экран просто
                // не рисует блок (см. DetailScreen.kt). Ошибка сети здесь же
                // гасится внутри getRecommendations() — сюда долетает пустой
                // список, а не исключение.
                val recommendations = meta?.tmdbId?.let { tmdb.getRecommendations(it) } ?: emptyList()
                _uiState.value = DetailUiState.Success(movie, meta, favEntity != null, progress, favEntity?.isAnime ?: false, recommendations)
            }.onFailure { _uiState.value = DetailUiState.Error(it.message ?: "Ошибка") }
        }
    }

    // Раньше "В избранное" сразу вставляло запись через toggle(), без выбора
    // папки. Теперь (PROMPT_FAVORITES_REDESIGN.md, п.2) точка входа —
    // DetailScreen/PhoneDetailScreen — сначала показывает MoveToFolderDialog
    // и вызывает этот метод уже с выбранным folderId; при снятии с
    // избранного (isFavorite == true) UI вызывает removeFavorite() напрямую,
    // без диалога. isAnime — эвристика по TMDB-метаданным, уже загруженным в
    // текущий state.Success на момент добавления (см. TmdbMetadata.looksLikeAnime());
    // для контента без TMDB (M3U/Xtream) остаётся false, ручной оверрайд —
    // через единое меню на карточке в FavoritesScreen/PhoneFavoritesScreen (п.3).
    fun addFavorite(movieId: String, title: String, poster: String?, folderId: Long?) {
        viewModelScope.launch {
            val current = _uiState.value as? DetailUiState.Success
            val isAnime = current?.metadata.looksLikeAnime()
            // Постер TMDB (если метаданные уже загружены) надёжнее, чем movie.poster: у backend-контента
            // он часто пустой, у плейлиста — скриншот кадра. Иначе в «Избранном» оставалась пустая карточка.
            val tmdbPoster = com.platinum.ott.core.platform.TmdbImage.posterUrl(current?.metadata?.posterPath, 400)
            val savedPoster = tmdbPoster ?: poster?.takeIf { it.isNotBlank() }
            favorites.add(FavoriteEntity(contentId = movieId, contentType = if (current?.series != null) "SERIES" else "MOVIE", title = title, poster = savedPoster, folderId = folderId, isAnime = isAnime))
            if (current != null) _uiState.value = current.copy(isFavorite = true, isAnime = isAnime)
        }
    }

    // Снятие с избранного — без диалога, сразу удаление (см. промт, п.2).
    // Снятие удаляет саму строку — вместе с ней теряется и isAnime (по
    // дизайну: это атрибут записи в favorites, не самого фильма/сериала).
    fun removeFavorite(movieId: String) {
        viewModelScope.launch {
            favorites.remove(movieId)
            val current = _uiState.value as? DetailUiState.Success
            if (current != null) _uiState.value = current.copy(isFavorite = false, isAnime = false)
        }
    }

    // Флаг "аниме" осмысленен только для уже добавленной в избранное записи —
    // UI отключает кнопку, если !isFavorite (см. DetailScreen.kt).
    fun setAnime(movieId: String, isAnime: Boolean) {
        viewModelScope.launch {
            favorites.setAnime(movieId, isAnime)
            val current = _uiState.value
            if (current is DetailUiState.Success) _uiState.value = current.copy(isAnime = isAnime)
        }
    }
}
