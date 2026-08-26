package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.platinum.ott.domain.model.TmdbMetadata
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.TmdbImage
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.components.CastRow
import com.platinum.ott.presentation.components.RecommendationsRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.detail.DetailUiState
import com.platinum.ott.presentation.screens.detail.DetailViewModel
import com.platinum.ott.presentation.screens.favorites.MoveToFolderDialog
import com.platinum.ott.ui.theme.*

@Composable
fun PhoneDetailScreen(movieId: String, navController: NavHostController, viewModel: DetailViewModel = hiltViewModel()) {
    LaunchedEffect(movieId) { viewModel.load(movieId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    // Как и на TV: диалог выбора папки только при добавлении, снятие с
    // избранного — сразу (PROMPT_FAVORITES_REDESIGN.md, п.2).
    var showAddFavoriteDialog by remember(movieId) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(ZenithDimens.paddingM)) {
        when (val state = uiState) {
            is DetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            // Эпизод сериала — сразу уводим на общий экран сериала вместо
            // отдельной детальной карточки (см. DetailViewModel.load()).
            // popUpTo убирает саму карточку-редирект из бэкстека — иначе
            // "Назад" с экрана сериала вернул бы на пустой промежуточный шаг.
            is DetailUiState.RedirectToSeries -> LaunchedEffect(state.seriesId) {
                navController.navigate("series/${state.seriesId}") { popUpTo("detail/{movieId}") { inclusive = true } }
            }
            is DetailUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
            is DetailUiState.Success -> {
                // Раньше здесь тоже не было ни одной картинки — только текст.
                // hasRealBackdrop разделяет два принципиально разных случая:
                // настоящий TMDB backdrop_path — это широкое кадрированное
                // изображение, ему Crop идёт естественно. А когда TMDB-метаданных
                // нет (для M3U/Xtream-каналов это почти всегда так — это не
                // фильмы, TMDB их не находит) и мы просто берём movie.poster —
                // это маленький квадратный логотип канала, и Crop на всю
                // ширину экрана растягивал/обрезал его до неузнаваемости
                // (видно на скриншотах — обрезанные по краям буквы логотипа).
                val context = LocalContext.current
                val density = LocalDensity.current
                val widthPx = with(density) { (LocalConfiguration.current.screenWidthDp.dp - ZenithDimens.paddingM * 2).roundToPx() }
                val heightPx = with(density) { 220.dp.roundToPx() }
                val hasRealBackdrop = !state.metadata?.backdropPath.isNullOrBlank()
                // Та же ступень, что и в TV DetailScreen.kt: перед тем как
                // падать на movie.poster (для Xtream/M3U — почти всегда
                // streamIcon, скриншот кадра от провайдера, не постер),
                // пробуем настоящий постер TMDB — он часто есть даже когда
                // backdrop_path пуст.
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
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    )
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                }
                Text(state.movie.title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                state.metadata?.let { m ->
                    // Год/длительность — PROMPT_DETAIL_SCREEN_UPGRADE.md, п.1,
                    // тот же приём, что в DetailScreen.kt (TV): пропускаем
                    // пустые части, duration у части источников — пустая
                    // строка по умолчанию.
                    val metaLine = listOfNotNull(
                        state.movie.year.takeIf { it > 0 }?.toString(),
                        state.movie.duration.takeIf { it.isNotBlank() },
                        m.genres?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (metaLine.isNotEmpty()) Text(metaLine, color = Color.Gray)
                    m.voteAverage?.let { Text("★ $it", color = ZenithWarning) }
                    m.overview?.let { Text(it, color = Color.White.copy(0.8f), modifier = Modifier.padding(top = ZenithDimens.paddingS)) }
                }
                Spacer(Modifier.height(ZenithDimens.paddingM))
                Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                    Button(onClick = { navController.navigate("player/$movieId") }, modifier = Modifier.weight(1f)) { Text(if (state.watchProgress != null) "Продолжить" else "Смотреть") }
                    // Добавление показывает выбор папки, снятие — сразу, без
                    // диалога. Отметка "аниме" убрана с этого экрана — теперь
                    // только в едином меню на карточке в FavoritesScreen
                    // (PROMPT_FAVORITES_REDESIGN.md, п.1/п.3).
                    OutlinedButton(onClick = {
                        if (state.isFavorite) viewModel.removeFavorite(movieId) else showAddFavoriteDialog = true
                    }) { Text(if (state.isFavorite) "♥" else "♡") }
                }
                // Карусель актёров (п.4) — как на TV, не рисуется, если пусто.
                if (state.metadata?.cast?.isNotEmpty() == true) {
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                    CastRow(state.metadata.cast)
                }
                // "Смотрите также" (п.5) — только когда TMDB нашёл совпадение.
                if (state.recommendations.isNotEmpty()) {
                    Spacer(Modifier.height(ZenithDimens.paddingM))
                    RecommendationsRow(state.recommendations)
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
