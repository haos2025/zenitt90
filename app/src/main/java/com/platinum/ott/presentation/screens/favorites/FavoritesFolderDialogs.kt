package com.platinum.ott.presentation.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.local.entity.FolderEntity

// Общий для TV (FavoritesScreen) и телефона (PhoneFavoritesScreen) набор
// диалогов — папки функционально одинаковы на обеих платформах, писать
// две почти идентичные реализации не было смысла. androidx.compose.material3
// (AlertDialog/OutlinedTextField/TextButton) уже используется внутри
// TV-экранов в проекте (см. PlaybackOptionDialog в PlaybackMenuOverlay.kt/
// PhonePlayerController.kt) — не новый для проекта паттерн смешивания с
// tv-material3.

/**
 * Список папок с удалением + поле создания новой — один диалог на оба
 * действия. Удаление папки решено с пользователем: удаляет и её
 * содержимое целиком (см. FavoritesUseCase.deleteFolder ->
 * FavoritesDao.deleteFolderWithContents), поэтому здесь без
 * промежуточного подтверждения "точно удалить?" не добавляли —
 * если понадобится защита от случайного тапа, это отдельная правка.
 */
@Composable
fun FolderManagerDialog(
    folders: List<FolderEntity>,
    onCreate: (String) -> Unit,
    onDelete: (FolderEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Папки") },
        text = {
            Column {
                if (folders.isEmpty()) {
                    Text("Пока нет ни одной папки", color = Color.Gray)
                } else {
                    folders.forEach { folder ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(folder.name, modifier = Modifier.weight(1f).padding(vertical = ZenithDimens.paddingS))
                            IconButton(onClick = { onDelete(folder) }) { Icon(Icons.Default.Delete, "Удалить папку «${folder.name}»") }
                        }
                    }
                }
                Spacer(Modifier.height(ZenithDimens.paddingS))
                OutlinedTextField(
                    value = newFolderName, onValueChange = { newFolderName = it },
                    label = { Text("Новая папка") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { if (newFolderName.isNotBlank()) { onCreate(newFolderName); newFolderName = "" } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Создать") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

/** Выбор папки назначения для одного элемента избранного, включая "без папки". */
@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить в папку") },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Без папки", modifier = Modifier.fillMaxWidth())
                }
                folders.forEach { folder ->
                    TextButton(onClick = { onSelect(folder.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(folder.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
