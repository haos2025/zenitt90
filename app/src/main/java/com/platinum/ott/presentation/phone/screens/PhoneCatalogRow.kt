package com.platinum.ott.presentation.phone.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
fun PhoneCatalogRow(title: String, movies: List<Movie>, onMovieClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(movies, key = { it.id }) { movie ->
                MovieCard(movie = movie, onClick = { onMovieClick(movie.id) })
            }
        }
    }
}
