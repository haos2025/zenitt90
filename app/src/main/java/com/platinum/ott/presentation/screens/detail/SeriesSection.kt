package com.platinum.ott.presentation.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.domain.model.CastMember
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.model.Recommendation
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.presentation.components.CastRow
import com.platinum.ott.presentation.components.RecommendationsRow
import com.platinum.ott.presentation.components.TvPillButton
import com.platinum.ott.ui.theme.*

/** Подпись серии для кнопки и списков: «С2·Э5», «Серия 5» или название. */
internal fun episodeLabel(ep: Movie): String {
    val s = ep.seasonNumber
    val e = ep.episodeNumber
    return when {
        s != null && e != null -> "С$s·Э$e"
        e != null -> "Серия $e"
        else -> ep.title
    }
}

private fun seasonLabel(season: Int?) = season?.let { "Сезон $it" } ?: "Без сезона"

private const val RANGE_SIZE = 50
// Сезон длиннее этого порога делится на диапазоны по RANGE_SIZE и показывается компактными строками.
private const val LONG_SEASON = 60
private const val MAX_SEASON_CHIPS = 8

/**
 * Нижняя «страница» окна «Деталь» у сериала: вкладки «Серии · Актёры · Похожие».
 * Вкладки нужны, чтобы длинный список серий не закрывал путь к актёрам и рекомендациям.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeriesSection(
    series: SeriesInfo,
    cast: List<CastMember>,
    recommendations: List<Recommendation>,
    onEpisodeClick: (Movie) -> Unit,
    onCastClick: (Int) -> Unit,
    onRecommendationClick: (Recommendation) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(0) }
    val tabs = remember(cast, recommendations) {
        buildList { add("Серии"); if (cast.isNotEmpty()) add("Актёры"); if (recommendations.isNotEmpty()) add("Похожие") }
    }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
            tabs.forEachIndexed { i, title ->
                SeriesChip(title, selected = tab == i, selectOnFocus = true, onSelect = { tab = i })
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tabs.getOrNull(tab)) {
                "Актёры" -> CastRow(cast, showRoles = false, onMemberClick = { onCastClick(it.id) })
                "Похожие" -> RecommendationsRow(recommendations, onItemClick = onRecommendationClick)
                else -> EpisodesPane(series, onEpisodeClick)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodesPane(series: SeriesInfo, onEpisodeClick: (Movie) -> Unit) {
    val groups = remember(series.episodes) { series.episodes.groupBy { it.seasonNumber } }
    val seasonKeys = remember(groups) {
        val l = ArrayList<Int?>(groups.keys.filterNotNull().sorted())
        if (groups.containsKey(null)) l.add(null)
        l
    }
    val resumeEp = series.resume.episode
    // Открываем сезон, в котором находится «продолжить» — в длинных сериалах не листать с первого.
    var season by remember(series.episodes) {
        mutableStateOf(if (resumeEp.seasonNumber in seasonKeys) resumeEp.seasonNumber else seasonKeys.firstOrNull())
    }
    val seasonEpisodes = groups[season].orEmpty()
    val needRanges = seasonEpisodes.size > LONG_SEASON
    val rangeCount = if (needRanges) (seasonEpisodes.size + RANGE_SIZE - 1) / RANGE_SIZE else 1
    var range by remember(season) {
        mutableStateOf(run {
            val i = seasonEpisodes.indexOfFirst { it.id == resumeEp.id }
            if (needRanges && i >= 0) i / RANGE_SIZE else 0
        })
    }
    val visible = if (needRanges) seasonEpisodes.drop(range * RANGE_SIZE).take(RANGE_SIZE) else seasonEpisodes
    // Компактные строки (без миниатюр): очень длинные сезоны и сезоны, где у всех серий один и тот же постер.
    val compact = needRanges || seasonEpisodes.map { it.poster }.filter { it.isNotBlank() }.distinct().size <= 1
    val watched = seasonEpisodes.count { series.history[it.id]?.completed == true }
    var showPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (seasonKeys.size > 1) {
            if (seasonKeys.size <= MAX_SEASON_CHIPS) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                    items(seasonKeys) { k ->
                        SeriesChip(seasonLabel(k), selected = k == season, selectOnFocus = false, onSelect = { season = k })
                    }
                }
            } else {
                Row { SeriesChip("${seasonLabel(season)}  ▾", selected = true, selectOnFocus = false, onSelect = { showPicker = true }) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
        }
        if (needRanges) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                items((0 until rangeCount).toList()) { r ->
                    val from = r * RANGE_SIZE + 1
                    val to = minOf(seasonEpisodes.size, (r + 1) * RANGE_SIZE)
                    SeriesChip("$from–$to", selected = r == range, selectOnFocus = false, onSelect = { range = r })
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
        }
        Text(
            "Просмотрено $watched из ${seasonEpisodes.size}",
            style = MaterialTheme.typography.labelMedium,
            color = ZenithOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        key(season, range) {
            val startIdx = (visible.indexOfFirst { it.id == resumeEp.id } - 1).coerceAtLeast(0)
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIdx)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { ep ->
                    EpisodeItem(ep, series.history[ep.id], compact, onClick = { onEpisodeClick(ep) })
                }
            }
        }
    }

    if (showPicker) {
        SeasonPickerDialog(
            items = seasonKeys.map { k ->
                val eps = groups[k].orEmpty()
                Triple(k, eps.size, eps.count { series.history[it.id]?.completed == true })
            },
            current = season,
            onPick = { season = it; showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/** Вкладка/чип: фокус — заливка акцентом и рамка (без белого). selectOnFocus — выбор сразу при фокусе (вкладки). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeriesChip(title: String, selected: Boolean, selectOnFocus: Boolean, onSelect: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onSelect,
        modifier = Modifier.onFocusChanged { if (selectOnFocus && it.hasFocus) onSelect() },
        shape = ClickableSurfaceDefaults.shape(ZenithShapePill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.28f) else ZenithOnSurface.copy(alpha = 0.08f),
            contentColor = ZenithOnSurface,
            focusedContainerColor = accent.copy(alpha = 0.40f),
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.50f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, accent))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeItem(ep: Movie, hist: WatchHistoryEntity?, compact: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val completed = hist?.completed == true
    val progress = hist?.takeIf { it.durationMs > 0 && !it.completed && it.positionMs > 0 }
        ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ZenithFocusContainer,
            contentColor = ZenithOnSurface,
            focusedContainerColor = accent.copy(alpha = 0.28f),
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.40f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, accent))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        if (compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ep.episodeNumber?.let { "$it." } ?: "•", color = ZenithOnSurfaceVariant, modifier = Modifier.width(56.dp), maxLines = 1)
                Text(ep.title, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (completed) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Просмотрено", tint = accent, modifier = Modifier.size(18.dp))
                } else if (progress != null) {
                    Text("${(progress * 100).toInt()}%", color = accent, style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                val context = LocalContext.current
                val density = LocalDensity.current
                val widthPx = with(density) { 64.dp.roundToPx() }
                val heightPx = with(density) { 96.dp.roundToPx() }
                val request = remember(ep.poster, widthPx, heightPx) {
                    ImageRequest.Builder(context).data(ep.poster).size(widthPx, heightPx).crossfade(true).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = ep.title,
                    contentScale = ContentScale.Fit,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.width(64.dp).height(96.dp).clip(ZenithShapeSmall).background(MaterialTheme.colorScheme.surface)
                )
                Spacer(Modifier.width(ZenithDimens.paddingSM))
                ep.episodeNumber?.let { Text("$it.", color = ZenithOnSurfaceVariant, modifier = Modifier.padding(end = ZenithDimens.paddingSM)) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ep.title, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (completed) {
                            Spacer(Modifier.width(ZenithDimens.paddingXS))
                            Icon(Icons.Default.CheckCircle, contentDescription = "Просмотрено", tint = accent, modifier = Modifier.size(16.dp))
                        }
                    }
                    if (ep.description.isNotBlank()) {
                        Text(ep.description, color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Выбор сезона, когда их больше восьми: список «Сезон N · просмотрено X из Y», фокус на текущем. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonPickerDialog(items: List<Triple<Int?, Int, Int>>, current: Int?, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val currentIndex = items.indexOfFirst { it.first == current }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface)
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.10f), ZenithShapeLarge)
                .padding(ZenithDimens.paddingL)
        ) {
            Text("Сезон", style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.first ?: -1 }) { (k, total, watched) ->
                    Surface(
                        onClick = { onPick(k) },
                        modifier = Modifier.fillMaxWidth().then(if (k == current) Modifier.focusRequester(currentFocus) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (k == current) accent.copy(alpha = 0.18f) else ZenithFocusContainer,
                            contentColor = ZenithOnSurface,
                            focusedContainerColor = accent.copy(alpha = 0.34f),
                            focusedContentColor = ZenithOnSurface,
                            pressedContainerColor = accent.copy(alpha = 0.46f),
                            pressedContentColor = ZenithOnSurface
                        ),
                        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, accent))),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(seasonLabel(k), color = ZenithOnSurface, modifier = Modifier.weight(1f))
                            Text("$watched / $total", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        currentFocus.safeRequestFocus()
    }
}

/** Выбор озвучки/варианта потока серии, когда их больше одного; «Назад» закрывает. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeVariantDialog(variants: List<StreamVariant>, onSelect: (StreamVariant) -> Unit, onDismiss: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface)
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.10f), ZenithShapeLarge)
                .padding(ZenithDimens.paddingL)
        ) {
            Text("Выберите вариант", style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                variants.forEachIndexed { i, v ->
                    TvPillButton(
                        Icons.Default.PlayArrow, { onSelect(v) },
                        Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        label = "${v.quality} · ${v.source}"
                    )
                }
                TvPillButton(Icons.Default.Close, onDismiss, Modifier.fillMaxWidth(), label = "Отмена")
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        firstFocus.safeRequestFocus()
    }
}
