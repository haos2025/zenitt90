package com.platinum.ott.presentation.screens.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.repository.SeriesSummary
import com.platinum.ott.domain.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Раньше "по сериалам" смотреть было негде — все эпизоды лежали в общем
// плоском списке фильмов вперемешку. PlaylistRepository.getSeriesList()/
// getEpisodesForSeries() уже группируют по seriesId (см. Movie.kt) —
// здесь просто раскладываем это по двум экранам.
sealed interface SeriesListUiState {
    object Loading : SeriesListUiState
    data class Success(val series: List<SeriesSummary>) : SeriesListUiState
    data class Error(val message: String) : SeriesListUiState
}

@HiltViewModel
class SeriesListViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val _uiState = MutableStateFlow<SeriesListUiState>(SeriesListUiState.Loading)
    val uiState: StateFlow<SeriesListUiState> = _uiState

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SeriesListUiState.Loading
            try {
                _uiState.value = SeriesListUiState.Success(sessionGraph.playlistRepository.getSeriesList())
            } catch (e: Exception) {
                _uiState.value = SeriesListUiState.Error(e.message ?: "Не удалось загрузить список сериалов")
            }
        }
    }
}

sealed interface SeriesEpisodesUiState {
    object Loading : SeriesEpisodesUiState
    data class Success(val episodes: List<Movie>) : SeriesEpisodesUiState
    data class Error(val message: String) : SeriesEpisodesUiState
}

@HiltViewModel
class SeriesEpisodesViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val _uiState = MutableStateFlow<SeriesEpisodesUiState>(SeriesEpisodesUiState.Loading)
    val uiState: StateFlow<SeriesEpisodesUiState> = _uiState

    // Раньше contentType == "SERIES" существовал только в схеме
    // FavoriteEntity — ничто в приложении никогда не создавало такую
    // запись, потому что раньше не было экрана, где можно было бы
    // добавить в избранное ИМЕННО сериал целиком (не отдельный эпизод).
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite

    private var currentSeriesId: String? = null
    private var currentSeriesTitle: String = ""
    private var currentSeriesPoster: String? = null

    fun load(seriesId: String) {
        currentSeriesId = seriesId
        viewModelScope.launch {
            _uiState.value = SeriesEpisodesUiState.Loading
            try {
                val episodes = sessionGraph.playlistRepository.getEpisodesForSeries(seriesId)
                currentSeriesTitle = episodes.firstOrNull()?.seriesTitle ?: episodes.firstOrNull()?.title ?: ""
                currentSeriesPoster = episodes.firstOrNull()?.poster
                _uiState.value = SeriesEpisodesUiState.Success(episodes)
                _isFavorite.value = sessionGraph.favoritesUseCase.isFavorite(seriesId)
            } catch (e: Exception) {
                _uiState.value = SeriesEpisodesUiState.Error(e.message ?: "Не удалось загрузить эпизоды")
            }
        }
    }

    fun toggleFavorite() {
        val seriesId = currentSeriesId ?: return
        viewModelScope.launch {
            sessionGraph.favoritesUseCase.toggle(
                com.platinum.ott.data.local.entity.FavoriteEntity(
                    contentId = seriesId,
                    contentType = "SERIES",
                    title = currentSeriesTitle,
                    poster = currentSeriesPoster
                )
            )
            _isFavorite.value = sessionGraph.favoritesUseCase.isFavorite(seriesId)
        }
    }
}
