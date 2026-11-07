package com.platinum.ott.presentation.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.data.local.entity.WatchHistoryEntity
import com.platinum.ott.presentation.components.CardCollection
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.components.NavSidebar
import com.platinum.ott.presentation.components.PosterListRow
import com.platinum.ott.presentation.components.TvConfirmDialog
import com.platinum.ott.presentation.components.TvPillButton
import com.platinum.ott.presentation.components.contentScaleForId
import com.platinum.ott.ui.theme.CardSection
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant

/**
 * История на ТВ. Строка списка и кнопки приведены к тому же виду, что в «Избранном»: у фокуса
 * акцентная рамка и мягкая подсветка вместо белой заливки стандартного Surface. Удаление любой
 * записи и очистка всей истории — через подтверждение (по умолчанию фокус на «Отмена»).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavHostController, onMovieClick: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState(initial = emptyList())
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WatchHistoryEntity?>(null) }

    // После удаления записи элемент с фокусом исчезает, и фокус пульта терялся: возвращаем его в список.
    val listFocus = remember { FocusRequester() }
    var lastSize by remember { mutableStateOf(history.size) }
    LaunchedEffect(history.size) {
        if (history.size < lastSize && history.isNotEmpty()) {
            repeat(2) { androidx.compose.runtime.withFrameNanos { } }
            listFocus.safeRequestFocus()
        }
        lastSize = history.size
    }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavSidebar(navController)
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("История просмотров", style = MaterialTheme.typography.displaySmall, color = ZenithOnSurface)
                if (history.isNotEmpty()) {
                    Spacer(Modifier.width(ZenithDimens.paddingSM))
                    Text("${history.size}", style = MaterialTheme.typography.titleLarge, color = ZenithOnSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    TvPillButton(Icons.Default.DeleteSweep, { showClearAllConfirm = true }, label = "Очистить всю историю", danger = true)
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            if (history.isEmpty()) {
                Text("Здесь появятся фильмы и сериалы, которые вы начали смотреть", color = ZenithOnSurfaceVariant)
            } else {
                CardCollection(
                    items = history,
                    key = { it.contentId },
                    section = CardSection.HISTORY,
                    isTv = true,
                    modifier = Modifier.focusRequester(listFocus).focusGroup(),
                    cardItem = { entry, _, wide ->
                        val p = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MovieCard(
                                title = entry.title, poster = entry.poster ?: "", year = 0,
                                onClick = { onMovieClick(entry.contentId) },
                                progress = if (entry.completed) 1f else p,
                                contentScale = contentScaleForId(entry.contentId),
                                wide = wide
                            )
                            // Удаление — отдельная фокусируемая кнопка под карточкой (долгое нажатие на пульте ненадёжно).
                            TvPillButton(
                                Icons.Default.Delete, { pendingDelete = entry },
                                Modifier.padding(top = ZenithDimens.paddingXS),
                                label = "Удалить", contentDescription = "Удалить «${entry.title}» из истории", danger = true
                            )
                        }
                    },
                    listItem = { entry, _ ->
                        val p = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                        PosterListRow(
                            title = entry.title,
                            poster = entry.poster ?: "",
                            subtitle = historySubtitle(entry),
                            progress = if (entry.completed) 1f else p,
                            thumbWidth = 96.dp,
                            onClick = { onMovieClick(entry.contentId) },
                            trailing = {
                                // Отдельная кнопка рядом со строкой, а не внутри её onClick (два кликабельных в одном — ненадёжно на пульте).
                                TvPillButton(
                                    Icons.Default.Delete, { pendingDelete = entry },
                                    Modifier.padding(start = ZenithDimens.paddingS),
                                    contentDescription = "Удалить «${entry.title}» из истории", danger = true
                                )
                            }
                        )
                    }
                )
            }
        }
    }

    pendingDelete?.let { entry ->
        TvConfirmDialog(
            title = "Удалить из истории?",
            text = "«${entry.title}» пропадёт из истории просмотров. Это нельзя отменить.",
            confirmLabel = "Удалить",
            onConfirm = { viewModel.delete(entry); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
    if (showClearAllConfirm) {
        TvConfirmDialog(
            title = "Удалить всю историю?",
            text = "Все записи (${history.size}) будут удалены. Это нельзя отменить.",
            confirmLabel = "Удалить всё",
            onConfirm = { viewModel.clearAll(); showClearAllConfirm = false },
            onDismiss = { showClearAllConfirm = false }
        )
    }
}
