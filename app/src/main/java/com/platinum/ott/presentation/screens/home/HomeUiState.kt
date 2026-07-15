package com.platinum.ott.presentation.screens.home
import com.platinum.ott.domain.model.Movie
sealed interface HomeUiState { object Loading : HomeUiState; data class Success(val movies: List<Movie>, val page: Int, val totalPages: Int, val isOffline: Boolean = false) : HomeUiState; data class Error(val message: String) : HomeUiState }
