package com.platinum.ott.presentation.phone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.Movie
import kotlinx.coroutines.delay

/**
 * Аналог HeroBanner.kt (TV), не переиспользует его напрямую — тот завязан
 * на androidx.tv.material3.Surface, здесь обычный clickable Box (тот же
 * подход, что и в паре CatalogRow.kt/PhoneCatalogRow.kt). Ниже и без
 * overscan-отступа TV.
 */
@Composable
fun PhoneHeroBanner(
    movies: List<Movie>,
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    resolvedPosters: Map<String, String> = emptyMap(),
    onResolvePoster: (Movie, Int) -> Unit = { _, _ -> }
) {
    if (movies.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { movies.size })

    LaunchedEffect(pagerState.currentPage, movies.size) {
        if (movies.size <= 1) return@LaunchedEffect
        delay(6000)
        val next = (pagerState.currentPage + 1) % movies.size
        pagerState.animateScrollToPage(next)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth().height(200.dp).padding(horizontal = ZenithDimens.paddingM)
    ) { page ->
        val movie = movies[page]
        val context = LocalContext.current
        val density = LocalDensity.current
        val widthPx = remember(density) { with(density) { 800.dp.roundToPx() } }
        LaunchedEffect(movie.id) { onResolvePoster(movie, widthPx) }
        val request = remember(movie.poster, resolvedPosters[movie.id], widthPx) {
            ImageRequest.Builder(context).data(resolvedPosters[movie.id] ?: movie.poster).size(widthPx).crossfade(true).build()
        }
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable { onMovieClick(movie.id) }
        ) {
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
            Column(Modifier.align(Alignment.BottomStart).padding(ZenithDimens.paddingM)) {
                Text(movie.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (movie.year > 0) Text("${movie.year}", color = Color.Gray)
            }
        }
    }
}
