package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.core.platform.ZenithDimens

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MovieCard(title = movie.title, poster = movie.poster, year = movie.year, onClick = onClick, modifier = modifier)
}

@Composable
fun MovieCard(title: String, poster: String, year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.width(ZenithDimens.cardWidth).height(ZenithDimens.cardHeight), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(model = poster, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(0.7f)).padding(8.dp)) {
                Column {
                    Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (year > 0) Text("$year", color = Color.Gray)
                }
            }
        }
    }
}
