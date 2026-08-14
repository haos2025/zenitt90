package com.platinum.ott.presentation.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.SeriesSummary

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesListScreen(onBackPressed: () -> Unit, onSeriesClick: (String) -> Unit, viewModel: SeriesListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackPressed) { Text("← Назад") }
            Spacer(Modifier.width(ZenithDimens.paddingM))
            Text("Сериалы", style = MaterialTheme.typography.displaySmall, color = Color.White)
        }
        Spacer(Modifier.height(ZenithDimens.paddingL))
        when (val state = uiState) {
            is SeriesListUiState.Loading -> Box(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXL), androidx.compose.ui.Alignment.TopCenter) { CircularProgressIndicator() }
            is SeriesListUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
            is SeriesListUiState.Success -> {
                if (state.series.isEmpty()) {
                    // Честно: если источник — только backend-каталог без
                    // Xtream/M3U с сериалами, здесь и будет пусто — это не
                    // баг, у backend-контента нет понятия "сериал".
                    Text("Сериалов не найдено. Раздел заполняется из Xtream (get_series) и M3U (по названиям вида S01E02).", color = Color.Gray)
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                        items(state.series, key = { it.seriesId }) { s -> SeriesCard(s, onClick = { onSeriesClick(s.seriesId) }) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesCard(series: SeriesSummary, onClick: () -> Unit) {
    val cardWidth = ZenithDimens.cardWidth
    val cardHeight = ZenithDimens.cardHeight
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { cardWidth.roundToPx() }
    val heightPx = with(density) { cardHeight.roundToPx() }
    val request = remember(series.poster, widthPx, heightPx) {
        ImageRequest.Builder(context).data(series.poster).size(widthPx, heightPx).crossfade(true).build()
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.width(cardWidth).height(cardHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = request, contentDescription = series.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), placeholder = ColorPainter(MaterialTheme.colorScheme.surface), error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.align(androidx.compose.ui.Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(0.7f)).padding(ZenithDimens.paddingS)) {
                Column {
                    Text(series.title, color = Color.White, maxLines = 1)
                    Text("${series.episodeCount} эп.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
