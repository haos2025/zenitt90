package com.platinum.ott.presentation.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.local.entity.FavoriteEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    sessionGraph: SessionGraph
) : ViewModel() {
    private val getMovie = sessionGraph.getMovieByIdUseCase
    private val favorites = sessionGraph.favoritesUseCase
    private val history = sessionGraph.watchHistoryUseCase
    private val tmdb = sessionGraph.tmdbRepository
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    fun load(movieId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            getMovie.execute(movieId).onSuccess { movie ->
                val meta = try { tmdb.getMetadata(movieId, movie.title, movie.year).getOrNull() } catch (_: Exception) { null }
                val favEntity = favorites.getByContentId(movieId)
                val hist = history.getByContentId(movieId)
                val progress = if (hist != null && hist.durationMs > 0) hist.positionMs.toFloat() / hist.durationMs else null
                _uiState.value = DetailUiState.Success(movie, meta, favEntity != null, progress, favEntity?.isAnime ?: false)
            }.onFailure { _uiState.value = DetailUiState.Error(it.message ?: "Ошибка") }
        }
    }

    fun toggleFavorite(movieId: String, title: String, poster: String?) {
        viewModelScope.launch {
            favorites.toggle(FavoriteEntity(contentId = movieId, title = title, poster = poster))
            // Раньше здесь запись в БД реально проходила, но _uiState никогда
            // не обновлялся после toggle() — кнопка "В избранное" всегда
            // показывала старое состояние, выглядело как будто ничего не
            // происходит, хотя запись/удаление в таблице favorites работали.
            val current = _uiState.value
            if (current is DetailUiState.Success) {
                val favEntity = favorites.getByContentId(movieId)
                // Снятие с избранного удаляет саму строку — вместе с ней
                // теряется и isAnime (по дизайну: это атрибут записи в
                // favorites, не самого фильма/сериала).
                _uiState.value = current.copy(isFavorite = favEntity != null, isAnime = favEntity?.isAnime ?: false)
            }
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
