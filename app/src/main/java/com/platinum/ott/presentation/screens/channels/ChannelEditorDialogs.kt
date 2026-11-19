package com.platinum.ott.presentation.screens.channels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.data.repository.ChannelNameMatcher
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.ui.theme.ZenithError
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithShapeLarge
import com.platinum.ott.ui.theme.ZenithShapeMedium
import com.platinum.ott.ui.theme.ZenithSurface

/**
 * Окна ТВ-экрана «Каналы» (меню действий, редактор канала и категории, выбор категории, поиск похожего канала,
 * ввод текста). Все собраны на одной основе [TvModal]: крупный текст, толстая рамка у фокуса, ячейки лежат
 * в сетке с адаптивной шириной (GridCells.Adaptive) — на узком окне одна колонка, на широком несколько.
 */

/** Раскладка экрана «Каналы» на ТВ. */
internal enum class ChannelsLayout(val label: String, val hint: String) {
    THREE("2 окна: категории и каналы + сведения", "слева категории и каналы, справа сведения"),
    CATS_CHANNELS("2 окна: категории + каналы", "сведения о передаче — в плитке канала"),
    CHANNELS_DETAILS("2 окна: каналы + сведения", "категории — полоской сверху");

    companion object {
        fun from(name: String?): ChannelsLayout = entries.firstOrNull { it.name == name } ?: THREE
    }
}

// ---------------------------------------------------------------------------------------------
// Основа
// ---------------------------------------------------------------------------------------------

/**
 * Модальное окно для пульта. [content] наполняет сетку; ему передаётся [FocusRequester] «первого»
 * элемента — вешайте его на ячейку, где должен оказаться фокус при открытии.
 */
@Composable
internal fun TvModal(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    minCell: Dp = 220.dp,
    header: (@Composable () -> Unit)? = null,
    content: LazyGridScope.(first: FocusRequester) -> Unit
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 780.dp)
                .heightIn(max = 480.dp)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface)
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.12f), ZenithShapeLarge)
                .padding(ZenithDimens.paddingL)
        ) {
            if (header != null) {
                header()
            } else {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = ZenithOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurfaceVariant)
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minCell),
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) { content(first) }
        }
    }
    LaunchedEffect(Unit) {
        // Окно создаёт содержимое не мгновенно: ждём пару кадров, потом ставим фокус.
        repeat(2) { withFrameNanos { } }
        first.safeRequestFocus()
    }
}

