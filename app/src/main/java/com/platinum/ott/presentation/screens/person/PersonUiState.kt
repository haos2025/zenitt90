package com.platinum.ott.presentation.screens.person

import com.platinum.ott.domain.model.PersonProfile

// Тот же принцип, что и DetailUiState.kt — минимальный набор состояний,
// без отдельного "частично загружен" (фильмография — часть PersonProfile,
// не отдельное поле state, см. TmdbRepositoryImpl.getPersonProfile()).
sealed interface PersonUiState {
    object Loading : PersonUiState
    data class Success(val profile: PersonProfile) : PersonUiState
    data class Error(val message: String) : PersonUiState
}
