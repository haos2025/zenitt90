package com.platinum.ott.presentation.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.platinum.ott.presentation.components.MovieCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(onBackPressed: () -> Unit, onMovieClick: (String) -> Unit, viewModel: FavoritesViewModel = viewModel()) {
    val favorites by viewModel.favorites.collectAsState(initial = emptyList())
    val folders by viewModel.folders.collectAsState(initial = emptyList())
    var selectedType by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(24.dp)) {
        Text("Избранное", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selectedType == null, onClick = { selectedType = null }) { Text("Все") }
            listOf("MOVIE", "SERIES", "ANIME").forEach { type ->
                FilterChip(selectedType == type, onClick = { selectedType = if (selectedType == type) null else type }) { Text(type) }
            }
        }
        Spacer(Modifier.height(16.dp))
        val filtered = if (selectedType == null) favorites else favorites.filter { it.contentType == selectedType }
        LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(filtered, key = { it.contentId }) { fav -> MovieCard(title = fav.title, poster = fav.poster ?: "", year = 0, onClick = { onMovieClick(fav.contentId) }) }
        }
    }
}
