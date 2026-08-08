package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.series.SeriesEpisodesUiState
import com.platinum.ott.presentation.screens.series.SeriesEpisodesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSeriesEpisodesScreen(seriesId: String, navController: NavHostController, viewModel: SeriesEpisodesViewModel = viewModel()) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Эпизоды") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") } },
            actions = {
                val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "В избранное", tint = if (isFavorite) Color(0xFF6C63FF) else Color.Unspecified)
                }
            }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF101010))) {
            when (val state = uiState) {
                is SeriesEpisodesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
                is SeriesEpisodesUiState.Error -> Text("⚠ ${state.message}", color = Color(0xFFFF6B6B), modifier = Modifier.padding(16.dp))
                is SeriesEpisodesUiState.Success -> {
                    val seasons = state.episodes.mapNotNull { it.seasonNumber }.distinct().sorted()
                    var selectedSeason by remember(seriesId) { mutableStateOf(seasons.firstOrNull()) }
                    Column(Modifier.fillMaxSize()) {
                        if (seasons.size > 1) {
                            ScrollableTabRow(selectedTabIndex = seasons.indexOf(selectedSeason).coerceAtLeast(0)) {
                                seasons.forEach { season ->
                                    Tab(selected = season == selectedSeason, onClick = { selectedSeason = season }, text = { Text("Сезон $season") })
                                }
                            }
                        }
                        val episodesInSeason = state.episodes.filter { selectedSeason == null || it.seasonNumber == selectedSeason }
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(episodesInSeason, key = { it.id }) { ep ->
                                Card(onClick = { navController.navigate("player/${ep.id}") }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF6C63FF))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            ep.episodeNumber?.let { Text("Эпизод $it", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                                            Text(ep.title)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
