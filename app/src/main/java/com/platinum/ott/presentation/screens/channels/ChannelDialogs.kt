package com.platinum.ott.presentation.screens.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem

// Тот же паттерн, что и SourceDialogs.kt/FavoritesFolderDialogs.kt —
// material3 AlertDialog общий для TV и телефона, отдельного файла под
// каждую платформу не заводим.

/** Переименование + региональная пометка ("Москва"/"Владивосток" и т.п.). */
@Composable
fun RenameChannelDialog(
    channel: ChannelUiItem,
    onConfirm: (name: String, regionHint: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(channel.canonicalName) }
    var regionHint by remember { mutableStateOf(channel.regionHint ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Канал") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = regionHint, onValueChange = { regionHint = it },
                    label = { Text("Регион (необязательно)") },
                    placeholder = { Text("Например: Москва") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, regionHint) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Слияние вручную — только для каналов без tvg-id (isMergeCandidate),
 * см. ChannelRepository.merge(). Список целей — все ОСТАЛЬНЫЕ каналы,
 * подписанные показаны первыми (самый вероятный настоящий получатель —
 * уже используемый пользователем канал, не другой такой же неопознанный).
 * Явного подтверждения после тапа по строке нет намеренно — тот же
 * паттерн, что и в MoveToFolderDialog (FavoritesFolderDialogs.kt), выбор
 * строки это и есть подтверждение, отдельный шаг "точно?" избыточен для
 * действия, которое ничего не удаляет безвозвратно с точки зрения контента
 * (стримы переносятся, не пропадают).
 */
@Composable
fun MergeChannelDialog(
    source: ChannelUiItem,
    candidates: List<ChannelUiItem>,
    onSelect: (targetChannelId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val ordered = candidates.sortedWith(
        compareByDescending<ChannelUiItem> { it.isSubscribed }.thenBy { it.canonicalName.lowercase() }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Слить «${source.canonicalName}» с...") },
        text = {
            if (ordered.isEmpty()) {
                Text("Больше не с чем сливать — это единственный канал в списке.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(ordered, key = { it.id }) { candidate ->
                        TextButton(onClick = { onSelect(candidate.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(candidate.canonicalName, modifier = Modifier.fillMaxWidth())
                                if (candidate.isSubscribed) {
                                    Text("Уже в списке", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Подтверждение удаления канала — вместе с ним теряются все его ChannelStream. */
@Composable
fun DeleteChannelConfirmDialog(
    channel: ChannelUiItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить канал?") },
        text = { Text("«${channel.canonicalName}» и все его ссылки от источников будут удалены из списка каналов.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
