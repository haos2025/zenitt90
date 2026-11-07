package com.platinum.ott.presentation.screens.epg

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.platinum.ott.presentation.components.TvPillButton
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.ui.theme.*

private val CHANNEL_COLUMN_WIDTH = 160.dp
private val ROW_HEIGHT = 60.dp
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
    var focusedCell by remember { mutableStateOf<Pair<EpgChannelRow, EpgSlot.ProgramSlot>?>(null) }
    var openedInfo by remember { mutableStateOf<Pair<EpgChannelRow, EpgSlot.ProgramSlot>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Программа передач", style = MaterialTheme.typography.displaySmall, color = ZenithOnSurface, modifier = Modifier.weight(1f))
            // Не открывает клавиатуру сам: сначала кнопка, по OK — ввод.
            com.platinum.ott.presentation.screens.channels.ChannelSearchField(
                query = searchQuery,
                onChange = { searchQuery = it; viewModel.search(it) },
                modifier = Modifier.width(200.dp),
                hint = "Поиск передачи"
            )
            Spacer(Modifier.width(ZenithDimens.paddingS))
            TvPillButton(Icons.Filled.ChevronLeft, { viewModel.shiftWindow(forward = false) }, label = "30 мин")
            Spacer(Modifier.width(ZenithDimens.paddingS))
            TvPillButton(Icons.Filled.Schedule, { viewModel.jumpToNow() }, label = "Сейчас")
            Spacer(Modifier.width(ZenithDimens.paddingS))
            TvPillButton(Icons.Filled.ChevronRight, { viewModel.shiftWindow(forward = true) }, label = "30 мин")
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))

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
                is EpgGridUiState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                is EpgGridUiState.NoChannels -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Нет подписанных каналов — подпишитесь на них в разделе \"Каналы\"",
                            color = ZenithOnSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                is EpgGridUiState.Success -> {
                    val categoryOf = { row: EpgChannelRow -> row.channel.category?.ifBlank { null } ?: "Без категории" }
                    val categoryCounts = remember(state.rows) {
                        state.rows.groupBy(categoryOf).mapValues { it.value.size }.toList().sortedByDescending { it.second }
                    }
                    val visibleRows = remember(state.rows, selectedCategory) {
                        if (selectedCategory == ALL_CATEGORY) state.rows else state.rows.filter { categoryOf(it) == selectedCategory }
                    }

                    // Категории — ряд чипов над сеткой (как на телефоне): сетка получает всю ширину экрана.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                        item {
                            EpgCategoryChip("Все · ${state.rows.size}", selectedCategory == ALL_CATEGORY) {
                                selectedCategory = ALL_CATEGORY; focusedCell = null
                            }
                        }
                        items(categoryCounts, key = { it.first }) { (category, count) ->
                            EpgCategoryChip("$category · $count", selectedCategory == category) {
                                selectedCategory = category; focusedCell = null
                            }
                        }
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingM))

                    // Нет ни одной передачи в окне — объясняем, что делать, вместо пустой сетки.
                    val hasPrograms = remember(visibleRows) { visibleRows.any { r -> r.slots.any { it is EpgSlot.ProgramSlot } } }
                    if (!hasPrograms) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Нет данных программы на это время", style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface)
                                Spacer(Modifier.height(ZenithDimens.paddingXS))
                                Text(
                                    "Нажмите «Сейчас» или выберите другую категорию. Если пусто везде — обновите источник в Настройках: у провайдера может не быть программы передач.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZenithOnSurfaceVariant
                                )
                            }
                        }
                    } else {
                        RulerRow(windowStart = state.windowStart, windowEnd = state.windowEnd)
                        Spacer(Modifier.height(ZenithDimens.paddingXS))
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(visibleRows, key = { it.channel.id }) { row ->
                                    EpgChannelRowView(
                                        row = row,
                                        onPlay = { onPlayChannel(row.channel.id) },
                                        onPlayCatchup = { start, end -> onPlayCatchup(row.channel.id, start, end) },
                                        onFocusProgram = { slot -> focusedCell = row to slot },
                                        onInfo = { slot -> openedInfo = row to slot }
                                    )
                                }
                            }
                            // Линия «сейчас»: доля окна → смещение внутри колонки программ.
                            val now = System.currentTimeMillis()
                            if (now in state.windowStart until state.windowEnd) {
                                val fraction = (now - state.windowStart).toFloat() / (state.windowEnd - state.windowStart)
                                val timelineWidth = maxWidth - CHANNEL_COLUMN_WIDTH
                                Box(
                                    Modifier
                                        .offset(x = CHANNEL_COLUMN_WIDTH + timelineWidth * fraction)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        ProgramDescriptionPanel(focusedCell)
                    }
                }
            }
        }
    }

    openedInfo?.let { (row, slot) ->
        EpgInfoDialog(row = row, slot = slot, onDismiss = { openedInfo = null })
    }
}

