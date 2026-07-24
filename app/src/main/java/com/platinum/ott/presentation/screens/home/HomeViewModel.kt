package com.platinum.ott.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.domain.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val getCatalog = ServiceLocator.getCatalogUseCase
    private val getPlaylistCatalog = ServiceLocator.getPlaylistCatalogUseCase
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

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

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
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
