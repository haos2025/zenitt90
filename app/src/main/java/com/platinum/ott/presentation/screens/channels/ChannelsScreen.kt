package com.platinum.ott.presentation.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.ui.theme.*

/**
 * PROMPT_IPTV_FOUNDATION.md, подзадача "UI слияния каналов" — второй
 * уровень навигации внутри Настроек, тот же паттерн, что и SourcesScreen.kt
 * (обычная кнопка "Назад", без NavSidebar — тот только для верхнеуровневых
 * экранов, см. SourcesScreen.kt).
 *
 * Список показывает ВСЕ каналы (и подписанные, и ещё нет) — подписка это
 * фильтр "показывать в разделе Каналы", а не факт существования записи:
 * канал появляется здесь при первом же refresh() live-источника
 * (ChannelMatchingRepository), до подписки он просто isSubscribed = false.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var mergeTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ChannelUiItem?>(null) }

    val channels = (uiState as? ChannelsUiState.Success)?.channels.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXXL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Каналы", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            // Кнопка сама уважает бэкофф ChannelHealthChecker (isDue()) —
            // это не "перепроверить всё немедленно", а "не ждать до
            // ближайшего запуска ChannelHealthCheckWorker", см. комментарий
            // у ChannelsViewModel.checkAll().
            OutlinedButton(onClick = { viewModel.checkAll() }, enabled = !isChecking) {
                Text(if (isChecking) "Проверка..." else "Проверить сейчас")
            }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(onClick = onBackPressed) { Text("Назад") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingXL))

        if (channels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Пока нет каналов — добавьте M3U-источник живых каналов\nили Xtream-панель в Источниках",
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
            ) {
                items(channels, key = { it.id }) { item ->
                    ChannelCard(
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelCard(
    item: ChannelUiItem,
    onToggleSubscribed: (Boolean) -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onCheckNow: () -> Unit,
    onPlay: () -> Unit
) {
    var showMenu by remember(item.id) { mutableStateOf(false) }
    // Декоративный Box, не Surface с onClick — тот же вывод, что и в
    // SourcesScreen.kt/SourceCard: карточка сама не должна перехватывать
    // фокус пульта, иначе Switch/IconButton внутри становятся недостижимы.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ZenithSurface)
    ) {
        Row(
            Modifier.padding(ZenithDimens.paddingM).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp, 36.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.logo != null) {
                    AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                } else {
                    Text("TV", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
                // Цветная точка health-check в углу превью — тот же приём,
                // что statusColor в SourceCard (SourcesScreen.kt), только
                // здесь это агрегат по всем ChannelStreamEntity канала
                // (ChannelRepository.getAll()), а не по одному источнику.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(healthColor(item.healthStatus))
                )
            }
            Spacer(Modifier.width(ZenithDimens.paddingM))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.canonicalName, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (item.regionHint != null) {
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        Text("(${item.regionHint})", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                Text(
                    buildString {
                        append(if (item.streamCount == 1) "1 источник" else "${item.streamCount} источника(ов)")
                        item.category?.let { append(" • $it") }
                        if (item.isMergeCandidate) append(" • не сопоставлен с другими источниками")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isMergeCandidate) ZenithWarning else Color.White.copy(alpha = 0.5f)
                )
            }

            // Отдельная фокусируемая кнопка, не клик по всей карточке — та
            // же причина, что и у декоративного Box выше: клик на весь ряд
            // сделал бы Switch/меню недостижимыми пультом (D-pad идёт по
            // фокусируемым элементам последовательно, а не "тап в область").
            if (item.streamCount > 0) {
                ChannelIconButton(onClick = onPlay, contentDescription = "Смотреть", icon = Icons.Default.PlayArrow)
                Spacer(Modifier.width(ZenithDimens.paddingS))
            }
            Switch(checked = item.isSubscribed, onCheckedChange = onToggleSubscribed)
            Spacer(Modifier.width(ZenithDimens.paddingS))

            Box {
                ChannelIconButton(onClick = { showMenu = true }, contentDescription = "Действия", icon = Icons.Default.MoreVert)
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

// "alive"/"dead"/"unknown" → цвет точки. ZenithSuccess/ZenithError уже
// используются в проекте для похожих статусных индикаторов (см.
// lastRefreshStatus в SourcesScreen.kt) — здесь тот же словарь цветов,
// просто третье, нейтральное состояние для ещё не проверенных стримов.
private fun healthColor(status: String): Color = when (status) {
    "alive" -> ZenithSuccess
    "dead" -> ZenithError
    else -> Color.Gray
}

// Дублирует TvIconButton из SourcesScreen.kt (тот private) — тот же
// принцип, что и в других местах проекта, где похожий небольшой компонент
// дублируется локально, а не выносится через границу файла ради одной
// кнопки (см., например, ZXing-код QrScanScreen.kt/SyncPairingScreen.kt).
@Composable
private fun ChannelIconButton(onClick: () -> Unit, contentDescription: String, icon: ImageVector) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isFocused) ZenithFocusContainerActive else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White)
    }
}
