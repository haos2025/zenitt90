package com.platinum.ott.presentation.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.platinum.ott.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(movieId: String, onPlayClick: () -> Unit, onBackPressed: () -> Unit, onNavigateToSeries: (String) -> Unit = {}, viewModel: DetailViewModel = hiltViewModel()) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                Column(modifier = Modifier.fillMaxSize().padding(ZenithDimens.paddingXL), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                    Text(state.movie.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    state.metadata?.let { meta ->
                        meta.genres?.let { Text(it, color = Color.Gray) }
                        meta.overview?.let { Text(it, color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodyLarge) }
                        meta.voteAverage?.let { Text("★ $it", color = ZenithWarning) }
                        meta.cast?.let { Text("Актёры: $it", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                        Button(onClick = onPlayClick) { Text(if (state.watchProgress != null) "Продолжить ${(state.watchProgress * 100).toInt()}%" else "Смотреть") }
                        OutlinedButton(onClick = { viewModel.toggleFavorite(movieId, state.movie.title, state.movie.poster) }) {
                            Text(if (state.isFavorite) "♥ В избранном" else "♡ В избранное")
                        }
                        // Отметка "аниме" осмысленна только для уже
                        // добавленной в избранное записи (см. DetailViewModel.setAnime) —
                        // до этого нечего помечать, кнопка отключена.
                        if (state.isFavorite) {
                            OutlinedButton(onClick = { viewModel.setAnime(movieId, !state.isAnime) }) {
                                Text(if (state.isAnime) "✓ Аниме" else "Пометить как аниме")
                            }
                        }
                        OutlinedButton(onClick = onBackPressed) { Text("Назад") }
                    }
                }
            }
        }
    }
}
