package com.platinum.ott.presentation.screens.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.*

/**
 * Заменяет прежний SetupScreen.kt (одноразовая настройка одного источника)
 * — теперь список уже подключённых плейлистов/панелей с добавлением через
 * URL/локальный файл/QR (следующая подзадача — сам диалог добавления,
 * onAddSourceClick пока просто прокидывается наружу в NavHost).
 *
 * Кнопки-обходы (Избранное/История/Настройки), которые раньше были в углу
 * SetupScreen.kt как наследие версии без нормальной навигации — здесь их
 * нет вообще, вместо них обычная кнопка "Назад" (см. CacheManagementScreen.kt
 * — тот же паттерн для экранов второго уровня внутри Настроек, без
 * NavSidebar, который используется только для верхнеуровневых экранов).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourcesScreen(
    onBackPressed: () -> Unit,
    onAddSourceClick: () -> Unit,
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshingIds by viewModel.refreshingIds.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<SourceUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SourceUiItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXXL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Источники", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBackPressed) { Text("Назад") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingXL))

        val sources = (uiState as? SourcesUiState.Success)?.sources.orEmpty()

        if (sources.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Пока нет ни одного источника", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) {
                items(sources, key = { it.id }) { item ->
                    SourceCard(
                        item = item,
                        isRefreshing = item.id in refreshingIds,
                        onToggleEnabled = { viewModel.setEnabled(item.id, it) },
                        onRefresh = { viewModel.refresh(item.id) },
                        onRename = { renameTarget = item },
                        onMoveUp = { viewModel.moveUp(item.id) },
                        onMoveDown = { viewModel.moveDown(item.id) },
                        onDelete = { deleteTarget = item }
                    )
                }
            }
        }

        Spacer(Modifier.height(ZenithDimens.paddingL))
        Button(onClick = onAddSourceClick, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("+ Добавить источник")
        }
    }

    renameTarget?.let { target ->
        RenameSourceDialog(
            currentLabel = target.label,
            onConfirm = { newLabel -> viewModel.rename(target.id, newLabel); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { target ->
        DeleteSourceConfirmDialog(
            label = target.label,
            onConfirm = { viewModel.delete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceCard(
    item: SourceUiItem,
    isRefreshing: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember(item.id) { mutableStateOf(false) }
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = ZenithSurface, focusedContainerColor = ZenithFocusContainerActive),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(ZenithDimens.paddingM).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(statusColor(item.enabled, item.lastRefreshStatus), CircleShape)
            )
            Spacer(Modifier.width(ZenithDimens.paddingM))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    Text(
                        if (item.type == "m3u") "M3U" else "Xtream",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Text(
                    "${item.contentCount} в каталоге • ${formatLastRefreshed(item.lastRefreshedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
                if (item.lastRefreshStatus != null && item.lastRefreshStatus != "ok") {
                    Text("⚠ ${item.lastRefreshStatus}", style = MaterialTheme.typography.bodySmall, color = ZenithError)
                }
            }

            Switch(checked = item.enabled, onCheckedChange = onToggleEnabled)
            Spacer(Modifier.width(ZenithDimens.paddingS))

            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Обновить", tint = Color.White) }
            }

            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Действия", tint = Color.White) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Изменить название") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Приоритет вверх") }, onClick = { showMenu = false; onMoveUp() }, enabled = !item.isFirst)
                    DropdownMenuItem(text = { Text("Приоритет вниз") }, onClick = { showMenu = false; onMoveDown() }, enabled = !item.isLast)
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

private fun statusColor(enabled: Boolean, lastRefreshStatus: String?): Color = when {
    !enabled -> Color.White.copy(alpha = 0.3f)
    lastRefreshStatus == null -> ZenithWarning
    lastRefreshStatus == "ok" -> ZenithSuccess
    else -> ZenithError
}
