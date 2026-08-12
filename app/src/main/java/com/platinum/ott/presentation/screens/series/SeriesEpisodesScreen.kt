package com.platinum.ott.presentation.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesEpisodesScreen(seriesId: String, onBackPressed: () -> Unit, onEpisodeClick: (String) -> Unit, viewModel: SeriesEpisodesViewModel = hiltViewModel()) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(start = 56.dp, top = 56.dp, end = 56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackPressed) { Text("← Назад") }
        }
        Spacer(Modifier.height(16.dp))
        when (val state = uiState) {
            is SeriesEpisodesUiState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.TopCenter) { CircularProgressIndicator() }
            is SeriesEpisodesUiState.Error -> Text("⚠ ${state.message}", color = Color(0xFFFF6B6B))
            is SeriesEpisodesUiState.Success -> {
                val seasons = state.episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
                var selectedSeason by remember(seriesId) { mutableStateOf(seasons.firstOrNull()) }
                val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.episodes.firstOrNull()?.seriesTitle ?: "Сериал", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { viewModel.toggleFavorite() }) { Text(if (isFavorite) "♥ В избранном" else "♡ В избранное") }
                }
                Spacer(Modifier.height(16.dp))
                if (seasons.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seasons.forEach { season ->
                            SeasonTab("Сезон $season", season == selectedSeason) { selectedSeason = season }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                val episodesInSeason = state.episodes.filter { selectedSeason == null || it.seasonNumber == selectedSeason }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(episodesInSeason, key = { it.id }) { ep ->
                        EpisodeRow(ep.episodeNumber, ep.title, onClick = { onEpisodeClick(ep.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun SeasonTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) Color(0xFF6C63FF) else Color.White.copy(0.08f), focusedContainerColor = if (selected) Color(0xFF6C63FF) else Color.White.copy(0.18f))
    ) { Text(label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White) }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun EpisodeRow(episodeNumber: Int?, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(0.05f), focusedContainerColor = Color(0xFF6C63FF).copy(0.5f))
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            episodeNumber?.let { Text("$it.", color = Color.Gray, modifier = Modifier.padding(end = 12.dp)) }
            Text(title, color = Color.White)
        }
    }
}
