package com.platinum.ott.presentation.screens.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.domain.model.PersonCreditItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    sessionGraph: SessionGraph
) : ViewModel() {
    private val tmdb = sessionGraph.tmdbRepository
    private val searchMovies = sessionGraph.searchMoviesUseCase
    private val _uiState = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val uiState: StateFlow<PersonUiState> = _uiState

    fun load(personId: Int) {
        viewModelScope.launch {
            _uiState.value = PersonUiState.Loading
            val profile = tmdb.getPersonProfile(personId)
            _uiState.value = if (profile != null) PersonUiState.Success(profile)
                else PersonUiState.Error("Не удалось загрузить данные об актёре")
        }
    }

    // Тот же приём, что и DetailViewModel.findInCatalog() для "Смотрите
    // также" — PersonCreditItem это запись TMDB, необязательно
    // присутствующая в собственном каталоге приложения (backend/плейлист),
    // прямого movieId нет. Один и тот же SearchMoviesUseCase, не
    // дублируем логику поиска по названию отдельной реализацией.
    suspend fun findInCatalog(item: PersonCreditItem): String? {
        val results = searchMovies.execute(item.title).getOrNull() ?: return null
        return results.firstOrNull { it.year == item.year }?.id
            ?: results.firstOrNull()?.id
    }
}
