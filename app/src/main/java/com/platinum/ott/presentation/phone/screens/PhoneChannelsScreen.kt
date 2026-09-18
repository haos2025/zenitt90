package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.presentation.screens.channels.ChannelsUiState
import com.platinum.ott.presentation.screens.channels.ChannelsViewModel
import com.platinum.ott.presentation.screens.channels.DeleteChannelConfirmDialog
import com.platinum.ott.presentation.screens.channels.MergeChannelDialog
import com.platinum.ott.presentation.screens.channels.RenameChannelDialog

/**
 * Телефон-версия ChannelsScreen.kt (TV) — тот же ChannelsViewModel и те же
 * общие диалоги (ChannelDialogs.kt), только список/карточка в
 * material3-стиле вместо tv-material3 (тот же принцип разделения, что и у
 * PhoneSourcesScreen.kt/SourcesScreen.kt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneChannelsScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onOpenEpgGrid: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var mergeTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    val channels = (uiState as? ChannelsUiState.Success)?.channels.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каналы") },
                navigationIcon = { IconButton(onClick = onBackPressed) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    // PROMPT_EPG.md, подзадача 4
                    IconButton(onClick = onOpenEpgGrid) { Icon(Icons.Filled.DateRange, contentDescription = "Программа передач") }
                    IconButton(onClick = { viewModel.checkAll() }, enabled = !isChecking) {
                        if (isChecking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Проверить сейчас")
                    }
                }
            )
        }
    ) { padding ->
        if (channels.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Пока нет каналов — добавьте M3U-источник живых каналов\nили Xtream-панель в Источниках",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(ZenithDimens.paddingXL)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(ZenithDimens.paddingM),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) {
                items(channels, key = { it.id }) { item ->
                    PhoneChannelCard(
                        item = item,
                        onToggleSubscribed = { viewModel.setSubscribed(item.id, it) },
                        onRename = { renameTarget = item },
                        onMerge = { mergeTarget = item },
                        onDelete = { deleteTarget = item },
                        onCheckNow = { viewModel.checkOne(item.id) },
                        onPlay = { onPlayChannel(item.id) }
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameChannelDialog(
            channel = target,
            onConfirm = { name, regionHint -> viewModel.rename(target.id, name, regionHint); renameTarget = null },
            onDismiss = { renameTarget = null }
        )
    }
    mergeTarget?.let { target ->
        MergeChannelDialog(
            source = target,
            candidates = channels.filter { it.id != target.id },
            onSelect = { targetId -> viewModel.merge(target.id, targetId); mergeTarget = null },
            onDismiss = { mergeTarget = null }
        )
    }
    deleteTarget?.let { target ->
        DeleteChannelConfirmDialog(
            channel = target,
            onConfirm = { viewModel.delete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun PhoneChannelCard(
    item: ChannelUiItem,
    onToggleSubscribed: (Boolean) -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onCheckNow: () -> Unit,
    onPlay: () -> Unit
) {
    var showMenu by remember(item.id) { mutableStateOf(false) }
    // onClick прямо на Surface — на телефоне это не проблема (тач, не
    // D-pad-фокус построчно, как на TV в ChannelsScreen.kt), Switch/меню
    // внутри останавливают свой собственный клик через consumeClick-семантику
    // material3-компонентов, не пропуская его наверх на родительский Surface.
    Surface(
        shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth(),
        onClick = onPlay, enabled = item.streamCount > 0
    ) {
        Row(Modifier.padding(ZenithDimens.paddingM).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp, 32.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.logo != null) {
                    AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                } else {
                    Text("TV", style = MaterialTheme.typography.labelSmall)
                }
                // Та же точка health-check, что и на TV (ChannelsScreen.kt) —
                // цвет по агрегату ChannelUiItem.healthStatus.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.healthStatus) {
                                "alive" -> Color(0xFF4CAF50)
                                "dead" -> MaterialTheme.colorScheme.error
                                else -> Color.Gray
                            }
                        )
                )
            }
            Spacer(Modifier.width(ZenithDimens.paddingM))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.canonicalName, style = MaterialTheme.typography.titleSmall)
                    if (item.regionHint != null) {
                        Spacer(Modifier.width(ZenithDimens.paddingXS))
                        Text("(${item.regionHint})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                Text(
                    buildString {
                        append(if (item.streamCount == 1) "1 источник" else "${item.streamCount} источника(ов)")
                        item.category?.let { append(" • $it") }
                        if (item.isMergeCandidate) append(" • не сопоставлен")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isMergeCandidate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Switch(checked = item.isSubscribed, onCheckedChange = onToggleSubscribed)

            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Действия") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Проверить сейчас") }, onClick = { showMenu = false; onCheckNow() })
                    DropdownMenuItem(text = { Text("Изменить название/регион") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Слить с другим каналом") }, onClick = { showMenu = false; onMerge() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
}
