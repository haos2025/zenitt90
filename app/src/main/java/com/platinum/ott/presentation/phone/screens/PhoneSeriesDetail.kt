package com.platinum.ott.presentation.phone.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.TmdbImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.model.StreamVariant
import com.platinum.ott.presentation.components.CastRow
import com.platinum.ott.presentation.components.RecommendationsRow
import com.platinum.ott.presentation.screens.detail.DetailUiState
import com.platinum.ott.presentation.screens.detail.DetailViewModel
import com.platinum.ott.presentation.screens.detail.ResumeKind
import com.platinum.ott.presentation.screens.detail.SeriesInfo
import com.platinum.ott.presentation.screens.detail.episodeLabel
import com.platinum.ott.presentation.screens.favorites.MoveToFolderDialog
import com.platinum.ott.ui.theme.*
import kotlinx.coroutines.launch

private const val PH_RANGE_SIZE = 50
private const val PH_LONG_SEASON = 60
private const val PH_MAX_SEASON_CHIPS = 8

private fun phSeasonLabel(season: Int?) = season?.let { "Сезон $it" } ?: "Без сезона"

/**
 * Единое окно «Деталь» телефона для сериала: шапка как у фильма, кнопка «Смотреть/Продолжить/Дальше»,
 * затем вкладки «Серии · Актёры · Похожие». Один LazyColumn на всё окно — серии рисуются лениво,
 * поэтому сериалы на сотни серий не тормозят.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSeriesDetail(
    state: DetailUiState.Success,
    series: SeriesInfo,
    seriesId: String,
    navController: NavHostController,
    viewModel: DetailViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingVariantChoice by viewModel.pendingVariantChoice.collectAsStateWithLifecycle()
    var showAddFavoriteDialog by remember(seriesId) { mutableStateOf(false) }

    fun navigateToPlayer(episodeId: String, variantUrl: String?) {
        val route = if (variantUrl != null) "player/$episodeId?variantUrl=${android.net.Uri.encode(variantUrl)}" else "player/$episodeId"
        navController.navigate(route)
    }
    val play: (Movie) -> Unit = { ep -> viewModel.onEpisodeSelected(ep.id) { id, url -> navigateToPlayer(id, url) } }

    val cast = state.metadata?.cast.orEmpty()
    val tabs = remember(cast, state.recommendations) {
        buildList { add("Серии"); if (cast.isNotEmpty()) add("Актёры"); if (state.recommendations.isNotEmpty()) add("Похожие") }
    }
    var tab by remember(seriesId) { mutableStateOf(0) }

    // Группировка серий по сезонам; очень длинные сезоны делятся на диапазоны по 50.
    val groups = remember(series.episodes) { series.episodes.groupBy { it.seasonNumber } }
    val seasonKeys = remember(groups) {
        val l = ArrayList<Int?>(groups.keys.filterNotNull().sorted())
        if (groups.containsKey(null)) l.add(null)
        l
    }
    val resumeEp = series.resume.episode
    var season by remember(series.episodes) {
        mutableStateOf(if (resumeEp.seasonNumber in seasonKeys) resumeEp.seasonNumber else seasonKeys.firstOrNull())
    }
    val seasonEpisodes = groups[season].orEmpty()
    val needRanges = seasonEpisodes.size > PH_LONG_SEASON
    val rangeCount = if (needRanges) (seasonEpisodes.size + PH_RANGE_SIZE - 1) / PH_RANGE_SIZE else 1
    var range by remember(season) {
        mutableStateOf(run {
            val i = seasonEpisodes.indexOfFirst { it.id == resumeEp.id }
            if (needRanges && i >= 0) i / PH_RANGE_SIZE else 0
        })
    }
    val visible = if (needRanges) seasonEpisodes.drop(range * PH_RANGE_SIZE).take(PH_RANGE_SIZE) else seasonEpisodes
    val compact = needRanges || seasonEpisodes.map { it.poster }.filter { it.isNotBlank() }.distinct().size <= 1
    val watched = seasonEpisodes.count { series.history[it.id]?.completed == true }

    val listState = rememberLazyListState()
    // Один раз при открытии: если серия «продолжить» глубоко в списке — подводим к ней (шапка остаётся выше).
    LaunchedEffect(series.episodes) {
        val idx = visible.indexOfFirst { it.id == resumeEp.id }
        if (idx > 3) {
            val before = 2 + (if (seasonKeys.size > 1) 1 else 0) + (if (needRanges) 1 else 0) + 1
            listState.scrollToItem(before + idx - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(ZenithDimens.paddingM),
        verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
    ) {
        // --- Шапка ---
        item(key = "hero") {
            Column {
                val density = LocalDensity.current
                val widthPx = with(density) { (LocalConfiguration.current.screenWidthDp.dp - ZenithDimens.paddingM * 2).roundToPx() }
                val heightPx = with(density) { 220.dp.roundToPx() }
                val hasRealBackdrop = !state.metadata?.backdropPath.isNullOrBlank()
                val tmdbPosterUrl = TmdbImage.posterUrl(state.metadata?.posterPath, (widthPx * 0.6f).toInt())
                val imageUrl = TmdbImage.backdropUrl(state.metadata?.backdropPath, widthPx)
                    ?: tmdbPosterUrl
                    ?: state.movie.poster.ifBlank { null }
                val request = remember(imageUrl, widthPx, heightPx) {
                    imageUrl?.let { ImageRequest.Builder(context).data(it).size(widthPx, heightPx).crossfade(true).build() }
                }
                if (request != null) {
                    AsyncImage(
                        model = request,
                        contentDescription = state.movie.title,
                        contentScale = if (hasRealBackdrop) ContentScale.Crop else ContentScale.Fit,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(ZenithShapeMedium).background(MaterialTheme.colorScheme.surface)
                    )
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                }
                Text(state.movie.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = ZenithOnSurface)

                val seasonsCount = series.episodes.mapNotNull { it.seasonNumber }.distinct().size
                val m = state.metadata
                val genres = m?.genres.orEmpty().split(',', '/').map { it.trim() }.filter { it.isNotEmpty() }.take(4)
                Row(
                    modifier = Modifier.padding(top = ZenithDimens.paddingS).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                ) {
                    m?.voteAverage?.let { PhoneDetailPill("★ $it", ZenithWarning, ZenithWarning.copy(alpha = 0.16f)) }
                    if (seasonsCount > 1) PhoneDetailPill("$seasonsCount сезонов", ZenithOnSurface, ZenithSurfaceVariant)
                    PhoneDetailPill("${series.episodes.size} серий", ZenithOnSurface, ZenithSurfaceVariant)
                    genres.forEach { PhoneDetailPill(it, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) }
                }
                m?.overview?.let { overview ->
                    var expanded by remember(seriesId) { mutableStateOf(false) }
                    var expandable by remember(seriesId) { mutableStateOf(false) }
                    Text(
                        overview, color = ZenithOnSurface.copy(0.8f),
                        modifier = Modifier.padding(top = ZenithDimens.paddingS),
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) expandable = it.hasVisualOverflow }
                    )
                    if (expandable) {
                        Text(
                            if (expanded) "Свернуть" else "Показать полностью",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = ZenithDimens.paddingXS).clickable { expanded = !expanded }
                        )
                    }
                }
                Spacer(Modifier.height(ZenithDimens.paddingM))
                Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                    val r = series.resume
                    val pct = r.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
                    val label = when (r.kind) {
                        ResumeKind.CONTINUE -> "Продолжить ${episodeLabel(r.episode)}$pct"
                        ResumeKind.NEXT -> "Дальше ${episodeLabel(r.episode)}"
                        ResumeKind.START -> "Смотреть ${episodeLabel(r.episode)}"
                    }
                    Button(onClick = { play(r.episode) }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                    OutlinedButton(onClick = {
                        when {
                            state.isFavorite -> viewModel.removeFavorite(seriesId)
                            folders.isEmpty() -> viewModel.addFavorite(seriesId, state.movie.title, state.movie.poster, null)
                            else -> showAddFavoriteDialog = true
                        }
                    }) { Text(if (state.isFavorite) "♥" else "♡") }
                }
            }
        }

        // --- Вкладки ---
        item(key = "tabs") {
            TabRow(selectedTabIndex = tab.coerceIn(0, tabs.lastIndex), containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
            }
        }

        when (tabs.getOrNull(tab)) {
            "Актёры" -> item(key = "cast") {
                CastRow(cast, onMemberClick = { navController.navigate("person/${it.id}") })
            }
            "Похожие" -> item(key = "recs") {
                RecommendationsRow(state.recommendations, onItemClick = { rec ->
                    scope.launch {
                        val foundId = viewModel.findInCatalog(rec)
                        if (foundId != null) navController.navigate("detail/$foundId")
                        else navController.navigate("tmdb/${if (rec.isTv) "tv" else "movie"}/${rec.tmdbId}")
                    }
                })
            }
            else -> {
                if (seasonKeys.size > 1) {
                    item(key = "seasons") {
                        if (seasonKeys.size <= PH_MAX_SEASON_CHIPS) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                                items(seasonKeys) { k ->
                                    FilterChip(selected = k == season, onClick = { season = k }, label = { Text(phSeasonLabel(k)) })
                                }
                            }
                        } else {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { menu = true }) { Text("${phSeasonLabel(season)}  ▾") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    seasonKeys.forEach { k ->
                                        val eps = groups[k].orEmpty()
                                        val done = eps.count { series.history[it.id]?.completed == true }
                                        DropdownMenuItem(
                                            text = { Text("${phSeasonLabel(k)} · $done / ${eps.size}") },
                                            onClick = { season = k; menu = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (needRanges) {
                    item(key = "ranges") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                            items((0 until rangeCount).toList()) { r ->
                                val from = r * PH_RANGE_SIZE + 1
                                val to = minOf(seasonEpisodes.size, (r + 1) * PH_RANGE_SIZE)
                                FilterChip(selected = r == range, onClick = { range = r }, label = { Text("$from–$to") })
                            }
                        }
                    }
                }
                item(key = "summary") {
                    Text("Просмотрено $watched из ${seasonEpisodes.size}", style = MaterialTheme.typography.labelMedium, color = ZenithOnSurfaceVariant)
                }
                items(visible, key = { it.id }) { ep ->
                    PhoneEpisodeRow(ep, series.history[ep.id], compact, onClick = { play(ep) })
                }
            }
        }
    }

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

    if (showAddFavoriteDialog) {
        MoveToFolderDialog(
            folders = folders,
            onSelect = { folderId ->
                viewModel.addFavorite(seriesId, state.movie.title, state.movie.poster, folderId)
                showAddFavoriteDialog = false
            },
            onDismiss = { showAddFavoriteDialog = false }
        )
    }
}

@Composable
private fun PhoneEpisodeRow(ep: Movie, hist: com.platinum.ott.data.local.entity.WatchHistoryEntity?, compact: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val completed = hist?.completed == true
    val progress = hist?.takeIf { it.durationMs > 0 && !it.completed && it.positionMs > 0 }
        ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(if (compact) PaddingValues(horizontal = ZenithDimens.paddingM, vertical = 12.dp) else PaddingValues(ZenithDimens.paddingS)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (compact) {
            Text(ep.episodeNumber?.let { "$it." } ?: "•", color = ZenithOnSurfaceVariant, modifier = Modifier.width(44.dp), maxLines = 1)
            Text(ep.title, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (completed) Icon(Icons.Default.CheckCircle, "Просмотрено", tint = accent, modifier = Modifier.size(18.dp))
            else if (progress != null) Text("${(progress * 100).toInt()}%", color = accent, style = MaterialTheme.typography.labelMedium)
        } else {
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
                modifier = Modifier.width(56.dp).height(84.dp).clip(ZenithShapeSmall)
            )
            Spacer(Modifier.width(ZenithDimens.paddingSM))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ep.episodeNumber?.let { Text("Эпизод $it", style = MaterialTheme.typography.bodySmall, color = ZenithOnSurfaceVariant) }
                    if (completed) {
                        Spacer(Modifier.width(ZenithDimens.paddingXS))
                        Icon(Icons.Default.CheckCircle, "Просмотрено", tint = accent, modifier = Modifier.size(14.dp))
                    }
                }
                Text(ep.title, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ep.description.isNotBlank()) {
                    Text(ep.description, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}
