package com.platinum.ott.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.*
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text     = title,
            style    = MaterialTheme.typography.titleLarge,
            color    = Color.White,
            modifier = Modifier.padding(start = 56.dp, bottom = 12.dp)
        )

        LazyRow(
            contentPadding         = PaddingValues(horizontal = 56.dp),
            horizontalArrangement  = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = movies,
                key   = { it.id }   // стабильные ключи для Compose-рекомпозиции
            ) { movie ->
                MovieCard(
                    title    = movie.title,
                    year     = movie.year,
                    poster = movie.poster,
                    onClick  = { onMovieClick(movie.id) }
                )
            }
        }
    }
}
