package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.core.platform.ZenithDimens

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MovieCard(title = movie.title, poster = movie.poster, year = movie.year, onClick = onClick, modifier = modifier)
}

@Composable
fun MovieCard(title: String, poster: String, year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cardWidth = ZenithDimens.cardWidth
    val cardHeight = ZenithDimens.cardHeight
    val context = LocalContext.current
    val density = LocalDensity.current

    // Переводим dp карточки в физические пиксели под плотность ЭТОГО экрана
    // (телефон, планшет, TV на 1080p/4K — везде своя плотность) и просим у Coil
    // именно такой размер, а не полагаемся только на неявный resolver.
    // Coil сам подберёт ближайший подходящий downsample при декодировании —
    // важно для сеточных списков на TV, где одновременно на экране десятки карточек.
    val widthPx = with(density) { cardWidth.roundToPx() }
    val heightPx = with(density) { cardHeight.roundToPx() }

    val request = remember(poster, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(poster)
            .size(widthPx, heightPx)
            .crossfade(true)
            .build()
    }

    Card(onClick = onClick, modifier = modifier.width(cardWidth).height(cardHeight), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = ColorPainter(Color(0xFF1C1C1C)),
                error = ColorPainter(Color(0xFF2A2A2A))
            )
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(0.7f)).padding(8.dp)) {
                Column {
                    Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (year > 0) Text("$year", color = Color.Gray)
                }
            }
        }
    }
}