/** Крупная кнопка-ячейка: иконка, подпись, необязательная подсказка; [selected] — отметка «выбрано». */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvActionCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hint: String? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    val accent = if (danger) ZenithError else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
        shape = ClickableSurfaceDefaults.shape(ZenithShapeMedium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.16f) else ZenithOnSurface.copy(alpha = 0.08f),
            contentColor = ZenithOnSurface,
            focusedContainerColor = accent.copy(alpha = 0.30f),
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.42f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, accent))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (danger) accent else ZenithOnSurface, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 18.sp, color = ZenithOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!hint.isNullOrBlank()) {
                    Text(hint, fontSize = 13.sp, color = ZenithOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Check, contentDescription = "Выбрано", tint = accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Заголовок раздела внутри сетки окна — на всю ширину. */
@OptIn(ExperimentalTvMaterial3Api::class)
internal fun LazyGridScope.sectionTitle(text: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
            color = ZenithOnSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Меню действий («Меню» в шапке экрана)
// ---------------------------------------------------------------------------------------------

/** Одно окно вместо россыпи кнопок в шапке: проверка, программа, правка, источники, раскладка. */
@Composable
internal fun ChannelsMenuDialog(
    editMode: Boolean,
    isChecking: Boolean,
    epgBusy: Boolean,
    layout: ChannelsLayout,
    onCheckAll: () -> Unit,
    onRefreshEpg: () -> Unit,
    onToggleEdit: () -> Unit,
    onOpenSources: () -> Unit,
    onLayout: (ChannelsLayout) -> Unit,
    onDismiss: () -> Unit
) {
    TvModal(title = "Меню каналов", onDismiss = onDismiss, minCell = 320.dp) { first ->
        sectionTitle("Действия")
        item {
            TvActionCell(
                if (isChecking) "Проверка источников…" else "Проверить все источники",
                onClick = { onCheckAll(); onDismiss() },
                modifier = Modifier.focusRequester(first),
                icon = Icons.Default.Refresh,
                hint = "Какие каналы сейчас отвечают",
                enabled = !isChecking
            )
        }
        item {
            TvActionCell(
                if (epgBusy) "Программа загружается…" else "Обновить программу",
                onClick = { onRefreshEpg(); onDismiss() },
                icon = Icons.Default.Schedule,
                hint = "Телепрограмма для всех каналов",
                enabled = !epgBusy
            )
        }
        item {
            TvActionCell(
                if (editMode) "Закончить правку" else "Править каналы",
                onClick = { onToggleEdit(); onDismiss() },
                icon = Icons.Default.Edit,
                hint = if (editMode) "Скрытые каналы снова спрячутся" else "Показать скрытые каналы и категории",
                selected = editMode
            )
        }
        item {
            TvActionCell(
                "Источники",
                onClick = { onOpenSources(); onDismiss() },
                icon = Icons.Default.Source,
                hint = "Плейлисты и панели"
            )
        }
        sectionTitle("Раскладка")
        ChannelsLayout.entries.forEach { mode ->
            item {
                TvActionCell(
                    mode.label,
                    onClick = { onLayout(mode); onDismiss() },
                    icon = Icons.Default.Tv,
                    hint = mode.hint,
                    selected = mode == layout
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TvActionCell("Закрыть", onClick = onDismiss, icon = Icons.Default.Close)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Редактор канала (долгое OK на канале)
// ---------------------------------------------------------------------------------------------

private fun healthText(status: String) = when (status) {
    "alive" -> "работает"
    "dead" -> "не отвечает"
    else -> "не проверялся"
}

/**
 * Полный редактор канала. Кнопки перемещения окно не закрывают: можно нажимать «Выше» несколько раз подряд.
 * [position]/[total] — место канала в списке, который сейчас на экране (0 — не найден).
 */
@Composable
internal fun ChannelEditorDialog(
    channel: ChannelUiItem,
    position: Int,
    total: Int,
    isFavorite: Boolean,
    isChecking: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    onCheck: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onToPosition: () -> Unit,
    onSetNumber: () -> Unit,
    onCategory: () -> Unit,
    onSimilar: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val canUp = position > 1
    val canDown = position in 1 until total
    TvModal(
        title = channel.canonicalName,
        onDismiss = onDismiss,
        minCell = 230.dp,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(ZenithShapeMedium).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) {
                    if (channel.logo != null) {
                        AsyncImage(model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(52.dp))
                    } else {
                        Text(channel.canonicalName.take(2).uppercase(), color = ZenithOnSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.width(ZenithDimens.paddingM))
                Column(Modifier.weight(1f)) {
                    Text(channel.canonicalName, style = MaterialTheme.typography.headlineSmall, color = ZenithOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val category = channel.category?.trim()?.ifBlank { null } ?: "Без категории"
                    Text(
                        "№${channel.channelNumber} · $category" + if (position > 0) " · позиция $position из $total" else "",
                        style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Источников: ${channel.streamCount} · ${healthText(channel.healthStatus)}" +
                            (channel.regionHint?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = ZenithOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    ) { first ->
        sectionTitle("Канал")
        item {
            TvActionCell(
                if (isFavorite) "Убрать из избранного" else "В избранное",
                onClick = onToggleFavorite,
                modifier = Modifier.focusRequester(first),
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                selected = isFavorite
            )
        }
        item {
            TvActionCell(
                if (channel.isHidden) "Показывать канал" else "Скрыть канал",
                onClick = onToggleHidden,
                icon = if (channel.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff
            )
        }
        item { TvActionCell("Название и регион", onClick = onRename, icon = Icons.Default.Edit, hint = "Переименовать, пометить регион") }
        item {
            TvActionCell(
                if (isChecking) "Проверка…" else "Проверить сейчас",
                onClick = onCheck, icon = Icons.Default.Refresh, enabled = !isChecking
            )
        }

        sectionTitle("Перемещение")
        item { TvActionCell("Выше на 1", onClick = onUp, icon = Icons.Default.KeyboardArrowUp, enabled = canUp) }
        item { TvActionCell("Ниже на 1", onClick = onDown, icon = Icons.Default.KeyboardArrowDown, enabled = canDown) }
        item { TvActionCell("В начало списка", onClick = onTop, icon = Icons.Default.VerticalAlignTop, enabled = canUp) }
        item { TvActionCell("В конец списка", onClick = onBottom, icon = Icons.Default.VerticalAlignBottom, enabled = canDown) }
        item { TvActionCell("На позицию…", onClick = onToPosition, icon = Icons.Default.SwapVert, hint = "Ввести место в списке", enabled = total > 1) }
        item { TvActionCell("Номер канала…", onClick = onSetNumber, icon = Icons.Default.SwapVert, hint = "Тот, что набирают на пульте") }

        sectionTitle("Категория и объединение")
        item { TvActionCell("Другая категория…", onClick = onCategory, icon = Icons.Default.CreateNewFolder, hint = "Выбрать или создать") }
        item {
            TvActionCell(
                "Объединить с похожим…", onClick = onSimilar, icon = Icons.Default.MergeType,
                hint = "Тот же канал под другим написанием"
            )
        }

        sectionTitle("Опасная зона")
        item { TvActionCell("Удалить канал", onClick = onDelete, icon = Icons.Default.Delete, danger = true) }
        item(span = { GridItemSpan(maxLineSpan) }) { TvActionCell("Готово", onClick = onDismiss, icon = Icons.Default.Check) }
    }
}

// ---------------------------------------------------------------------------------------------
// Редактор категории (долгое OK на категории)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun CategoryEditorDialog(
    name: String,
    channelCount: Int,
    isHidden: Boolean,
    position: Int,
    total: Int,
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onDismiss: () -> Unit
) {
    val canUp = position > 1
    val canDown = position in 1 until total
    TvModal(
        title = name,
        subtitle = "Каналов: $channelCount" + if (position > 0) " · позиция $position из $total" else "",
        onDismiss = onDismiss,
        minCell = 230.dp
    ) { first ->
        sectionTitle("Категория")
        item {
            TvActionCell(
                if (isHidden) "Показать категорию" else "Скрыть категорию",
                onClick = onToggleHidden,
                modifier = Modifier.focusRequester(first),
                icon = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                hint = if (isHidden) "Каналы в ней снова видны" else "Скрыты и все будущие каналы в ней"
            )
        }
        item { TvActionCell("Переименовать…", onClick = onRename, icon = Icons.Default.Edit, hint = "Если имя уже есть — категории сольются") }
        sectionTitle("Порядок категорий")
        item { TvActionCell("Выше на 1", onClick = onUp, icon = Icons.Default.KeyboardArrowUp, enabled = canUp) }
        item { TvActionCell("Ниже на 1", onClick = onDown, icon = Icons.Default.KeyboardArrowDown, enabled = canDown) }
        item { TvActionCell("В начало", onClick = onTop, icon = Icons.Default.VerticalAlignTop, enabled = canUp) }
        item { TvActionCell("В конец", onClick = onBottom, icon = Icons.Default.VerticalAlignBottom, enabled = canDown) }
        item(span = { GridItemSpan(maxLineSpan) }) { TvActionCell("Готово", onClick = onDismiss, icon = Icons.Default.Check) }
    }
}

// ---------------------------------------------------------------------------------------------
// Выбор категории
// ---------------------------------------------------------------------------------------------

/** [categories] — существующие имена; [current] — категория канала сейчас; null в [onPick] — «Без категории». */
@Composable
internal fun CategoryPickerDialog(
    channelName: String,
    categories: List<String>,
    current: String,
    noCategoryName: String,
    onPick: (String?) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    TvModal(title = "Категория канала", subtitle = "«$channelName» — выберите, куда перенести", onDismiss = onDismiss, minCell = 220.dp) { first ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            TvActionCell("Новая категория…", onClick = onNew, modifier = Modifier.focusRequester(first), icon = Icons.Default.CreateNewFolder)
        }
        categories.forEach { cat ->
            item {
                TvActionCell(
                    cat,
                    onClick = { onPick(if (cat == noCategoryName) null else cat) },
                    selected = cat == current
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { TvActionCell("Отмена", onClick = onDismiss, icon = Icons.Default.Close) }
    }
}

// ---------------------------------------------------------------------------------------------
// Ввод текста / числа
// ---------------------------------------------------------------------------------------------

/**
 * Ввод строки или числа. Поле — то же, что поиск на экране «Каналы»: клавиатура открывается по OK,
 * а не сама. При [numeric] остаются только цифры.
 */
@Composable
internal fun TvTextInputDialog(
    title: String,
    subtitle: String?,
    hint: String,
    initial: String,
    numeric: Boolean,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    TvModal(title = title, subtitle = subtitle, onDismiss = onDismiss, minCell = 300.dp) { first ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().focusRequester(first).focusGroup()) {
                ChannelSearchField(
                    query = text,
                    onChange = { text = if (numeric) it.filter { c -> c.isDigit() } else it },
                    modifier = Modifier.fillMaxWidth(),
                    hint = hint
                )
            }
        }
        item { TvActionCell(confirmLabel, onClick = { onConfirm(text) }, icon = Icons.Default.Check, enabled = text.isNotBlank()) }
        item { TvActionCell("Отмена", onClick = onDismiss, icon = Icons.Default.Close) }
    }
}

// ---------------------------------------------------------------------------------------------
// Поиск похожего канала для объединения
// ---------------------------------------------------------------------------------------------

/**
 * Похожие по названию каналы: «СТС» находит «STS», «Первый канал» — «Pervyj Kanal», «Матч ТВ» — «Match TV».
 * Пока строка поиска пуста — каналы, похожие на выбранный, по убыванию похожести; с текстом — подходящие под него
 * (латиницей или кириллицей, всё равно). Выбор строки сразу объединяет: ссылки выбранного канала переходят
 * к тому, что выбрали, а сам он исчезает из списка.
 */
@Composable
internal fun SimilarChannelsDialog(
    source: ChannelUiItem,
    candidates: List<ChannelUiItem>,
    onSelect: (ChannelUiItem) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val ranked = remember(query, candidates, source.id) {
        val strict = ChannelNameMatcher.rank(source.canonicalName, candidates, { it.canonicalName }, query)
        // Ничего достаточно похожего — показываем хотя бы самые близкие, чтобы список не был пустым.
        if (strict.isEmpty() && query.isBlank()) {
            ChannelNameMatcher.rank(source.canonicalName, candidates, { it.canonicalName }, query, minScore = 0.3, limit = 15)
        } else strict
    }
    TvModal(
        title = "Объединить «${source.canonicalName}»",
        subtitle = "Выберите канал, к которому перейдут его ссылки. Выбранный здесь останется, «${source.canonicalName}» исчезнет.",
        onDismiss = onDismiss,
        minCell = 600.dp
    ) { first ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            ChannelSearchField(
                query = query,
                onChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                hint = "Название — можно латиницей или кириллицей"
            )
        }
        if (ranked.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (query.isBlank()) "Похожих названий нет — введите часть названия выше" else "Ничего не найдено",
                    color = ZenithOnSurfaceVariant, modifier = Modifier.padding(vertical = ZenithDimens.paddingS)
                )
            }
        }
        ranked.forEachIndexed { index, scored ->
            item(key = scored.item.id) {
                val ch = scored.item
                val label = when {
                    query.isNotBlank() -> null
                    scored.score >= 0.9 -> "очень похож"
                    scored.score >= 0.6 -> "похож"
                    else -> "возможно"
                }
                TvActionCell(
                    ch.canonicalName,
                    onClick = { onSelect(ch) },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    icon = Icons.Default.MergeType,
                    hint = listOfNotNull(
                        ch.category?.trim()?.ifBlank { null },
                        label,
                        "№${ch.channelNumber}"
                    ).joinToString(" · ")
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TvActionCell(
                "Отмена", onClick = onDismiss, icon = Icons.Default.Close,
                modifier = if (ranked.isEmpty()) Modifier.focusRequester(first) else Modifier
            )
        }
    }
}
