package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.epg.EpgChannelRow
import com.platinum.ott.presentation.screens.epg.EpgGridUiState
import com.platinum.ott.presentation.screens.epg.EpgGridViewModel
import com.platinum.ott.presentation.screens.epg.EpgSlot
import com.platinum.ott.presentation.screens.epg.formatHm
import com.platinum.ott.ui.theme.ZenithShapeSmall

private val CHANNEL_COLUMN_WIDTH = 96.dp
private val ROW_HEIGHT = 56.dp

/**
 * Телефон-версия EpgGridScreen.kt (TV) — тот же EpgGridViewModel, тот же
 * принцип разделения, что и у PhoneChannelsScreen.kt/ChannelsScreen.kt:
 * общая ViewModel/логика, отдельный composable под material3 вместо
 * tv-material3 (в этой сетке фокус пульта неактуален, но названия
 * функций/структура намеренно зеркалят TV-версию для единообразия).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneEpgGridScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onPlayCatchup: (channelId: String, startMillis: Long, endMillis: Long) -> Unit,
    viewModel: EpgGridViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ФИКС (аудит): тот же паттерн, что в TV-версии (EpgGridScreen.kt).
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Программа передач") },
                navigationIcon = { IconButton(onClick = onBackPressed) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.shiftWindow(forward = false) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Раньше на 30 минут") }
                TextButton(onClick = { viewModel.jumpToNow() }) { Text("Сейчас") }
                IconButton(onClick = { viewModel.shiftWindow(forward = true) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Позже на 30 минут") }
            }

            when (val state = uiState) {
                is EpgGridUiState.Loading -> Unit
                is EpgGridUiState.NoChannels -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Нет подписанных каналов — подпишитесь на них в разделе \"Каналы\"",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                is EpgGridUiState.Success -> {
                    PhoneRulerRow(windowStart = state.windowStart, windowEnd = state.windowEnd)
                    LazyColumn(Modifier.weight(1f)) {
                        items(state.rows, key = { it.channel.id }) { row ->
                            PhoneEpgChannelRowView(
                                row = row,
                                onPlay = { onPlayChannel(row.channel.id) },
                                onPlayCatchup = { start, end -> onPlayCatchup(row.channel.id, start, end) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneRulerRow(windowStart: Long, windowEnd: Long) {
    Row(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS)) {
        Box(Modifier.width(CHANNEL_COLUMN_WIDTH))
        Row(Modifier.weight(1f)) {
            var t = windowStart
            while (t < windowEnd) {
                Text(
                    formatHm(t),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                t += EpgGridViewModel.RULER_SEGMENT_MS
            }
        }
    }
}

@Composable
private fun PhoneEpgChannelRowView(row: EpgChannelRow, onPlay: () -> Unit, onPlayCatchup: (Long, Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = ZenithDimens.paddingM, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.width(CHANNEL_COLUMN_WIDTH).fillMaxHeight().padding(end = ZenithDimens.paddingS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp, 22.dp).clip(ZenithShapeSmall).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (row.channel.logo != null) {
                    AsyncImage(model = row.channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                }
            }
            Spacer(Modifier.width(ZenithDimens.paddingXS))
            Text(row.channel.canonicalName, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            row.slots.forEach { slot ->
                when (slot) {
                    is EpgSlot.GapSlot -> Spacer(Modifier.weight(slot.weightMinutes))
                    is EpgSlot.ProgramSlot -> PhoneProgramCell(
                        modifier = Modifier.weight(slot.weightMinutes).fillMaxHeight(),
                        slot = slot,
                        playable = (slot.isLive && row.channel.streamCount > 0) || slot.isCatchupAvailable,
                        onClick = {
                            if (slot.isLive) onPlay()
                            else if (slot.isCatchupAvailable) onPlayCatchup(slot.program.startTimeMillis, slot.program.endTimeMillis)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneProgramCell(modifier: Modifier, slot: EpgSlot.ProgramSlot, playable: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .clip(ZenithShapeSmall)
            .background(
                when {
                    slot.isLive -> MaterialTheme.colorScheme.primaryContainer
                    slot.isCatchupAvailable -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(if (playable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(slot.program.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatHm(slot.program.startTimeMillis), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                // См. тот же комментарий в EpgGridScreen.kt (TV) — единственная
                // видимая подсказка, что прошедшую программу можно открыть
                // из архива.
                if (slot.isCatchupAvailable) {
                    Spacer(Modifier.width(4.dp))
                    Text("⟲", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}
