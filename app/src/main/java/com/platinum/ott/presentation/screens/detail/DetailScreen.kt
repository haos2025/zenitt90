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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(movieId: String, onPlayClick: () -> Unit, onBackPressed: () -> Unit, viewModel: DetailViewModel = hiltViewModel()) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101010))) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is DetailUiState.Error -> Column(Modifier.align(Alignment.Center).padding(32.dp)) { Text("⚠ ${state.message}", color = Color(0xFFFF6B6B)); Button(onClick = onBackPressed) { Text("Назад") } }
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
                        placeholder = ColorPainter(Color(0xFF101010)),
                        error = ColorPainter(Color(0xFF101010))
                    )
                    // Скрим слева направо: TV-контролы фокусируются слева, там нужен
                    // максимальный контраст текста с фоном.
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color(0xFF101010), Color(0xFF101010).copy(alpha = 0.75f), Color.Transparent))))
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF101010)), startY = 300f)))
                }
                Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(state.movie.title, style = MaterialTheme.typography.displaySmall, color = Color.White)
                    state.metadata?.let { meta ->
                        meta.genres?.let { Text(it, color = Color.Gray) }
                        meta.overview?.let { Text(it, color = Color.White.copy(0.8f), style = MaterialTheme.typography.bodyLarge) }
                        meta.voteAverage?.let { Text("★ $it", color = Color(0xFFFFC107)) }
                        meta.cast?.let { Text("Актёры: $it", color = Color.Gray, style = MaterialTheme.typography.bodyMedium) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPlayClick) { Text(if (state.watchProgress != null) "Продолжить ${(state.watchProgress * 100).toInt()}%" else "Смотреть") }
                        OutlinedButton(onClick = { viewModel.toggleFavorite(movieId, state.movie.title, state.movie.poster) }) {
                            Text(if (state.isFavorite) "♥ В избранном" else "♡ В избранное")
                        }
                        OutlinedButton(onClick = onBackPressed) { Text("Назад") }
                    }
                }
            }
        }
    }
}
