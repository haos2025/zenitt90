package com.platinum.ott.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.Movie

/**
 * Горизонтальный ряд каталога с заголовком секции.
 * LazyRow поддерживает D-pad навигацию и фокус из коробки.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CatalogRow(
    title: String,
    movies: List<Movie>,
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    // ROADMAP.md п.11: TMDB-постеры в сетке каталога. Раньше карточки везде
    // показывали movie.poster напрямую (для backend-контента это чаще
    // скриншот кадра, не постер) — resolvedPosters/onResolvePoster
    // приходят от HomeViewModel (см. HomeScreen.kt), дефолты пустые, чтобы
    // CatalogRow не ломался для мест, где его вызывают без этой логики.
    resolvedPosters: Map<String, String> = emptyMap(),
    onResolvePoster: (Movie, Int) -> Unit = { _, _ -> }
) {
    val density = LocalDensity.current
    // ZenithDimens.cardWidth — @Composable get() (адаптивен по WindowSize,
    // см. Platform.kt), его нельзя читать внутри лямбды remember{} (та не
    // @Composable-контекст, "invocations can only happen from..." на CI) —
    // читаем здесь, в теле composable-функции, а не внутри remember.
    val cardWidth = ZenithDimens.cardWidth
    val posterWidthPx = remember(density, cardWidth) { with(density) { cardWidth.roundToPx() } }

    Column(modifier = modifier) {
        Text(
            text     = title,
            style    = MaterialTheme.typography.titleLarge,
            color    = Color.White,
            modifier = Modifier.padding(start = 56.dp, bottom = 12.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding         = PaddingValues(horizontal = 56.dp),
            horizontalArrangement  = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = movies,
                key   = { it.id }   // стабильные ключи для Compose-рекомпозиции
            ) { movie ->
                // Срабатывает, когда карточка КОМПОЗИРУЕТСЯ — LazyRow композирует
                // только элементы рядом с видимой областью, так что это и есть
                // "запрос под видимые карточки" без отдельного viewport-трекинга.
                LaunchedEffect(movie.id) { onResolvePoster(movie, posterWidthPx) }
                MovieCard(
                    title    = movie.title,
                    year     = movie.year,
                    poster = resolvedPosters[movie.id] ?: movie.poster,
                    onClick  = { onMovieClick(movie.id) }
                )
            }
        }
    }
}
