package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelCategoryFilter
import com.platinum.ott.data.repository.ChannelNameMatcher
import com.platinum.ott.data.repository.ChannelUiItem

private fun healthText(status: String) = when (status) {
    "alive" -> "работает"
    "dead" -> "не отвечает"
    else -> "не проверялся"
}

/**
 * Редактор канала на телефоне (нижняя шторка) — тот же набор действий, что и на ТВ: положение в списке (выше/ниже),
 * номер, название и регион, категория, скрыть, объединить с похожим (кириллица и латиница), проверка, удаление.
 * Сам список каналов остаётся под шторкой на том же месте.
 *
 * [position]/[total] — место канала в списке, который сейчас показан (внутри выбранной категории).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneChannelEditorSheet(
    channel: ChannelUiItem,
    position: Int,
    total: Int,
    categories: List<String>,
    allChannels: List<ChannelUiItem>,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSetNumber: (Int) -> Unit,
    onRename: () -> Unit,
    onMoveToCategory: (String?) -> Unit,
    onToggleHidden: () -> Unit,
    onMerge: (ChannelUiItem) -> Unit,
    onCheck: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showMerge by remember { mutableStateOf(false) }
    var numberText by remember(channel.id, channel.channelNumber) { mutableStateOf(channel.channelNumber.toString()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ZenithDimens.paddingM)) {
            Text(
                buildString {
                    append(channel.canonicalName)
                    channel.regionHint?.let { append(" ($it)") }
                },
                style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append("№${channel.channelNumber} · ${if (channel.streamCount == 1) "1 источник" else "${channel.streamCount} источников"} · ${healthText(channel.healthStatus)}")
                    if (channel.isHidden) append(" · скрыт") else if (channel.categoryHidden) append(" · категория скрыта")
                    if (channel.isMergeCandidate) append(" · не сопоставлен")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(ZenithDimens.paddingM))

            // Положение в списке и перемещение.
            Text(
                if (total > 0) "Положение: $position из $total" else "Положение: канал не входит в показанный список",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(ZenithDimens.paddingXS))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onMoveUp, enabled = position > 1) {
                    Icon(Icons.Default.ArrowUpward, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Выше")
                }
                OutlinedButton(onClick = onMoveDown, enabled = position < total) {
                    Icon(Icons.Default.ArrowDownward, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ниже")
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = numberText,
                    onValueChange = { v -> if (v.length <= 5 && v.all { it.isDigit() }) numberText = v },
                    label = { Text("Номер канала") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                val parsed = numberText.toIntOrNull()
                Button(onClick = { if (parsed != null) onSetNumber(parsed) }, enabled = parsed != null && parsed != channel.channelNumber) { Text("Задать") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            HorizontalDivider()

            EditorAction(Icons.Default.PlayArrow, "Смотреть", null, onPlay)
            EditorAction(Icons.Default.Edit, "Название и регион", null, onRename)
            EditorAction(Icons.Default.Category, "Категория", ChannelCategoryFilter.categoryOf(channel), onClick = { showCategoryPicker = true })
            EditorAction(
                if (channel.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                if (channel.isHidden) "Показывать канал" else "Скрыть канал", null, onToggleHidden
            )
            EditorAction(Icons.Default.MergeType, "Объединить с похожим…", "Если один и тот же канал записан по-разному (СТС / STS)", onClick = { showMerge = true })
            EditorAction(Icons.Default.Refresh, "Проверить сейчас", null, onCheck)
            EditorAction(Icons.Default.Delete, "Удалить канал", null, onDelete, danger = true)
            Spacer(Modifier.height(ZenithDimens.paddingL))
        }
    }

    if (showCategoryPicker) {
        PhoneCategoryPickerDialog(
            current = ChannelCategoryFilter.categoryOf(channel),
            categories = categories,
            onPick = { name -> showCategoryPicker = false; onMoveToCategory(name) },
            onDismiss = { showCategoryPicker = false }
        )
    }
    if (showMerge) {
        SimilarChannelsPickDialog(
            source = channel,
            candidates = allChannels.filter { it.id != channel.id },
            onSelect = { showMerge = false; onMerge(it) },
            onDismiss = { showMerge = false }
        )
    }
}

@Composable
private fun EditorAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String?, onClick: () -> Unit, danger: Boolean = false) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = tint) },
        supportingContent = hint?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Выбор категории: существующие, «Без категории» и новая (вводом названия). */
