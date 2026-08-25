package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.presentation.screens.series.SeriesEpisodesUiState
import com.platinum.ott.presentation.screens.series.SeriesEpisodesViewModel
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.ui.theme.*
import com.platinum.ott.core.platform.ZenithDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSeriesEpisodesScreen(seriesId: String, navController: NavHostController, viewModel: SeriesEpisodesViewModel = hiltViewModel()) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingVariantChoice by viewModel.pendingVariantChoice.collectAsStateWithLifecycle()
    val episodeHistory by viewModel.episodeHistory.collectAsStateWithLifecycle()

    // Общий для TV/телефона способ собрать "player/{id}?variantUrl=..." —
    // тот же приём, что в ZenithNavHost.kt (там для TV-варианта).
    fun navigateToPlayer(episodeId: String, variantUrl: String?) {
        val route = if (variantUrl != null) "player/$episodeId?variantUrl=${android.net.Uri.encode(variantUrl)}" else "player/$episodeId"
        navController.navigate(route)
    }

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
                                val hist = episodeHistory[ep.id]
                                val progress = hist?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs }
                                Card(onClick = { viewModel.onEpisodeSelected(ep.id) { episodeId, variantUrl -> navigateToPlayer(episodeId, variantUrl) } }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
                                        // Раньше здесь была статичная иконка PlayArrow вместо
                                        // миниатюры эпизода — ep.poster уже приходит вместе с
                                        // остальными данными (тот же принцип, что и в
                                        // PhoneHistoryScreen.kt, 56×84dp). Play-иконка оставлена
                                        // полупрозрачным оверлеем поверх — визуальная подсказка
                                        // "это можно нажать" сохраняется.
                                        Box {
                                            val context = LocalContext.current
                                            val density = LocalDensity.current
                                            val widthPx = with(density) { 56.dp.roundToPx() }
                                            val heightPx = with(density) { 84.dp.roundToPx() }
                                            val request = remember(ep.poster, widthPx, heightPx) {
                                                ImageRequest.Builder(context).data(ep.poster).size(widthPx, heightPx).crossfade(true).build()
                                            }
                                            AsyncImage(
                                                model = request,
                                                contentDescription = ep.title,
                                                contentScale = ContentScale.Fit,
                                                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                            Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.Center).size(20.dp))
                                        }
                                        Spacer(Modifier.width(ZenithDimens.paddingSM))
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ep.episodeNumber?.let { Text("Эпизод $it", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                                                if (hist?.completed == true) {
                                                    Spacer(Modifier.width(ZenithDimens.paddingXS))
                                                    Icon(Icons.Default.CheckCircle, contentDescription = "Просмотрено", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Text(ep.title)
                                            if (ep.description.isNotBlank()) {
                                                Text(ep.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = ZenithDimens.paddingXS))
                                            }
                                            if (hist?.completed != true && progress != null && progress > 0f) {
                                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS))
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

    // Выбор озвучки/варианта потока — только когда у эпизода реально
    // больше одного (см. SeriesEpisodesViewModel.onEpisodeSelected).
    pendingVariantChoice?.let { (episodeId, variants) ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissVariantChoice() },
            title = { Text("Выберите вариант") },
            text = {
                Column {
                    variants.forEach { v: StreamVariant ->
                        TextButton(
                            onClick = { viewModel.selectVariant(episodeId, v) { id, url -> navigateToPlayer(id, url) } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${v.quality} · ${v.source}", modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.dismissVariantChoice() }) { Text("Отмена") } }
        )
    }
}
