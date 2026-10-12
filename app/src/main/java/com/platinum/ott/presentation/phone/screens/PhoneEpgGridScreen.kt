package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.epg.EpgChannelRow
import com.platinum.ott.presentation.screens.epg.EpgGridUiState
import com.platinum.ott.presentation.screens.epg.EpgGridViewModel
import com.platinum.ott.presentation.screens.epg.EpgSearchResult
import com.platinum.ott.presentation.screens.epg.EpgSlot
import com.platinum.ott.presentation.screens.epg.formatHm
import com.platinum.ott.ui.theme.ZenithShapeSmall

private val CHANNEL_COLUMN_WIDTH = 96.dp
private val ROW_HEIGHT = 56.dp
private const val ALL_CATEGORY = "__all__"

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
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    // ФИКС (аудит): тот же паттерн, что в TV-версии (EpgGridScreen.kt).
    LaunchedEffect(Unit) { viewModel.load() }

    // «Вариант C» (как на TV): категории, линия «сейчас», описание передачи
    // и поиск по названию. Отличия под телефон: категории — ряд чипов
    // вместо сайдбара, описание — нижний лист по нажатию на ячейку (фокуса
    // пультом нет), поиск — в верхней панели.
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORY) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var openedCell by remember { mutableStateOf<Pair<EpgChannelRow, EpgSlot.ProgramSlot>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { query = it; viewModel.search(it) },
                            singleLine = true,
                            placeholder = { Text("Название передачи") },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else Text("Программа передач")
                },
                navigationIcon = { IconButton(onClick = onBackPressed) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    IconButton(onClick = {
                        if (searchOpen) { query = ""; viewModel.search("") }
                        searchOpen = !searchOpen
                    }) {
                        if (searchOpen) Icon(Icons.Filled.Close, contentDescription = "Закрыть поиск")
                        else Icon(Icons.Filled.Search, contentDescription = "Поиск по передачам")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (searchOpen && query.isNotBlank()) {
                PhoneSearchResults(
                    results = searchResults,
                    onResultClick = { result ->
                        viewModel.jumpToProgram(result.program.startTimeMillis)
                        query = ""; viewModel.search(""); searchOpen = false
                    }
                )
                return@Column
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.shiftWindow(forward = false) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Раньше на 30 минут") }
                TextButton(onClick = { viewModel.jumpToNow() }) { Text("Сейчас") }
                IconButton(onClick = { viewModel.shiftWindow(forward = true) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Позже на 30 минут") }
            }

            when (val state = uiState) {
                is EpgGridUiState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is EpgGridUiState.NoChannels -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Нет подписанных каналов — подпишитесь на них в разделе \"Каналы\"",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                is EpgGridUiState.Success -> {
                    val categoryOf = { row: EpgChannelRow -> row.channel.category?.ifBlank { null } ?: "Без категории" }
                    val categories = remember(state.rows) {
                        state.rows.groupBy(categoryOf).mapValues { it.value.size }.toList().sortedByDescending { it.second }
                    }
                    val visibleRows = remember(state.rows, selectedCategory) {
                        if (selectedCategory == ALL_CATEGORY) state.rows else state.rows.filter { categoryOf(it) == selectedCategory }
                    }

                    // Категории — ряд чипов; «Все» первым.
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ZenithDimens.paddingM),
                        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                    ) {
                        item {
                            FilterChip(selected = selectedCategory == ALL_CATEGORY, onClick = { selectedCategory = ALL_CATEGORY }, label = { Text("Все · ${state.rows.size}") })
                        }
                        items(categories, key = { it.first }) { (category, count) ->
                            FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text("$category · $count") })
                        }
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingXS))

                    // Нет ни одной передачи в видимом окне (EPG не загружен или окно
                    // сдвинуто за пределы данных) — вместо пустой сетки объясняем, что делать.
                    val hasPrograms = remember(visibleRows) { visibleRows.any { r -> r.slots.any { it is EpgSlot.ProgramSlot } } }
                    if (!hasPrograms) {
                        Box(Modifier.fillMaxWidth().weight(1f).padding(ZenithDimens.paddingL), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Нет данных программы на это время", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(ZenithDimens.paddingXS))
                                Text(
                                    "Попробуйте кнопку «Сейчас» или другую категорию. Если пусто везде — обновите источник в Настройках: у провайдера может не быть программы передач.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZenithOnSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        return@Column
                    }

                    PhoneRulerRow(windowStart = state.windowStart, windowEnd = state.windowEnd)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(visibleRows, key = { it.channel.id }) { row ->
                                PhoneEpgChannelRowView(row = row, onProgramClick = { slot -> openedCell = row to slot })
                            }
                        }
                        // Вертикальная линия «сейчас»: доля окна → смещение внутри колонки
                        // программ (после колонки каналов), скрыта, если «сейчас» вне окна.
                        val now = System.currentTimeMillis()
                        if (now in state.windowStart until state.windowEnd) {
                            val fraction = (now - state.windowStart).toFloat() / (state.windowEnd - state.windowStart)
                            val gridWidth: Dp = maxWidth - ZenithDimens.paddingM * 2 - CHANNEL_COLUMN_WIDTH
                            Box(
                                Modifier
                                    .offset(x = ZenithDimens.paddingM + CHANNEL_COLUMN_WIDTH + gridWidth * fraction)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }

    // Описание передачи — нижний лист по нажатию на ячейку.
    openedCell?.let { (row, slot) ->
        ModalBottomSheet(onDismissRequest = { openedCell = null }) {
            Column(Modifier.padding(horizontal = ZenithDimens.paddingM).padding(bottom = ZenithDimens.paddingL).navigationBarsPadding()) {
                Text(slot.program.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(ZenithDimens.paddingXS))
                Text(
                    "${row.channel.canonicalName} · ${formatHm(slot.program.startTimeMillis)}–${formatHm(slot.program.endTimeMillis)}" +
                        (slot.program.category?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenithOnSurfaceVariant
                )
                slot.program.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(ZenithDimens.paddingM))
                when {
                    slot.isLive && row.channel.streamCount > 0 ->
                        Button(onClick = { openedCell = null; onPlayChannel(row.channel.id) }) { Text("Смотреть") }
                    slot.isCatchupAvailable ->
                        Button(onClick = { openedCell = null; onPlayCatchup(row.channel.id, slot.program.startTimeMillis, slot.program.endTimeMillis) }) { Text("Смотреть запись") }
                    else -> Text(
                        if (slot.program.startTimeMillis > System.currentTimeMillis()) "Эта передача ещё не началась" else "Запись этой передачи недоступна",
                        style = MaterialTheme.typography.bodySmall, color = ZenithOnSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PhoneSearchResults(results: List<EpgSearchResult>, onResultClick: (EpgSearchResult) -> Unit) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        return
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(ZenithDimens.paddingM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)) {
        items(results, key = { "${it.channel.id}:${it.program.startTimeMillis}" }) { result ->
            Row(
                Modifier.fillMaxWidth().clip(ZenithShapeSmall).background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onResultClick(result) }.padding(ZenithDimens.paddingM),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (result.channel.logo != null) {
                    AsyncImage(model = result.channel.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                }
                Column(Modifier.weight(1f)) {
                    Text(result.program.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${result.channel.canonicalName} · ${formatHm(result.program.startTimeMillis)}–${formatHm(result.program.endTimeMillis)}",
                        style = MaterialTheme.typography.labelSmall, color = ZenithOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
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
private fun PhoneEpgChannelRowView(row: EpgChannelRow, onProgramClick: (EpgSlot.ProgramSlot) -> Unit) {
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
                        onClick = { onProgramClick(slot) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneProgramCell(modifier: Modifier, slot: EpgSlot.ProgramSlot, onClick: () -> Unit) {
    Box(
        modifier
            .clip(ZenithShapeSmall)
            .background(
                when {
                    slot.isLive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                    slot.isCatchupAvailable -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(slot.program.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatHm(slot.program.startTimeMillis), style = MaterialTheme.typography.labelSmall, color = ZenithOnSurfaceVariant)
                // См. тот же комментарий в EpgGridScreen.kt (TV) — единственная
                // видимая подсказка, что прошедшую программу можно открыть
                // из архива.
                if (slot.isCatchupAvailable) {
                    Spacer(Modifier.width(4.dp))
                    Text("⟲", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurfaceVariant)
                }
            }
        }
    }
}
