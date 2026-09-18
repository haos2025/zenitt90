package com.platinum.ott.presentation.screens.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.*

private val CHANNEL_COLUMN_WIDTH = 160.dp
private val ROW_HEIGHT = 64.dp

/**
 * PROMPT_EPG.md, подзадача 4 — сетка программ. Достижим из "Каналы"
 * (см. ChannelsScreen.kt), тот же уровень вложенности, что и сам
 * "channels" (обычная кнопка "Назад", не NavSidebar — см. комментарий там
 * же про нишевые экраны). Показывает только ПОДПИСАННЫЕ каналы
 * (EpgGridViewModel.reload() → channelRepository.getSubscribed()) — тот
 * же список, что пользователь видит включённым в "Каналы".
 *
 * Кликабельны две категории ячеек: "сейчас в эфире" (см.
 * EpgSlot.ProgramSlot.isLive) — ведёт на прямой эфир, и уже прошедшие
 * программы в пределах архива канала (EpgSlot.ProgramSlot.isCatchupAvailable,
 * см. EpgGridViewModel.kt) — ведут на архив (PROMPT_EPG.md, подзадача 5).
 * Будущие программы неактивны — заранее запланировать напоминание/запись
 * не входит ни в одну из подзадач EPG.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgGridScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onPlayCatchup: (channelId: String, startMillis: Long, endMillis: Long) -> Unit,
    viewModel: EpgGridViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXXL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Программа передач", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.shiftWindow(forward = false) }) { Text("◀ 30 мин") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(onClick = { viewModel.jumpToNow() }) { Text("Сейчас") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(onClick = { viewModel.shiftWindow(forward = true) }) { Text("30 мин ▶") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(onClick = onBackPressed) { Text("Назад") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingXL))

        when (val state = uiState) {
            is EpgGridUiState.Loading -> Unit
            is EpgGridUiState.NoChannels -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Нет подписанных каналов — подпишитесь на них в разделе \"Каналы\"",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            is EpgGridUiState.Success -> {
                RulerRow(windowStart = state.windowStart, windowEnd = state.windowEnd)
                Spacer(Modifier.height(ZenithDimens.paddingM))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)
                ) {
                    items(state.rows, key = { it.channel.id }) { row ->
                        EpgChannelRowView(
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

@Composable
private fun RulerRow(windowStart: Long, windowEnd: Long) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(CHANNEL_COLUMN_WIDTH))
        Row(Modifier.weight(1f)) {
            var t = windowStart
            while (t < windowEnd) {
                Text(
                    formatHm(t),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                t += EpgGridViewModel.RULER_SEGMENT_MS
            }
        }
    }
}

@Composable
private fun EpgChannelRowView(row: EpgChannelRow, onPlay: () -> Unit, onPlayCatchup: (Long, Long) -> Unit) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(CHANNEL_COLUMN_WIDTH).fillMaxHeight().padding(end = ZenithDimens.paddingS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp, 28.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (row.channel.logo != null) {
                    AsyncImage(model = row.channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                } else {
                    Text("TV", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                }
            }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            Text(row.channel.canonicalName, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            row.slots.forEach { slot ->
                when (slot) {
                    is EpgSlot.GapSlot -> Spacer(Modifier.weight(slot.weightMinutes))
                    is EpgSlot.ProgramSlot -> ProgramCell(
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
private fun ProgramCell(modifier: Modifier, slot: EpgSlot.ProgramSlot, playable: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> ZenithFocusContainerActive
                    slot.isLive -> ZenithSurface.copy(alpha = 0.9f)
                    slot.isCatchupAvailable -> ZenithSurface.copy(alpha = 0.7f)
                    else -> ZenithSurface.copy(alpha = 0.5f)
                }
            )
            .then(if (playable) Modifier.onFocusChanged { isFocused = it.isFocused }.clickable(onClick = onClick) else Modifier)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (slot.isLive) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(ZenithSuccess))
                    Spacer(Modifier.width(4.dp))
                }
                Text(slot.program.title, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatHm(slot.program.startTimeMillis), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                // PROMPT_EPG.md, подзадача 5 — единственная видимая
                // подсказка, что прошедшую программу можно открыть из
                // архива (иначе от обычной неактивной ячейки прошлого
                // визуально не отличить).
                if (slot.isCatchupAvailable) {
                    Spacer(Modifier.width(4.dp))
                    Text("⟲", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}
