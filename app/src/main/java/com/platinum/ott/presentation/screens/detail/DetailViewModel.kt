package com.platinum.ott.presentation.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DetailViewModel : ViewModel() {
    private val getMovie = ServiceLocator.getMovieByIdUseCase
    private val favorites = ServiceLocator.favoritesUseCase
    private val history = ServiceLocator.watchHistoryUseCase
    private val tmdb = ServiceLocator.tmdbRepository
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState

    fun load(movieId: String) {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            getMovie.execute(movieId).onSuccess { movie ->
                val meta = try { tmdb.getMetadata(movieId, movie.title, movie.year).getOrNull() } catch (_: Exception) { null }
                val isFav = favorites.isFavorite(movieId)
                val hist = history.getByContentId(movieId)
                val progress = if (hist != null && hist.durationMs > 0) hist.positionMs.toFloat() / hist.durationMs else null
                _uiState.value = DetailUiState.Success(movie, meta, isFav, progress)
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
                _uiState.value = current.copy(isFavorite = favorites.isFavorite(movieId))
            }
        }
    }
}
