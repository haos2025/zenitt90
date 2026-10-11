package com.platinum.ott.presentation.screens.favorites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.FOLDER_COLORS
import com.platinum.ott.core.nextFolderColor
import com.platinum.ott.core.parseFolderColor
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
/** Цветная точка папки (для чипов, списка папок и выбора назначения). */
@Composable
fun FolderDot(colorHex: String, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(parseFolderColor(colorHex)))
}

/** Ряд из готовых цветов для выбора; selected подсвечивается белой обводкой. */
@Composable
private fun FolderColorPicker(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
        FOLDER_COLORS.forEach { hex ->
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(parseFolderColor(hex))
                    .then(if (isSelected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onSelect(hex) }
            )
        }
    }
}

/**
 * Список папок (точка цвета — нажатие переключает на следующий цвет палитры,
 * корзина — удаление) + создание новой папки с выбором цвета.
 * Удаление папки решено с пользователем: удаляет и её содержимое целиком
 * (FavoritesUseCase.deleteFolder -> FavoritesDao.deleteFolderWithContents).
 */
@Composable
fun FolderManagerDialog(
    folders: List<FolderEntity>,
    onCreate: (name: String, color: String) -> Unit,
    onDelete: (FolderEntity) -> Unit,
    onChangeColor: (FolderEntity, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderName by remember { mutableStateOf("") }
    var newFolderColor by remember { mutableStateOf(FOLDER_COLORS.first()) }
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
                            Box(
                                Modifier.size(40.dp).clickable { onChangeColor(folder, nextFolderColor(folder.color)) },
                                contentAlignment = Alignment.Center
                            ) { FolderDot(folder.color, 20.dp) }
                            Text(folder.name, modifier = Modifier.weight(1f).padding(vertical = ZenithDimens.paddingS))
                            IconButton(onClick = { onDelete(folder) }) { Icon(Icons.Default.Delete, "Удалить папку «${folder.name}»") }
                        }
                    }
                    Text("Нажмите на цветную точку, чтобы сменить цвет", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(ZenithDimens.paddingS))
                OutlinedTextField(
                    value = newFolderName, onValueChange = { newFolderName = it },
                    label = { Text("Новая папка") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(ZenithDimens.paddingS))
                FolderColorPicker(selected = newFolderColor, onSelect = { newFolderColor = it })
                TextButton(
                    onClick = { if (newFolderName.isNotBlank()) { onCreate(newFolderName, newFolderColor); newFolderName = "" } },
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            FolderDot(folder.color)
                            Spacer(Modifier.width(ZenithDimens.paddingS))
                            Text(folder.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
