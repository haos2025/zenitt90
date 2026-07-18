package com.platinum.ott.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.platinum.ott.presentation.components.CatalogRow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(onMovieClick: (String) -> Unit, onSettingsClick: () -> Unit, onFavoritesClick: () -> Unit, onHistoryClick: () -> Unit, modifier: Modifier = Modifier, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize().background(Color(0xFF101010)).padding(top = 24.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
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
                // Раньше здесь был единый LazyVerticalGrid по всем фильмам сразу —
                // CatalogRow.kt (ряды по жанрам, LazyRow с D-pad навигацией) был
                // готов, но нигде не импортировался. Группируем уже полученный
                // (один и тот же) список по Movie.genre — без изменений в
                // HomeViewModel/GetCatalogUseCase, это чисто UI-перекладка того,
                // что уже приходит с backend. Фильмы без жанра идут в общий ряд.
                val grouped = remember(state.movies) {
                    state.movies.groupBy { it.genre.ifBlank { "Каталог" } }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    grouped.forEach { (genre, movies) ->
                        item(key = genre) {
                            CatalogRow(title = genre, movies = movies, onMovieClick = onMovieClick)
                        }
                    }
                }
            }
        }
    }
}
