package com.platinum.ott.presentation.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
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
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.platinum.ott.presentation.components.NavSidebar

// PROMPT_NAVIGATION_SIDEBAR.md — постоянный сайдбар вместо возврата только
// на Home. onBackPressed раньше принимался параметром, но нигде в теле не
// вызывался — убран вместе с добавлением navController.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavHostController, onMovieClick: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState(initial = emptyList())
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavSidebar(navController)
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("История просмотров", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
                // Кнопка видима всегда (даже на пустой истории её проще спрятать
                // за history.isNotEmpty(), но пульту TV нужна предсказуемая
                // раскладка фокуса — на пустом списке она просто не нужна,
                // прячем только в этом случае).
                if (history.isNotEmpty()) {
                    OutlinedButton(onClick = { showClearAllConfirm = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(ZenithDimens.paddingXS))
                        Text("Очистить всю историю")
                    }
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            if (history.isEmpty()) {
                // Тот же принцип пустого состояния, что уже сделан в
                // SeriesListScreen.kt для пустого списка сериалов — обычный
                // серый Text, без отдельного компонента.
                Text("Здесь появятся фильмы и сериалы, которые вы начали смотреть", color = Color.Gray)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                    items(history, key = { it.contentId }) { entry ->
                        val progress = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                        // Кнопка удаления — отдельный фокусируемый элемент рядом с
                        // Surface, не внутри его onClick. Тот же принцип, что уже
                        // применялся в FavoritesScreen.kt: "📁 В папку" там —
                        // самостоятельная кнопка под MovieCard, а не что-то, вложенное
                        // в кликабельную карточку (два кликабельных на одном пульте —
                        // источник как раз того рода ненадёжности, из-за которой в
                        // истории вообще не годится долгое нажатие).
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(onClick = { onMovieClick(entry.contentId) }, modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(ZenithDimens.paddingSM), verticalAlignment = Alignment.CenterVertically) {
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
                        // Удаление одной записи — на TV долгое нажатие ненадёжно
                        // (зависит от пульта), поэтому видимая кнопка на самой
                        // строке, не спрятанная в меню.
                        IconButton(onClick = { viewModel.delete(entry) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить «${entry.title}» из истории")
                        }
                        }
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
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearAllConfirm = false }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Отмена") }
            }
        )
    }
}
