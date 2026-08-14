package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.navigation.navigateToTab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHistoryScreen(navController: NavHostController) {

val viewModel: com.platinum.ott.presentation.screens.history.HistoryViewModel = hiltViewModel()
val history by viewModel.history.collectAsState(initial = emptyList())
Scaffold(bottomBar = { PhoneBottomBar(navController) }) { padding ->
    LazyColumn(Modifier.padding(padding).background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(history, key = { it.contentId }) { entry ->
            val p = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
            Card(
                onClick = { navController.navigate("detail/${entry.contentId}") },
                modifier = Modifier.fillMaxWidth()
            ) { Row(Modifier.padding(12.dp)) {
                // Раньше здесь не было картинки вообще — только текст, хотя
                // entry.poster уже приходит из WatchHistoryEntity. На TV этот
                // же список рендерится через MovieCard и постер есть, здесь
                // просто забыли — визуальный разнобой между платформами.
                val context = LocalContext.current
                val density = LocalDensity.current
                val widthPx = with(density) { 56.dp.roundToPx() }
                val heightPx = with(density) { 84.dp.roundToPx() }
                val request = remember(entry.poster, widthPx, heightPx) {
                    ImageRequest.Builder(context).data(entry.poster).size(widthPx, heightPx).crossfade(true).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Fit,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surface),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.width(56.dp).height(84.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                Text(entry.title, color = Color.White)
                // Раньше здесь был только прогресс-бар без текста — на ТВ
                // процент показывается, на телефоне не было вообще.
                Text(
                    if (entry.completed) "Просмотрено" else "Прогресс: ${(p * 100).toInt()}%",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator({ p })
            } } }
        }
    }
}
}
