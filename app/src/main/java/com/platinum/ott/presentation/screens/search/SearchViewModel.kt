package com.platinum.ott.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.domain.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(val results: List<Movie>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

// Раньше поиск существовал только как MovieSearchProvider для системного
// поиска Android TV — в самом приложении не было ни экрана, ни способа
// ввести запрос текстом. searchMoviesUseCase был готов в ServiceLocator с
// самого начала, но НИКЕМ не вызывался.
//
// searchMoviesUseCase ходит только в backend-каталог — у PlaylistRepository
// (M3U/Xtream) нет текстового поиска по API. Чтобы плейлист не выпадал из
// поиска совсем, дополнительно фильтруем уже загруженный на телефон/TV
// список плейлиста по подстроке в названии — тот же getPlaylistCatalogUseCase,
// что и HomeViewModel использует для главного экрана.
@HiltViewModel
class SearchViewModel @Inject constructor(
    sessionGraph: SessionGraph
) : ViewModel() {
    private val searchMovies = sessionGraph.searchMoviesUseCase
    private val getPlaylistCatalog = sessionGraph.getPlaylistCatalogUseCase

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private var cachedPlaylist: List<Movie>? = null

    init {
        viewModelScope.launch {
            _query.debounce(400).distinctUntilChanged().collectLatest { q ->
                if (q.length < 2) { _uiState.value = SearchUiState.Idle; return@collectLatest }
                _uiState.value = SearchUiState.Loading
                runSearch(q)
            }
        }
    }

    fun onQueryChange(newQuery: String) { _query.value = newQuery }

    private suspend fun runSearch(q: String) {
        if (cachedPlaylist == null) {
            cachedPlaylist = try { getPlaylistCatalog.execute() } catch (_: Exception) { emptyList() }
        }
        val playlistMatches = cachedPlaylist.orEmpty().filter { it.title.contains(q, ignoreCase = true) }
        searchMovies.execute(q)
            .onSuccess { backendMatches ->
                val combined = (backendMatches + playlistMatches).distinctBy { it.id }
                _uiState.value = if (combined.isEmpty()) SearchUiState.Success(emptyList()) else SearchUiState.Success(combined)
            }
            .onFailure { error ->
                // Backend недоступен, но локальный плейлист может дать результат —
                // тот же принцип отказоустойчивости, что в HomeViewModel.
                if (playlistMatches.isNotEmpty()) _uiState.value = SearchUiState.Success(playlistMatches)
                else _uiState.value = SearchUiState.Error(error.message ?: "Ошибка поиска")
            }
    }
}
