package com.platinum.ott.presentation.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryScreen(onBackPressed: () -> Unit, onMovieClick: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
        Text("История просмотров", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(ZenithDimens.paddingM))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
            items(history, key = { it.contentId }) { entry ->
                val progress = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                Surface(onClick = { onMovieClick(entry.contentId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(ZenithDimens.paddingSM)) {
                        // Раньше здесь стоял MovieCard — тот же полноразмерный
                        // компонент, что и в сетке каталога (180×270dp по
                        // ZenithDimens на Expanded/TV). В сетке карточки такого
                        // размера ожидаемы, но в построчном списке истории
                        // рядом с текстом это выглядело как один гигантский
                        // постер на весь ряд. На телефоне для этого же экрана
                        // уже был отдельный маленький тумбнейл (56×84dp,
                        // PhoneHistoryScreen.kt) — здесь просто применяем тот
                        // же приём, увеличенный под дистанцию просмотра TV.
                        val context = LocalContext.current
                        val density = LocalDensity.current
                        val widthPx = with(density) { 96.dp.roundToPx() }
                        val heightPx = with(density) { 144.dp.roundToPx() }
                        val request = remember(entry.poster, widthPx, heightPx) {
                            ImageRequest.Builder(context).data(entry.poster).size(widthPx, heightPx).crossfade(true).build()
                        }
                        AsyncImage(
                            model = request,
                            contentDescription = entry.title,
                            contentScale = ContentScale.Fit,
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.width(96.dp).height(144.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)
                        )
                        Spacer(Modifier.width(ZenithDimens.paddingSM))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(if (entry.completed) "Просмотрено" else "Прогресс: ${(progress * 100).toInt()}%", color = Color.Gray)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXS))
                        }
                    }
                }
            }
        }
    }
}
