package com.platinum.ott.presentation.screens.sources

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * Один диалог на обе платформы — тот же принцип, что и в
 * FavoritesFolderDialogs.kt (material3 AlertDialog внутри TV-экранов уже не
 * новый паттерн в проекте, см. PlaybackOptionDialog).
 */
@Composable
fun RenameSourceDialog(
    currentLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Название источника") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(label) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Подтверждение удаления источника — вместе с ним теряется весь его закэшированный контент. */
@Composable
fun DeleteSourceConfirmDialog(
    label: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить источник?") },
        text = { Text("«$label» и весь его контент будут удалены из приложения. Сам плейлист/панель на сервере не затрагивается.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Общее форматирование "когда обновлялся" для карточек источника на обеих платформах. */
fun formatLastRefreshed(timestamp: Long?): String {
    if (timestamp == null) return "Ещё не обновлялся"
    val fmt = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
    return "Обновлено: ${fmt.format(java.util.Date(timestamp))}"
}
