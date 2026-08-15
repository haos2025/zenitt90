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
import com.platinum.ott.ui.theme.*
import com.platinum.ott.core.platform.ZenithDimens

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesEpisodesScreen(seriesId: String, onBackPressed: () -> Unit, onEpisodeClick: (String) -> Unit, viewModel: SeriesEpisodesViewModel = hiltViewModel()) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackPressed) { Text("← Назад") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))
        when (val state = uiState) {
            is SeriesEpisodesUiState.Loading -> Box(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXL), Alignment.TopCenter) { CircularProgressIndicator() }
            is SeriesEpisodesUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
            is SeriesEpisodesUiState.Success -> {
                val seasons = state.episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
                var selectedSeason by remember(seriesId) { mutableStateOf(seasons.firstOrNull()) }
                val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
                val isAnime by viewModel.isAnime.collectAsStateWithLifecycle()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.episodes.firstOrNull()?.seriesTitle ?: "Сериал", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { viewModel.toggleFavorite() }) { Text(if (isFavorite) "♥ В избранном" else "♡ В избранное") }
                    // Как и в DetailScreen.kt: помечать аниме можно только уже
                    // добавленный в избранное сериал.
                    if (isFavorite) {
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        OutlinedButton(onClick = { viewModel.setAnime(!isAnime) }) { Text(if (isAnime) "✓ Аниме" else "Пометить как аниме") }
                    }
                }
                Spacer(Modifier.height(ZenithDimens.paddingM))
                if (seasons.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                        seasons.forEach { season ->
                            SeasonTab("Сезон $season", season == selectedSeason) { selectedSeason = season }
                        }
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                }
                val episodesInSeason = state.episodes.filter { selectedSeason == null || it.seasonNumber == selectedSeason }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
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
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainer, focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive)
    ) { Text(label, modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS), color = Color.White) }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun EpisodeRow(episodeNumber: Int?, title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = ZenithFocusContainer, focusedContainerColor = MaterialTheme.colorScheme.primary.copy(0.5f))
    ) {
        Row(Modifier.fillMaxWidth().padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
            episodeNumber?.let { Text("$it.", color = Color.Gray, modifier = Modifier.padding(end = ZenithDimens.paddingSM)) }
            Text(title, color = Color.White)
        }
    }
}
