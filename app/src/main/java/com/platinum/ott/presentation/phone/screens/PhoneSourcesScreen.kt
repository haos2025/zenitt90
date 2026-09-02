package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.sources.*
import com.platinum.ott.ui.theme.*

/**
 * Телефон-версия SourcesScreen.kt (TV) — тот же SourcesViewModel/диалоги,
 * отличается только вёрсткой (Card вместо TV Surface-строк, TopAppBar со
 * стрелкой назад вместо кнопки "Назад" в заголовке — тот же паттерн, что и
 * в PhoneCacheManagementScreen.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSourcesScreen(
    navController: NavHostController,
    onAddSourceClick: () -> Unit,
    viewModel: SourcesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshingIds by viewModel.refreshingIds.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<SourceUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SourceUiItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Источники") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddSourceClick, text = { Text("Добавить") }, icon = {})
        }
    ) { padding ->
        val sources = (uiState as? SourcesUiState.Success)?.sources.orEmpty()

        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (sources.isEmpty()) {
                Text(
                    "Пока нет ни одного источника",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(ZenithDimens.paddingSM),
                    verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                ) {
                    items(sources, key = { it.id }) { item ->
                        PhoneSourceCard(
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

@Composable
private fun PhoneSourceCard(
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
                    Text(item.label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    Text(
                        if (item.type == "m3u") "M3U" else "Xtream",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${item.contentCount} в каталоге • ${formatLastRefreshed(item.lastRefreshedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.lastRefreshStatus != null && item.lastRefreshStatus != "ok") {
                    Text("⚠ ${item.lastRefreshStatus}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Switch(checked = item.enabled, onCheckedChange = onToggleEnabled)

            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(start = ZenithDimens.paddingS), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Обновить") }
            }

            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Действия") }
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
    !enabled -> Color.Gray.copy(alpha = 0.4f)
    lastRefreshStatus == null -> ZenithWarning
    lastRefreshStatus == "ok" -> ZenithSuccess
    else -> ZenithError
}