@Composable
private fun EpgCategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapePill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else ZenithOnSurface.copy(alpha = 0.08f),
            contentColor = ZenithOnSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** Окно с описанием передачи для ячеек, которые нельзя запустить (ещё не началась / нет записи). */
@Composable
private fun EpgInfoDialog(row: EpgChannelRow, slot: EpgSlot.ProgramSlot, onDismiss: () -> Unit) {
    val closeFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface)
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.10f), ZenithShapeLarge)
                .padding(ZenithDimens.paddingL)
        ) {
            Text(slot.program.title, style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
            Spacer(Modifier.height(ZenithDimens.paddingXS))
            Text(
                "${row.channel.canonicalName} · ${formatHm(slot.program.startTimeMillis)}–${formatHm(slot.program.endTimeMillis)}" +
                    (slot.program.category?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurfaceVariant
            )
            slot.program.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(ZenithDimens.paddingS))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.85f), maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text(
                if (slot.program.startTimeMillis > System.currentTimeMillis()) "Эта передача ещё не началась" else "Запись этой передачи недоступна",
                style = MaterialTheme.typography.bodySmall, color = ZenithOnSurfaceVariant
            )
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvPillButton(Icons.Filled.Close, onDismiss, Modifier.focusRequester(closeFocus), label = "Закрыть")
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        closeFocus.safeRequestFocus()
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
private fun ProgramDescriptionPanel(cell: Pair<EpgChannelRow, EpgSlot.ProgramSlot>?) {
    // Высота фиксирована: панель не прыгает и не сдвигает сетку при перемещении фокуса.
    Column(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(ZenithShapeMedium)
            .background(ZenithSurface.copy(alpha = 0.85f))
            .padding(ZenithDimens.paddingM)
    ) {
        if (cell == null) {
            Text("Выберите передачу — здесь появится описание", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.5f))
            return@Column
        }
        val (row, slot) = cell
        val program = slot.program
        Text(program.title, style = MaterialTheme.typography.titleSmall, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val action = when {
            slot.isLive && row.channel.streamCount > 0 -> " · OK — смотреть"
            slot.isCatchupAvailable -> " · OK — смотреть запись"
            else -> ""
        }
        Text(
            "${row.channel.canonicalName} · ${formatHm(program.startTimeMillis)}–${formatHm(program.endTimeMillis)}" +
                (program.category?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + action,
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (!program.description.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(program.description, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun EpgChannelRowView(
    row: EpgChannelRow,
    onPlay: () -> Unit,
    onPlayCatchup: (Long, Long) -> Unit,
    onFocusProgram: (EpgSlot.ProgramSlot) -> Unit,
    onInfo: (EpgSlot.ProgramSlot) -> Unit
) {
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
                        onClick = {
                            when {
                                slot.isLive && row.channel.streamCount > 0 -> onPlay()
                                slot.isCatchupAvailable -> onPlayCatchup(slot.program.startTimeMillis, slot.program.endTimeMillis)
                                else -> onInfo(slot)
                            }
                        },
                        onFocusProgram = onFocusProgram
                    )
                }
            }
        }
    }
}

/** Все ячейки фокусируются (как нажимаются на телефоне): описание видно у любой передачи, не только у доступных. */
@Composable
private fun ProgramCell(modifier: Modifier, slot: EpgSlot.ProgramSlot, onClick: () -> Unit, onFocusProgram: (EpgSlot.ProgramSlot) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val base = when {
        slot.isLive -> accent.copy(alpha = 0.30f)
        slot.isCatchupAvailable -> ZenithSecondary.copy(alpha = 0.22f)
        else -> ZenithSurfaceVariant
    }
    Box(
        modifier
            .clip(ZenithShapeSmall)
            .background(if (isFocused) base.compositeOver(accent.copy(alpha = 0.30f)) else base)
            .border(if (isFocused) 2.dp else 0.dp, if (isFocused) accent else Color.Transparent, ZenithShapeSmall)
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onFocusProgram(slot) }
            .clickable(onClick = onClick)
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
                Text(formatHm(slot.program.startTimeMillis), style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.6f))
                if (slot.isCatchupAvailable) {
                    Spacer(Modifier.width(4.dp))
                    Text("⟲", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}
