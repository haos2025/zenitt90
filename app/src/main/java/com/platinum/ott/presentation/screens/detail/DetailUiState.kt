package com.platinum.ott.presentation.screens.detail
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.model.TmdbMetadata
sealed interface DetailUiState { object Loading : DetailUiState; data class Success(val movie: Movie, val metadata: TmdbMetadata? = null, val isFavorite: Boolean = false, val watchProgress: Float? = null) : DetailUiState; data class Error(val message: String) : DetailUiState }
