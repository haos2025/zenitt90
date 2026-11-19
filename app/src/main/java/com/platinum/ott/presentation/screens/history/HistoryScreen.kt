package com.platinum.ott.presentation.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
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
 *
 * Режим «Список» компактный: миниатюра 60 dp, отступ 6 dp, строки лежат в адаптивной сетке (GridCells.Adaptive),
 * поэтому на экране 960×540 dp шесть записей помещаются в две колонки. Подпись — сколько осталось времени
 * (для сериалов ещё сезон, серия и сколько серий дальше), без процентов. Над списком — фильтр по типу
 * («Все / Фильмы / Сериалы / Каналы»): он появляется сам, когда в истории есть записи больше одного типа.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavHostController, onMovieClick: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState(initial = emptyList())
    val loaded by viewModel.items.collectAsState(initial = emptyList())
    // Пока подписи с данными о сериях считаются, показываем записи с простой подписью — без мигания пустого экрана.
    val items = remember(history, loaded) {
        when {
            history.isEmpty() -> emptyList()
            loaded.isEmpty() -> history.map { HistoryItem(it, historyKindOf(it), historySubtitle(it)) }
            else -> loaded
        }
    }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<WatchHistoryEntity?>(null) }

    // Фильтр по типу: только те типы, что реально есть в истории; если выбранный тип исчез — «Все».
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    val kinds = HistoryKind.entries.filter { kind -> items.any { it.kind == kind } }
    val activeKind = HistoryKind.entries.firstOrNull { it.name == filterName }?.takeIf { it in kinds }
    val shown = if (activeKind == null) items else items.filter { it.kind == activeKind }

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

            if (kinds.size >= 2) {
                Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                    HistoryFilterChip("Все ${items.size}", activeKind == null) { filterName = null }
                    kinds.forEach { kind ->
                        HistoryFilterChip("${kind.label} ${items.count { it.kind == kind }}", activeKind == kind) { filterName = kind.name }
                    }
                }
                Spacer(Modifier.height(ZenithDimens.paddingM))
            }

            if (history.isEmpty()) {
                Text("Здесь появятся фильмы и сериалы, которые вы начали смотреть", color = ZenithOnSurfaceVariant)
            } else {
                CardCollection(
                    items = shown,
                    key = { it.entry.contentId },
                    section = CardSection.HISTORY,
                    isTv = true,
                    modifier = Modifier.focusRequester(listFocus).focusGroup(),
                    // Строки «Списка» не уже 340 dp: на широком экране — несколько колонок.
                    listMinWidth = 340.dp,
                    cardItem = { item, _, wide ->
                        val entry = item.entry
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
                    listItem = { item, _ ->
                        val entry = item.entry
                        val p = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                        PosterListRow(
                            title = entry.title,
                            poster = entry.poster ?: "",
                            subtitle = item.subtitle,
                            progress = if (entry.completed) 1f else p,
                            thumbWidth = 60.dp,
                            contentPadding = 6.dp,
                            onClick = { onMovieClick(entry.contentId) },
                            trailing = {
                                // Отдельная кнопка рядом со строкой, а не внутри её onClick (два кликабельных в одном — ненадёжно на пульте).
                                TvPillButton(
                                    Icons.Default.Delete, { pendingDelete = entry },
                                    Modifier.padding(start = ZenithDimens.paddingXS),
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

/** Плашка фильтра: у фокуса акцентная рамка, выбранная — с лёгкой заливкой (тот же вид, что у кнопок рядом). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HistoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
            contentColor = ZenithOnSurface,
            focusedContainerColor = accent.copy(alpha = 0.34f),
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.46f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.5.dp, accent.copy(alpha = 0.7f))) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, accent))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
