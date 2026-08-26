package com.platinum.ott.presentation.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.TmdbImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.components.CastRow
import com.platinum.ott.presentation.components.RecommendationsRow
import com.platinum.ott.presentation.screens.favorites.MoveToFolderDialog
import com.platinum.ott.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(movieId: String, onPlayClick: () -> Unit, onBackPressed: () -> Unit, onNavigateToSeries: (String) -> Unit = {}, viewModel: DetailViewModel = hiltViewModel()) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    // Пикер папки при добавлении в избранное (PROMPT_FAVORITES_REDESIGN.md, п.2) —
    // показывается только на пути "не в избранном" -> "в избранном", снятие
    // с избранного происходит сразу, без диалога. remember(movieId) — чтобы
    // не унаследовать открытый диалог при переходе на другой фильм.
    var showAddFavoriteDialog by remember(movieId) { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            // Эпизод сериала — сразу уводим на общий экран сериала, не
            // рисуя здесь вообще ничего (см. DetailViewModel.load()).
            is DetailUiState.RedirectToSeries -> LaunchedEffect(state.seriesId) { onNavigateToSeries(state.seriesId) }
            is DetailUiState.Error -> Column(Modifier.align(Alignment.Center).padding(ZenithDimens.paddingXL)) { Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error); Button(onClick = onBackPressed) { Text("Назад") } }
            is DetailUiState.Success -> {
                // Раньше тут не было ни одной картинки — ни постера, ни backdrop'а,
                // хотя TMDB-метаданные (state.metadata) уже приходили с backdropPath.
                // Если TMDB backdrop недоступен — падаем на постер из бэкенда/плейлиста,
                // чтобы фон не был совсем пустым.
                // hasRealBackdrop: настоящий TMDB backdrop — широкий кадр, ему Crop
                // на весь экран идёт естественно. А movie.poster (для M3U/Xtream —
                // почти всегда просто логотип канала, TMDB такие каналы не находит)
                // на весь экран через Crop растягивался и обрезался до нечитаемости —
                // видно на присланных скриншотах.
                val context = LocalContext.current
                val density = LocalDensity.current
                val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
                val hasRealBackdrop = !state.metadata?.backdropPath.isNullOrBlank()
                // Раньше при отсутствии TMDB-backdrop'а сразу падали на
                // movie.poster — а это для Xtream/M3U почти всегда streamIcon,
                // который панель провайдера отдаёт как скриншот кадра потока,
                // не постер. TMDB-метаданные (state.metadata) при этом уже
                // содержат posterPath в большинстве случаев, даже когда
                // backdropPath пуст (у части фильмов в TMDB нет backdrop'а),
                // просто posterUrl() нигде не вызывался. Добавлена
                // промежуточная ступень: настоящий постер TMDB (порт­ретный,
                // Fit) — предпочтительнее сырого скриншота от провайдера, к
                // нему падаем только если TMDB вообще не нашёл фильм.
                val tmdbPosterUrl = TmdbImage.posterUrl(state.metadata?.posterPath, (screenWidthPx * 0.4f).toInt())
                val backdropUrl = TmdbImage.backdropUrl(state.metadata?.backdropPath, screenWidthPx)
                    ?: tmdbPosterUrl
                    ?: state.movie.poster.ifBlank { null }
                val backdropRequest = remember(backdropUrl, screenWidthPx) {
                    backdropUrl?.let { ImageRequest.Builder(context).data(it).size(screenWidthPx, screenWidthPx / 2).crossfade(true).build() }
                }
                if (backdropRequest != null) {
                    AsyncImage(
                        model = backdropRequest,
                        contentDescription = null,
                        contentScale = if (hasRealBackdrop) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = ColorPainter(MaterialTheme.colorScheme.background),
                        error = ColorPainter(MaterialTheme.colorScheme.background)
                    )
                    // Скрим слева направо: TV-контролы фокусируются слева, там нужен
                    // максимальный контраст текста с фоном.
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background.copy(alpha = 0.75f), Color.Transparent))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background), startY = 300f)))
                }
                // verticalScroll — раньше содержимое просто обрезалось снизу
                // без возможности докрутить (см. PROMPT_DETAIL_SCREEN_UPGRADE.md,
                // п.2): при длинном описании кнопки "Смотреть"/"В избранное"
                // могло вытолкнуть за нижний край экрана. Тот же паттерн, что
                // уже проверен на TV в SettingsScreen.kt (обычный Column +
                // verticalScroll с tv-material3 Button внутри, фокус D-pad
                // работает) — TvLazyColumn не понадобился, тут нет своего
                // отдельного набора фокусируемых строк, как в CycleSetting,
                // просто текст и ряд кнопок.
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ZenithDimens.paddingXL), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                    Text(state.movie.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    state.metadata?.let { meta ->
                        // Год/длительность — PROMPT_DETAIL_SCREEN_UPGRADE.md, п.1.
                        // Поля year/duration есть на Movie и раньше нигде не
                        // выводились ни на TV, ни на телефоне. duration может
                        // быть пустой строкой по умолчанию у части источников —
                        // пропускаем пустые части, чтобы не показать "·" без
                        // содержимого по обе стороны.
                        val metaLine = listOfNotNull(
                            state.movie.year.takeIf { it > 0 }?.toString(),
                            state.movie.duration.takeIf { it.isNotBlank() },
                            meta.genres?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (metaLine.isNotEmpty()) Text(metaLine, color = Color.Gray)
                        meta.overview?.let { Text(it, color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodyLarge) }
                        meta.voteAverage?.let { Text("★ $it", color = ZenithWarning) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                        Button(onClick = onPlayClick) { Text(if (state.watchProgress != null) "Продолжить ${(state.watchProgress * 100).toInt()}%" else "Смотреть") }
                        // Добавление показывает выбор папки, снятие — сразу,
                        // без диалога (см. DetailViewModel.addFavorite/removeFavorite).
                        // Отметка "аниме" на этом экране убрана — управление
                        // ей теперь только в едином меню на карточке в
                        // FavoritesScreen (PROMPT_FAVORITES_REDESIGN.md, п.1/п.3).
                        OutlinedButton(onClick = {
                            if (state.isFavorite) viewModel.removeFavorite(movieId) else showAddFavoriteDialog = true
                        }) {
                            Text(if (state.isFavorite) "♥ В избранном" else "♡ В избранное")
                        }
                        OutlinedButton(onClick = onBackPressed) { Text("Назад") }
                    }
                    // Карусель актёров (п.4) — не рисуется вовсе, если TMDB
                    // credits не вернул ничего (старая закэшированная запись
                    // без castJson, ошибка сети, или у фильма правда нет cast).
                    if (state.metadata?.cast?.isNotEmpty() == true) {
                        CastRow(state.metadata.cast)
                    }
                    // "Смотрите также" (п.5) — только для контента, у которого
                    // TMDB нашёл совпадение (см. DetailViewModel.load()); для
                    // собственного M3U/Xtream-плейлиста список всегда пуст.
                    if (state.recommendations.isNotEmpty()) {
                        RecommendationsRow(state.recommendations)
                    }
                }
                if (showAddFavoriteDialog) {
                    MoveToFolderDialog(
                        folders = folders,
                        onSelect = { folderId ->
                            viewModel.addFavorite(movieId, state.movie.title, state.movie.poster, folderId)
                            showAddFavoriteDialog = false
                        },
                        onDismiss = { showAddFavoriteDialog = false }
                    )
                }
            }
        }
    }
}
