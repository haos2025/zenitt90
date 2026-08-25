package com.platinum.ott.presentation.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.ui.theme.*
import com.platinum.ott.core.platform.ZenithDimens

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesEpisodesScreen(seriesId: String, onBackPressed: () -> Unit, onEpisodeClick: (episodeId: String, variantUrl: String?) -> Unit, viewModel: SeriesEpisodesViewModel = hiltViewModel()) {
    LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingVariantChoice by viewModel.pendingVariantChoice.collectAsStateWithLifecycle()
    val episodeHistory by viewModel.episodeHistory.collectAsStateWithLifecycle()

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
                        val hist = episodeHistory[ep.id]
                        val progress = hist?.takeIf { it.durationMs > 0 }?.let { it.positionMs.toFloat() / it.durationMs }
                        EpisodeRow(ep.episodeNumber, ep.title, ep.poster, ep.description, progress, hist?.completed == true, onClick = {
                            viewModel.onEpisodeSelected(ep.id) { episodeId, variantUrl -> onEpisodeClick(episodeId, variantUrl) }
                        })
                    }
                }
            }
        }
    }

    // Выбор озвучки/варианта потока — до запуска плеера, не после (см.
    // SeriesEpisodesViewModel.onEpisodeSelected). Показывается только когда
    // у эпизода реально больше одного варианта — для одного варианта
    // диалог не появляется вообще, ведёт в плеер напрямую.
    pendingVariantChoice?.let { (episodeId, variants) ->
        VariantChoiceDialog(
            variants = variants,
            onSelect = { variant -> viewModel.selectVariant(episodeId, variant) { id, url -> onEpisodeClick(id, url) } },
            onDismiss = { viewModel.dismissVariantChoice() }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun VariantChoiceDialog(variants: List<StreamVariant>, onSelect: (StreamVariant) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Выберите вариант") },
        text = {
            androidx.compose.foundation.layout.Column {
                variants.forEach { v ->
                    androidx.compose.material3.TextButton(onClick = { onSelect(v) }, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.Text("${v.quality} · ${v.source}", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Отмена") } }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun SeasonTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainer, focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive)
    ) { Text(label, modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS), color = Color.White) }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable
private fun EpisodeRow(episodeNumber: Int?, title: String, poster: String, description: String, progress: Float?, completed: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = ZenithFocusContainer, focusedContainerColor = MaterialTheme.colorScheme.primary.copy(0.5f))
    ) {
        Row(Modifier.fillMaxWidth().padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
            // ep.poster/ep.description уже приходят вместе с остальными
            // данными эпизода (Xtream/M3U) — тот же паттерн миниатюры, что
            // и в HistoryScreen.kt (96×144dp, Coil AsyncImage).
            val context = LocalContext.current
            val density = LocalDensity.current
            val widthPx = with(density) { 96.dp.roundToPx() }
            val heightPx = with(density) { 144.dp.roundToPx() }
            val request = remember(poster, widthPx, heightPx) {
                ImageRequest.Builder(context).data(poster).size(widthPx, heightPx).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)
            )
            Spacer(Modifier.width(ZenithDimens.paddingSM))
            episodeNumber?.let { Text("$it.", color = Color.Gray, modifier = Modifier.padding(end = ZenithDimens.paddingSM)) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, modifier = Modifier.weight(1f, fill = false))
                    if (completed) {
                        Spacer(Modifier.width(ZenithDimens.paddingXS))
                        Icon(Icons.Default.CheckCircle, contentDescription = "Просмотрено", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                if (description.isNotBlank()) {
                    Text(description, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = ZenithDimens.paddingXS))
                }
                // Полоска прогресса — только для начатых, но не досмотренных
                // (completed уже показан галочкой выше, дублировать не нужно).
                if (!completed && progress != null && progress > 0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS))
                }
            }
        }
    }
}
