package com.platinum.ott.presentation.screens.setup
sealed interface SetupUiState { object Idle : SetupUiState; object Loading : SetupUiState; data class Error(val message: String) : SetupUiState }
