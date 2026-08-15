package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.series.SeriesEpisodesUiState
import com.platinum.ott.presentation.screens.series.SeriesEpisodesViewModel
import com.platinum.ott.ui.theme.*
import com.platinum.ott.core.platform.ZenithDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSeriesEpisodesScreen(seriesId: String, navController: NavHostController, viewModel: SeriesEpisodesViewModel = hiltViewModel()) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Эпизоды") },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") } },
            actions = {
                val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
                val isAnime by viewModel.isAnime.collectAsStateWithLifecycle()
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "В избранное", tint = if (isFavorite) ZenithFavorite else Color.Unspecified)
                }
                // Как и на TV: помечать аниме можно только уже добавленный в
                // избранное сериал (SeriesEpisodesViewModel.setAnime).
                if (isFavorite) {
                    IconButton(onClick = { viewModel.setAnime(!isAnime) }) {
                        Icon(Icons.Default.Movie, if (isAnime) "Снять отметку аниме" else "Пометить как аниме", tint = if (isAnime) ZenithFavorite else Color.Unspecified)
                    }
                }
            }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is SeriesEpisodesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = ZenithDimens.paddingXL))
                is SeriesEpisodesUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(ZenithDimens.paddingM))
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
                        LazyColumn(contentPadding = PaddingValues(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                            items(episodesInSeason, key = { it.id }) { ep ->
                                Card(onClick = { navController.navigate("player/${ep.id}") }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(ZenithDimens.paddingSM))
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
