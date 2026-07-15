package com.platinum.ott.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import com.platinum.ott.presentation.components.MovieCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(onMovieClick: (String) -> Unit, onSettingsClick: () -> Unit, onFavoritesClick: () -> Unit, onHistoryClick: () -> Unit, modifier: Modifier = Modifier, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().background(Color(0xFF101010)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("ZENITH", style = MaterialTheme.typography.displaySmall, color = Color(0xFF6C63FF), modifier = Modifier.weight(1f))
            TextButton(onClick = onFavoritesClick) { Text("Избранное") }
            TextButton(onClick = onHistoryClick) { Text("История") }
            TextButton(onClick = onSettingsClick) { Text("Настройки") }
        }
        Spacer(Modifier.height(16.dp))
        when (val state = uiState) {
            is HomeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is HomeUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("⚠ ${state.message}", color = Color(0xFFFF6B6B)); Button(onClick = { viewModel.loadCatalog() }) { Text("Повторить") } }
            is HomeUiState.Success -> {
                LazyVerticalGrid(columns = GridCells.Fixed(6), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(state.movies, key = { it.id }) { movie -> MovieCard(movie = movie, onClick = { onMovieClick(movie.id) }) }
                }
            }
        }
    }
}
