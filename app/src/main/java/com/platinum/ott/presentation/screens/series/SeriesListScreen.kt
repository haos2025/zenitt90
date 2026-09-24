package com.platinum.ott.presentation.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.ZenithShapeSmall

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesListScreen(onBackPressed: () -> Unit, onSeriesClick: (String) -> Unit, viewModel: SeriesListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // ФИКС (аудит): тот же паттерн, что в SourcesScreen.kt/ChannelsScreen.kt/EpgGridScreen.kt.
    LaunchedEffect(Unit) { viewModel.load() }
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

    // PROMPT_DESIGN_SYSTEM.md, подзадача 3. В отличие от MovieCard.kt,
    // здесь уже настоящий TV-нативный Surface(onClick=...) — у него
    // ЕСТЬ встроенный скейл на фокусе по умолчанию
    // (ClickableSurfaceDefaults.scale(), необъявленный дефолт которого —
    // focusedScale = 1.1f), просто нигде явно не задан. Задаю его явно
    // как 1.06f — тот же коэффициент, что и у MovieCard.kt, вместо
    // скрытого дефолта библиотеки, чтобы у обоих типов карточек была
    // одна и та же цифра, а не два случайных разных скейла в одном
    // приложении.
    //
    // Подсветка фона у Surface тоже как бы есть (focusedContainerColor),
    // но она явно приравнена к обычному containerColor — то есть
    // сознательно выключена — и даже если бы не была, здесь
    // ContentScale.Crop (не Fit, как в MovieCard.kt), картинка полностью
    // закрывает поверхность без леттербоксинга, так что смена
    // containerColor всё равно была бы не видна под ней. Поэтому
    // подсветка сделана так же, как в MovieCard.kt — отдельный
    // полупрозрачный Box поверх всего содержимого, а не через colors().
    // Фокус читается из своего interactionSource, который Surface и так
    // принимает параметром.
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        modifier = Modifier.width(cardWidth).height(cardHeight),
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        interactionSource = interactionSource
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = request, contentDescription = series.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), placeholder = ColorPainter(MaterialTheme.colorScheme.surface), error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.align(androidx.compose.ui.Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(0.7f)).padding(ZenithDimens.paddingS)) {
                Column {
                    Text(series.title, color = Color.White, maxLines = 1)
                    Text("${series.episodeCount} эп.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isFocused) {
                Box(Modifier.fillMaxSize().background(ZenithFocusContainerActive))
            }
        }
    }
}
