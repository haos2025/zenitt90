package com.platinum.ott.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val getCatalog = ServiceLocator.getCatalogUseCase
    private val getPlaylistCatalog = ServiceLocator.getPlaylistCatalogUseCase
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

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
            val playlistMovies = try { getPlaylistCatalog.execute() } catch (_: Exception) { emptyList() }

            getCatalog.execute(page).onSuccess {
                _uiState.value = HomeUiState.Success(playlistMovies + it.movies, it.currentPage, it.totalPages)
            }.onFailure { error ->
                // Backend недоступен, но свой плейлист может быть жив —
                // не превращать это в полный отказ экрана, если есть хоть что-то
                if (playlistMovies.isNotEmpty()) {
                    _uiState.value = HomeUiState.Success(playlistMovies, 1, 1)
                } else {
                    _uiState.value = HomeUiState.Error(error.message ?: "Ошибка загрузки")
                }
            }
        }
    }
}
