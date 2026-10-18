package com.platinum.ott.presentation.screens.epg

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.local.entity.EpgProgramEntity
import com.platinum.ott.ui.theme.*

private val CHANNEL_COLUMN_WIDTH = 160.dp
private val CATEGORY_SIDEBAR_WIDTH = 200.dp
private val ROW_HEIGHT = 64.dp
private const val ALL_CATEGORY = "__all__"

/**
 * РЕДИЗАЙН (разбор IPTV с нуля, EPG-сетка — "вариант C", максимальный):
 * добавлены категории-сайдбар (тот же принцип и данные, что в
 * ChannelsBrowseScreen.kt — консистентность между двумя связанными
 * экранами), вертикальная линия "сейчас" через всю сетку, панель полного
 * описания программы при ФОКУСЕ (не клике — можно листать расписание, не
 * запуская воспроизведение на каждой ячейке), и поле поиска по названию
 * передачи (UI для уже существовавшего, но не подключённого никуда
 * EpgGridViewModel.search()/searchResults/jumpToProgram()).
 *
 * Left/Right сознательно НЕ отданы под сдвиг окна времени — в сетке они
 * уже нужны для перемещения фокуса между ячейками программ пультом;
 * кнопки "◀ 30 мин"/"30 мин ▶" остаются единственным способом сдвига,
 * это обычные фокусируемые кнопки, с пульта достижимы так же, как и
 * раньше.
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
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    // ФИКС (аудит): та же причина, что в SourcesScreen.kt/ChannelsManageScreen.kt —
    // EpgGridViewModel грузит расписание один раз в init{}.
    LaunchedEffect(Unit) { viewModel.load() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORY) }
    var focusedProgram by remember { mutableStateOf<EpgProgramEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXXL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Программа передач", style = MaterialTheme.typography.displaySmall, color = ZenithOnSurface, modifier = Modifier.weight(1f))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; viewModel.search(it) },
                singleLine = true,
                textStyle = TextStyle(ZenithOnSurface, 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(200.dp).background(ZenithOnSurface.copy(alpha = 0.08f), ZenithShapeSmall).padding(ZenithDimens.paddingM, ZenithDimens.paddingSM),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("Поиск передачи", color = ZenithOnSurface.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                    inner()
                }
            )
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = { viewModel.shiftWindow(forward = false) }) { Text("◀ 30 мин") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = { viewModel.jumpToNow() }) { Text("Сейчас") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = { viewModel.shiftWindow(forward = true) }) { Text("30 мин ▶") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingXL))

        if (searchQuery.isNotBlank()) {
            SearchResultsList(
                results = searchResults,
                onResultClick = { result ->
                    viewModel.jumpToProgram(result.program.startTimeMillis)
                    searchQuery = ""
                }
            )
        } else {
            when (val state = uiState) {
                is EpgGridUiState.Loading -> Unit
                is EpgGridUiState.NoChannels -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Нет подписанных каналов — подпишитесь на них в разделе \"Каналы\"",
                            color = ZenithOnSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                is EpgGridUiState.Success -> {
                    val categoryCounts = remember(state.rows) {
                        state.rows.groupBy { it.channel.category?.ifBlank { null } ?: "Без категории" }
                            .mapValues { it.value.size }
                            .toList()
                            .sortedByDescending { it.second }
                    }
                    val visibleRows = remember(state.rows, selectedCategory) {
                        if (selectedCategory == ALL_CATEGORY) state.rows
                        else state.rows.filter { (it.channel.category?.ifBlank { null } ?: "Без категории") == selectedCategory }
                    }

                    Row(Modifier.weight(1f)) {
                        LazyColumn(
                            modifier = Modifier.width(CATEGORY_SIDEBAR_WIDTH),
                            verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)
                        ) {
                            item {
                                EpgCategoryRow("Все каналы", state.rows.size, selectedCategory == ALL_CATEGORY) { selectedCategory = ALL_CATEGORY }
                            }
                            items(categoryCounts, key = { it.first }) { (category, count) ->
                                EpgCategoryRow(category, count, selectedCategory == category) { selectedCategory = category }
                            }
                        }
                        Spacer(Modifier.width(ZenithDimens.paddingL))
                        Column(Modifier.weight(1f)) {
                            BoxWithConstraints(Modifier.weight(1f)) {
                                val nowFraction = ((System.currentTimeMillis() - state.windowStart).toFloat() / (state.windowEnd - state.windowStart)).let {
                                    if (it in 0f..1f) it else null
                                }
                                Column {
                                    RulerRow(windowStart = state.windowStart, windowEnd = state.windowEnd)
                                    Spacer(Modifier.height(ZenithDimens.paddingM))
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)) {
                                        items(visibleRows, key = { it.channel.id }) { row ->
                                            EpgChannelRowView(
                                                row = row,
                                                onPlay = { onPlayChannel(row.channel.id) },
                                                onPlayCatchup = { start, end -> onPlayCatchup(row.channel.id, start, end) },
                                                onFocusProgram = { focusedProgram = it }
                                            )
                                        }
                                    }
                                }
                                if (nowFraction != null) {
                                    val timelineWidth = maxWidth - CHANNEL_COLUMN_WIDTH
                                    Box(
                                        Modifier
                                            .padding(start = CHANNEL_COLUMN_WIDTH + timelineWidth * nowFraction)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(ZenithError.copy(alpha = 0.8f))
                                    )
                                }
                            }
                            if (focusedProgram != null) {
                                Spacer(Modifier.height(ZenithDimens.paddingM))
                                ProgramDescriptionPanel(focusedProgram!!)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgCategoryRow(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZenithShapeSmall)
            .background(
                when {
                    isFocused -> ZenithFocusContainerActive
                    selected -> ZenithOnSurface.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = if (selected) ZenithOnSurface else ZenithOnSurface.copy(alpha = 0.75f), maxLines = 1)
        Text("$count", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun ColumnScope.SearchResultsList(results: List<EpgSearchResult>, onResultClick: (EpgSearchResult) -> Unit) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text("Ничего не найдено", color = ZenithOnSurface.copy(alpha = 0.5f))
        }
        return
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)) {
        items(results, key = { "${it.channel.id}:${it.program.startTimeMillis}" }) { result ->
            var isFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZenithShapeSmall)
                    .background(if (isFocused) ZenithFocusContainerActive else ZenithSurface.copy(alpha = 0.6f))
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onResultClick(result) }
                    .padding(ZenithDimens.paddingM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (result.channel.logo != null) {
                    AsyncImage(model = result.channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                }
                Column(Modifier.weight(1f)) {
                    Text(result.program.title, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${result.channel.canonicalName} · ${formatHm(result.program.startTimeMillis)}–${formatHm(result.program.endTimeMillis)}",
                        style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramDescriptionPanel(program: EpgProgramEntity) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ZenithShapeMedium)
            .background(ZenithSurface.copy(alpha = 0.85f))
            .padding(ZenithDimens.paddingM)
    ) {
        Text(program.title, style = MaterialTheme.typography.titleSmall, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${formatHm(program.startTimeMillis)}–${formatHm(program.endTimeMillis)}",
            style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f)
        )
        if (!program.description.isNullOrBlank()) {
            Spacer(Modifier.height(ZenithDimens.paddingXS))
            Text(program.description, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.75f), maxLines = 3, overflow = TextOverflow.Ellipsis)
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
                    color = ZenithOnSurface.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                t += EpgGridViewModel.RULER_SEGMENT_MS
            }
        }
    }
}

@Composable
private fun EpgChannelRowView(row: EpgChannelRow, onPlay: () -> Unit, onPlayCatchup: (Long, Long) -> Unit, onFocusProgram: (EpgProgramEntity?) -> Unit) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.width(CHANNEL_COLUMN_WIDTH).fillMaxHeight().padding(end = ZenithDimens.paddingS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp, 28.dp).clip(ZenithShapeSmall).background(ZenithOnSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (row.channel.logo != null) {
                    AsyncImage(model = row.channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                } else {
                    Text("TV", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.4f))
                }
            }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            Text(row.channel.canonicalName, style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        },
                        onFocusProgram = onFocusProgram
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramCell(modifier: Modifier, slot: EpgSlot.ProgramSlot, playable: Boolean, onClick: () -> Unit, onFocusProgram: (EpgProgramEntity?) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(ZenithShapeSmall)
            .background(
                when {
                    isFocused -> ZenithFocusContainerActive
                    slot.isLive -> ZenithSurface.copy(alpha = 0.9f)
                    slot.isCatchupAvailable -> ZenithSurface.copy(alpha = 0.7f)
                    else -> ZenithSurface.copy(alpha = 0.5f)
                }
            )
            .then(
                if (playable) Modifier
                    .onFocusChanged { isFocused = it.isFocused; onFocusProgram(if (it.isFocused) slot.program else null) }
                    .clickable(onClick = onClick)
                else Modifier
            )
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (slot.isLive) {
                    Box(Modifier.size(6.dp).clip(ZenithShapePill).background(ZenithSuccess))
                    Spacer(Modifier.width(4.dp))
                }
                Text(slot.program.title, style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatHm(slot.program.startTimeMillis), style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f))
                if (slot.isCatchupAvailable) {
                    Spacer(Modifier.width(4.dp))
                    Text("⟲", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}
