package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.Movie
import kotlinx.coroutines.delay

/**
 * Hero-баннер (решение 1 из PROMPT_HOME_FEED_REDESIGN.md) — ротация карточек
 * НЕДАВНО ДОБАВЛЕННОГО контента (movies приходит от HomeViewModel как первые
 * N фильмов первой страницы backend-каталога — тот уже отдаёт свежее
 * первым, отдельного поля "дата добавления" на Movie нет и не заводится
 * ради этого). Не ряд по рейтингу — TMDB-рейтинг есть не у всего каталога,
 * особенно у собственного плейлиста (см. обоснование в самом промте).
 *
 * movies приходят только из backend-каталога (никогда из плейлиста —
 * см. HomeViewModel.loadCatalog()), поэтому ContentScale.Crop здесь
 * безопасен: баг с обрезкой текста у логотипов M3U/Xtream-каналов
 * (см. комментарий в MovieCard.kt) на эти карточки не распространяется.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroBanner(
    movies: List<Movie>,
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    resolvedPosters: Map<String, String> = emptyMap(),
    onResolvePoster: (Movie, Int) -> Unit = { _, _ -> }
) {
    if (movies.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { movies.size })

    // Автопрокрутка — эффект живёт, пока жив composable, перезапускается на
    // каждую смену страницы (в том числе ручную) так, чтобы отсчёт паузы
    // начинался заново. Пауза 6с — достаточно, чтобы прочитать название, не
    // настолько долго, чтобы ротация ощущалась статичной.
    LaunchedEffect(pagerState.currentPage, movies.size) {
        if (movies.size <= 1) return@LaunchedEffect
        delay(6000)
        val next = (pagerState.currentPage + 1) % movies.size
        pagerState.animateScrollToPage(next)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth().height(340.dp)
    ) { page ->
        val movie = movies[page]
        val context = LocalContext.current
        val density = LocalDensity.current
        val widthPx = remember(density) { with(density) { 1200.dp.roundToPx() } }
        LaunchedEffect(movie.id) { onResolvePoster(movie, widthPx) }
        val request = remember(movie.poster, resolvedPosters[movie.id], widthPx) {
            ImageRequest.Builder(context).data(resolvedPosters[movie.id] ?: movie.poster).size(widthPx).crossfade(true).build()
        }
        Surface(
            onClick = { onMovieClick(movie.id) },
            modifier = Modifier.fillMaxSize().padding(horizontal = ZenithDimens.tvOverscanPadding)
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = request,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    Text(movie.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (movie.year > 0) Text("${movie.year}", color = Color.Gray)
                }
            }
        }
    }
}
