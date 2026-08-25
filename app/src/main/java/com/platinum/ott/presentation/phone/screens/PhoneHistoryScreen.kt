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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
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
import com.platinum.ott.core.platform.ZenithDimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHistoryScreen(navController: NavHostController) {

val viewModel: com.platinum.ott.presentation.screens.history.HistoryViewModel = hiltViewModel()
val history by viewModel.history.collectAsState(initial = emptyList())
var showClearAllConfirm by remember { mutableStateOf(false) }

Scaffold(
    topBar = {
        TopAppBar(
            title = { Text("История") },
            actions = {
                // Видима только когда есть что чистить — то же решение, что
                // на TV в HistoryScreen.kt.
                if (history.isNotEmpty()) {
                    IconButton(onClick = { showClearAllConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, "Очистить всю историю")
                    }
                }
            }
        )
    },
    bottomBar = { PhoneBottomBar(navController) }
) { padding ->
    if (history.isEmpty()) {
        // Тот же принцип, что уже сделан в SeriesListScreen.kt/
        // PhoneSeriesListScreen.kt для пустого списка сериалов.
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
            Text("Здесь появятся фильмы и сериалы, которые вы начали смотреть", color = Color.Gray, modifier = Modifier.padding(ZenithDimens.paddingM))
        }
    } else {
        LazyColumn(Modifier.padding(padding).background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
            items(history, key = { it.contentId }) { entry ->
                val p = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                // Удаление одной записи — свайп (SwipeToDismissBox из
                // Material3), как и предполагалось для телефона; долгое
                // нажатие с подтверждением сюда не добавляли — свайпа с
                // подложкой-подсказкой уже достаточно, не дублируем два
                // способа для одного действия.
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                            viewModel.delete(entry)
                            true
                        } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = ZenithDimens.paddingM),
                            contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
                        ) { Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                ) {
                    Card(
                        onClick = { navController.navigate("detail/${entry.contentId}") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Row(Modifier.padding(ZenithDimens.paddingSM)) {
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
                        Spacer(Modifier.width(ZenithDimens.paddingSM))
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
}

if (showClearAllConfirm) {
    AlertDialog(
        onDismissRequest = { showClearAllConfirm = false },
        title = { Text("Удалить всю историю?") },
        text = { Text("Это нельзя отменить.") },
        confirmButton = { TextButton(onClick = { viewModel.clearAll(); showClearAllConfirm = false }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Отмена") } }
    )
}
}
