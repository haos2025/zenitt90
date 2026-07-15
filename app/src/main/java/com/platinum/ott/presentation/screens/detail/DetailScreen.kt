package com.platinum.ott.presentation.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(movieId: String, onPlayClick: () -> Unit, onBackPressed: () -> Unit, viewModel: DetailViewModel = viewModel()) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(32.dp)) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is DetailUiState.Error -> Column(Modifier.align(Alignment.Center)) { Text("⚠ ${state.message}", color = Color(0xFFFF6B6B)); Button(onClick = onBackPressed) { Text("Назад") } }
            is DetailUiState.Success -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.movie.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    state.metadata?.let { meta ->
                        meta.genres?.let { Text(it, color = Color.Gray) }
                        meta.overview?.let { Text(it, color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodyLarge) }
                        meta.voteAverage?.let { Text("★ $it", color = Color(0xFFFFC107)) }
                        meta.cast?.let { Text("Актёры: $it", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPlayClick) { Text(if (state.watchProgress != null) "Продолжить ${(state.watchProgress * 100).toInt()}%" else "Смотреть") }
                        OutlinedButton(onClick = { viewModel.toggleFavorite(movieId, state.movie.title, state.movie.poster) }) {
                            Text(if (state.isFavorite) "♥ В избранном" else "♡ В избранное")
                        }
                        OutlinedButton(onClick = onBackPressed) { Text("Назад") }
                    }
                }
            }
        }
    }
}
