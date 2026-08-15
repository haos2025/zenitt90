package com.platinum.ott.presentation.phone.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.presentation.components.MovieCard

/**
 * Аналог CatalogRow.kt (TV), но не переиспользует его напрямую — тот
 * завязан на androidx.tv.material3 и TV-отступы (padding 56dp — почти
 * половина ширины экрана телефона, не подходит). Карточка внутри —
 * та же самая MovieCard, у которой cardWidth уже адаптивен по WindowSize
 * (ZenithDimens/Platform.kt) — здесь это работает правильно, потому что
 * LazyRow не делит ширину на равные ячейки как LazyVerticalGrid, каждая
 * карточка сохраняет свою декларативную ширину и ряд просто скроллится.
 */
@Composable
fun PhoneCatalogRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    // ROADMAP.md п.11 — то же самое, что и в CatalogRow.kt (TV): резолв
    // TMDB-постера приходит от HomeViewModel, дефолты пустые для мест, где
    // PhoneCatalogRow используется без этой логики.
    resolvedPosters: Map<String, String> = emptyMap(),
    onResolvePoster: (Movie, Int) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    val posterWidthPx = remember(density) { with(density) { ZenithDimens.cardWidth.roundToPx() } }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ZenithDimens.paddingM),
            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
        ) {
            items(movies, key = { it.id }) { movie ->
                LaunchedEffect(movie.id) { onResolvePoster(movie, posterWidthPx) }
                MovieCard(
                    title = movie.title, year = movie.year,
                    poster = resolvedPosters[movie.id] ?: movie.poster,
                    onClick = { onMovieClick(movie.id) }
                )
            }
        }
    }
}