@Composable
internal fun PhoneCategoryPickerDialog(current: String, categories: List<String>, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категория") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName, onValueChange = { if (it.length <= 40) newName = it },
                    label = { Text("Новая категория") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (newName.isNotBlank()) {
                    TextButton(onClick = { onPick(newName.trim()) }) { Text("Создать «${newName.trim()}» и перенести") }
                }
                Spacer(Modifier.height(ZenithDimens.paddingXS))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    val options = (categories.filter { it != ChannelCategoryFilter.NO_CATEGORY } + ChannelCategoryFilter.NO_CATEGORY).distinct()
                    items(options, key = { it }) { name ->
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = { if (name == current) Text("сейчас", style = MaterialTheme.typography.labelSmall) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { onPick(if (name == ChannelCategoryFilter.NO_CATEGORY) null else name) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Объединение: похожие по звучанию названия (кириллица и латиница), поиск по части названия. Выбранный канал
 * остаётся, редактируемый — исчезает, а его ссылки переходят к выбранному.
 */
@Composable
internal fun SimilarChannelsPickDialog(source: ChannelUiItem, candidates: List<ChannelUiItem>, onSelect: (ChannelUiItem) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val ranked = remember(query, candidates, source.id) {
        val strict = ChannelNameMatcher.rank(source.canonicalName, candidates, { it.canonicalName }, query)
        if (strict.isEmpty() && query.isBlank()) {
            ChannelNameMatcher.rank(source.canonicalName, candidates, { it.canonicalName }, query, minScore = 0.3, limit = 15)
        } else strict
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Объединить «${source.canonicalName}»") },
        text = {
            Column {
                Text(
                    "Выберите канал, к которому перейдут ссылки. Выбранный останется, «${source.canonicalName}» исчезнет.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Название — латиницей или кириллицей") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (ranked.isEmpty()) {
                    Text(
                        if (query.isBlank()) "Похожих названий нет — введите часть названия" else "Ничего не найдено",
                        modifier = Modifier.padding(vertical = ZenithDimens.paddingS)
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(ranked, key = { it.item.id }) { scored ->
                        val ch = scored.item
                        val label = when {
                            query.isNotBlank() -> null
                            scored.score >= 0.9 -> "очень похож"
                            scored.score >= 0.6 -> "похож"
                            else -> "возможно"
                        }
                        ListItem(
                            headlineContent = { Text(ch.canonicalName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(listOfNotNull(ch.category?.trim()?.ifBlank { null }, label, "№${ch.channelNumber}").joinToString(" · "))
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { onSelect(ch) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Редактор категории (долгое нажатие на чип в режиме правки): переименовать, скрыть/показать, сдвинуть. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneCategoryEditorSheet(
    name: String,
    channelCount: Int,
    hidden: Boolean,
    index: Int,
    total: Int,
    onRename: (String) -> Unit,
    onToggleHidden: () -> Unit,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember(name) { mutableStateOf(name) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ZenithDimens.paddingM)) {
            Text("Категория «$name»", style = MaterialTheme.typography.titleLarge)
            Text(
                "Каналов: $channelCount" + if (hidden) " · скрыта" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(ZenithDimens.paddingM))
            if (name != ChannelCategoryFilter.NO_CATEGORY) {
                Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { if (it.length <= 40) newName = it },
                        label = { Text("Название") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onRename(newName.trim()) }, enabled = newName.isNotBlank() && newName.trim() != name) { Text("Готово") }
                }
                Spacer(Modifier.height(ZenithDimens.paddingS))
            }
            Text("Порядок среди категорий: ${index + 1} из $total", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(ZenithDimens.paddingXS))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                OutlinedButton(onClick = { onMove(0) }, enabled = index > 0) { Text("В начало") }
                OutlinedButton(onClick = { onMove(index - 1) }, enabled = index > 0) { Text("Левее") }
                OutlinedButton(onClick = { onMove(index + 1) }, enabled = index < total - 1) { Text("Правее") }
                OutlinedButton(onClick = { onMove(total - 1) }, enabled = index < total - 1) { Text("В конец") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            HorizontalDivider()
            EditorAction(
                if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                if (hidden) "Показать категорию" else "Скрыть категорию",
                "Новые каналы в скрытой категории тоже будут скрыты", onToggleHidden
            )
            Spacer(Modifier.height(ZenithDimens.paddingL))
        }
    }
}
