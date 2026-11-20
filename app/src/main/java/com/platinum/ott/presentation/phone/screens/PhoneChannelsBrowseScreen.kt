package com.platinum.ott.presentation.phone.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelCategoryFilter
import com.platinum.ott.data.repository.ChannelNameMatcher
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.presentation.screens.channels.ChannelsUiState
import com.platinum.ott.presentation.screens.channels.ChannelsViewModel
import com.platinum.ott.presentation.screens.channels.DeleteChannelConfirmDialog
import com.platinum.ott.presentation.screens.channels.RenameChannelDialog
import com.platinum.ott.ui.theme.CardSection
import com.platinum.ott.ui.theme.CardStyle
import com.platinum.ott.ui.theme.LocalAppearance
import kotlinx.coroutines.delay

/**
 * Телефон: «Каналы» и редактор — одно окно. Список один и тот же; «Править» — режим этого же списка (строки ведут
 * в редактор, скрытые каналы бледные), а не отдельный экран, поэтому позиция прокрутки и выбранная категория сохраняются.
 *
 *  • Тап — смотреть. Долгое нажатие — шторка «Канал»: избранное, телепрограмма, «Править этот канал».
 *  • «Править этот канал» включает режим правки, прокручивает список к этому каналу, подсвечивает его и сразу открывает редактор.
 *  • В режиме правки: тап по каналу — редактор (положение, номер, название, категория, скрыть, объединить с похожим,
 *    проверка, удаление); долгое нажатие на чип категории — редактор категории (название, скрыть, порядок).
 *  • Категория (в том числе «★ Избранные») запоминается и общая с переключением каналов в плеере.
 *  • «Избранные» хранятся локально на этом телефоне (SharedPreferences), в синхронизацию не входят.
 *
 * [focusChannelId] — открыть сразу в режиме правки на этом канале (например, переход из другого места).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhoneChannelsBrowseScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onOpenEpgGrid: () -> Unit,
    onAddSourceClick: () -> Unit,
    onOpenSources: () -> Unit,
    initialEdit: Boolean = false,
    focusChannelId: String? = null,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val epgBusy by viewModel.epgBusy.collectAsStateWithLifecycle()
    val epgVersion by viewModel.epgVersion.collectAsStateWithLifecycle()
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val hiddenCategoryNames by viewModel.hiddenCategories.collectAsStateWithLifecycle()
    val categoryOrder by viewModel.categoryOrder.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ChannelCategoryFilter.PREFS_PHONE, Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(prefs.getStringSet(ChannelCategoryFilter.KEY_FAVS, emptySet()).orEmpty().toSet()) }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet(ChannelCategoryFilter.KEY_FAVS, favorites).apply()
    }

    val allChannels = remember(uiState) {
        (uiState as? ChannelsUiState.Success)?.channels.orEmpty().sortedBy { it.channelNumber }.distinctBy { it.id }
    }
    var editMode by rememberSaveable { mutableStateOf(initialEdit || focusChannelId != null) }
    // Обычный режим — только видимые каналы; «Править» — все, скрытые приглушены.
    val subscribedChannels = remember(allChannels, editMode) { if (editMode) allChannels else allChannels.filter { it.isVisible } }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    // Выбранная категория запоминается — после просмотра канала экран возвращается в неё; она же — категория
    // переключения каналов в плеере.
    var selectedCategoryRaw by remember { mutableStateOf(prefs.getString(ChannelCategoryFilter.KEY_CATEGORY, ChannelCategoryFilter.ALL) ?: ChannelCategoryFilter.ALL) }
    LaunchedEffect(selectedCategoryRaw) { prefs.edit().putString(ChannelCategoryFilter.KEY_CATEGORY, selectedCategoryRaw).apply() }

    val categories = remember(subscribedChannels, categoryOrder) { ChannelCategoryFilter.categoriesOf(subscribedChannels, categoryOrder) }
    // Запомненная категория могла исчезнуть — тогда «Все»; запись не затираем. «Избранные» в режиме правки не показываются.
    val selectedCategory = ChannelCategoryFilter.resolve(selectedCategoryRaw, categories, allowFav = !editMode)
    val visibleChannels = remember(subscribedChannels, selectedCategory, searchQuery, favorites) {
        subscribedChannels
            .filter { ChannelCategoryFilter.matches(selectedCategory, favorites, it) }
            .filter { ChannelNameMatcher.matchesQuery(it.canonicalName, searchQuery) }
    }

    // ---- состояния окон
    var actionSheetId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryEditorName by rememberSaveable { mutableStateOf<String?>(null) }
    var showHiddenCategories by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ChannelUiItem?>(null) }

    // ---- переход к каналу: прокрутить к нему, подсветить, при необходимости открыть редактор
    var pendingScrollId by rememberSaveable { mutableStateOf(focusChannelId) }
    var pendingOpenEditor by rememberSaveable { mutableStateOf(focusChannelId != null) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    var anchorId by remember { mutableStateOf<String?>(null) }

    val appearance = LocalAppearance.current
    val chStyle = appearance.styleOf(CardSection.CHANNELS, false)
    val useGrid = !editMode && chStyle != CardStyle.LIST
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    /** Переключение режима с сохранением места: первый видимый канал остаётся на месте после смены списка. */
    fun setEdit(on: Boolean) {
        val index = if (useGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
        anchorId = visibleChannels.getOrNull(index)?.id
        editMode = on
        editorId = null
    }
    LaunchedEffect(editMode) {
        val id = anchorId ?: return@LaunchedEffect
        anchorId = null
        val idx = visibleChannels.indexOfFirst { it.id == id }
        if (idx >= 0) { if (useGrid) gridState.scrollToItem(idx) else listState.scrollToItem(idx) }
    }
    LaunchedEffect(pendingScrollId, pendingOpenEditor, editMode, visibleChannels, allChannels) {
        val id = pendingScrollId ?: return@LaunchedEffect
        if (allChannels.isEmpty()) return@LaunchedEffect // каналы ещё грузятся
        val channel = allChannels.firstOrNull { it.id == id }
        if (channel == null) { pendingScrollId = null; pendingOpenEditor = false; return@LaunchedEffect }
        // Редактор открывается в режиме правки: сначала включаем его и ждём, пока список перестроится (появятся скрытые).
        if (pendingOpenEditor && !editMode) { editMode = true; return@LaunchedEffect }
        val idx = visibleChannels.indexOfFirst { it.id == id }
        if (idx < 0) {
            // Канала нет в показанном списке (другая категория или поиск) — показываем «Все» без поиска и ждём перестроения.
            if (searchQuery.isNotEmpty()) { searchQuery = ""; return@LaunchedEffect }
            if (selectedCategory != ChannelCategoryFilter.ALL) { selectedCategoryRaw = ChannelCategoryFilter.ALL; return@LaunchedEffect }
            pendingScrollId = null; pendingOpenEditor = false // скрыт и режим правки выключен — переходить некуда
            return@LaunchedEffect
        }
        if (useGrid) gridState.scrollToItem(idx) else listState.scrollToItem(idx)
        highlightId = id
        if (pendingOpenEditor) editorId = id
        pendingScrollId = null; pendingOpenEditor = false
    }
    LaunchedEffect(highlightId) { if (highlightId != null) { delay(1800); highlightId = null } }
    BackHandler(enabled = editMode) { setEdit(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (editMode) "Править каналы" else "Каналы") },
                    navigationIcon = { IconButton(onClick = { if (editMode) setEdit(false) else onBackPressed() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
                    actions = {
                        IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) { Icon(Icons.Filled.Search, contentDescription = "Поиск") }
                        if (!editMode) {
                            IconButton(
                                enabled = !epgBusy,
                                onClick = { viewModel.refreshEpg { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() } }
                            ) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить программу") }
                        } else {
                            TextButton(onClick = onOpenSources) { Text("Источники") }
                            IconButton(onClick = { viewModel.checkAll() }, enabled = !isChecking) {
                                if (isChecking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Filled.Refresh, contentDescription = "Проверить все каналы")
                            }
                        }
                        IconButton(onClick = { setEdit(!editMode) }) {
                            if (editMode) Icon(Icons.Filled.Check, contentDescription = "Готово")
                            else Icon(Icons.Filled.Edit, contentDescription = "Править")
                        }
                    }
                )
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск канала (можно латиницей или кириллицей)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS)
                    )
                }
                if (subscribedChannels.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS),
                        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                    ) {
                        item("all") { CategoryChip("Все", selectedCategory == ChannelCategoryFilter.ALL) { selectedCategoryRaw = ChannelCategoryFilter.ALL } }
                        if (!editMode) item("fav") { CategoryChip("★ Избранные", selectedCategory == ChannelCategoryFilter.FAV) { selectedCategoryRaw = ChannelCategoryFilter.FAV } }
                        items(categories, key = { "c_" + it.name }) { c ->
                            CategoryChip(
                                title = if (editMode && c.allHidden) "${c.name} (скрыта)" else c.name,
                                selected = selectedCategory == c.name,
                                onClick = { selectedCategoryRaw = c.name },
                                onLongClick = if (editMode) ({ categoryEditorName = c.name }) else null
                            )
                        }
                        if (editMode && hiddenCategoryNames.isNotEmpty()) {
                            item("hidden") { CategoryChip("Скрытые (${hiddenCategoryNames.size})", false, onClick = { showHiddenCategories = true }) }
                        }
                    }
                }
                if (editMode) {
                    Text(
                        "Нажмите на канал, чтобы изменить. Долгое нажатие на категорию — её настройки. Скрытые каналы бледные.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS)
                    )
                }
            }
        }
    ) { padding ->
        when {
            subscribedChannels.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                    Text(
                        if (allChannels.isEmpty()) "Каналов пока нет — добавьте плейлист или панель" else "Все каналы скрыты — нажмите карандаш, чтобы показать",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = ZenithDimens.paddingXL)
                    )
                    if (allChannels.isEmpty()) Button(onClick = onAddSourceClick) { Text("Добавить источник") }
                }
            }
            visibleChannels.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    if (selectedCategory == ChannelCategoryFilter.FAV && searchQuery.isBlank()) "Избранных каналов пока нет.\nДолгое нажатие на канал — «В избранное»." else "Ничего не найдено",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(ZenithDimens.paddingXL)
                )
            }
            editMode -> {
                LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = ZenithDimens.paddingL)) {
                    items(visibleChannels, key = { it.id }) { ch ->
                        EditChannelRow(ch, highlight = ch.id == highlightId, onClick = { editorId = ch.id })
                    }
                }
            }
            else -> {
                // Форма — настройка «Внешний вид → Карточки: Каналы»: список (как было), плитка или широкая плитка.
                @Composable
                fun RowFor(ch: ChannelUiItem) {
                    ChannelRow(
                        item = ch,
                        style = chStyle,
                        isFavorite = ch.id in favorites,
                        epgVersion = epgVersion,
                        viewModel = viewModel,
                        highlight = ch.id == highlightId,
                        onClick = { onPlayChannel(ch.id) },
                        onLongClick = { actionSheetId = ch.id }
                    )
                }
                if (chStyle == CardStyle.LIST) {
                    LazyColumn(Modifier.padding(padding).fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = ZenithDimens.paddingL)) {
                        items(visibleChannels, key = { it.id }) { ch -> RowFor(ch) }
                    }
                } else {
                    val minCell = (if (chStyle == CardStyle.WIDE) 240.dp else 104.dp) * appearance.sizeMultiplier
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minCell),
                        state = gridState,
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(start = ZenithDimens.paddingS, end = ZenithDimens.paddingS, bottom = ZenithDimens.paddingL),
                        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                        verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                    ) {
                        gridItems(visibleChannels, key = { it.id }) { ch -> RowFor(ch) }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- шторка «Канал» (долгое нажатие)
    actionSheetId?.let { id ->
        val ch = allChannels.firstOrNull { it.id == id }
        if (ch == null) LaunchedEffect(id) { actionSheetId = null } else {
            ModalBottomSheet(onDismissRequest = { actionSheetId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(Modifier.fillMaxWidth().padding(bottom = ZenithDimens.paddingL)) {
                    Text(ch.canonicalName, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = ZenithDimens.paddingM))
                    SheetAction(if (ch.id in favorites) "Убрать из избранного" else "В избранное") { toggleFavorite(ch.id); actionSheetId = null }
                    SheetAction("Телепрограмма") { actionSheetId = null; onOpenEpgGrid() }
                    SheetAction("Править этот канал") { actionSheetId = null; pendingScrollId = ch.id; pendingOpenEditor = true }
                }
            }
        }
    }

    // ---------------------------------------------------------------- редактор канала
    editorId?.let { id ->
        val ch = allChannels.firstOrNull { it.id == id }
        if (ch == null) LaunchedEffect(id) { if (allChannels.isNotEmpty()) editorId = null } else {
            val viewIds = visibleChannels.map { it.id }
            val index = viewIds.indexOf(ch.id)
            PhoneChannelEditorSheet(
                channel = ch,
                position = if (index >= 0) index + 1 else 0,
                total = if (index >= 0) viewIds.size else 0,
                categories = categories.map { it.name },
                allChannels = allChannels,
                onMoveUp = { if (index > 0) viewModel.reorder(viewIds, ch.id, index - 1) },
                onMoveDown = { if (index in 0 until viewIds.lastIndex) viewModel.reorder(viewIds, ch.id, index + 1) },
                onSetNumber = { viewModel.setNumber(ch.id, it) },
                onRename = { renameTarget = ch },
                onMoveToCategory = { name ->
                    viewModel.moveToCategory(ch.id, name)
                    // Следуем за каналом: показываем категорию, куда он ушёл.
                    selectedCategoryRaw = name ?: ChannelCategoryFilter.NO_CATEGORY
                    searchQuery = ""
                },
                onToggleHidden = { viewModel.setHidden(ch.id, !ch.isHidden) },
                onMerge = { target -> viewModel.merge(ch.id, target.id); editorId = null; pendingScrollId = target.id },
                onCheck = { viewModel.checkOne(ch.id) },
                onPlay = { editorId = null; onPlayChannel(ch.id) },
                onDelete = { deleteTarget = ch },
                onDismiss = { editorId = null }
            )
        }
    }

    // ---------------------------------------------------------------- редактор категории и скрытые категории
    categoryEditorName?.let { name ->
        val info = categories.firstOrNull { it.name == name }
        if (info == null) LaunchedEffect(name) { categoryEditorName = null } else {
            PhoneCategoryEditorSheet(
                name = name,
                channelCount = info.count,
                hidden = info.allHidden,
                index = categories.indexOfFirst { it.name == name },
                total = categories.size,
                onRename = { newName ->
                    viewModel.renameCategory(name, newName)
                    if (selectedCategoryRaw == name) selectedCategoryRaw = newName
                    categoryEditorName = null
                },
                onToggleHidden = { viewModel.setCategoryHidden(if (name == ChannelCategoryFilter.NO_CATEGORY) null else name, !info.allHidden) },
                onMove = { to -> viewModel.moveCategory(categories.map { it.name }, name, to) },
                onDismiss = { categoryEditorName = null }
            )
        }
    }
    if (showHiddenCategories) {
        ModalBottomSheet(onDismissRequest = { showHiddenCategories = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM).padding(bottom = ZenithDimens.paddingL)) {
                Text("Скрытые категории", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(ZenithDimens.paddingS))
                if (hiddenCategoryNames.isEmpty()) Text("Скрытых категорий нет")
                hiddenCategoryNames.forEach { name ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { viewModel.setCategoryHidden(if (name == ChannelCategoryFilter.NO_CATEGORY) null else name, false) }) { Text("Показать") }
                    }
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
    deleteTarget?.let { target ->
        DeleteChannelConfirmDialog(
            channel = target,
            onConfirm = { viewModel.delete(target.id); deleteTarget = null; editorId = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun SheetAction(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Строка канала в режиме правки: номер, логотип с точкой проверки, название, состояние, значок «изменить». */
@Composable
private fun EditChannelRow(item: ChannelUiItem, highlight: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .alpha(if (item.isVisible) 1f else 0.5f)
            .padding(horizontal = ZenithDimens.paddingM, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${item.channelNumber}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.width(34.dp))
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) {
            if (item.logo != null) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
            else Text(item.canonicalName.take(2).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Box(
                Modifier.align(Alignment.BottomEnd).padding(3.dp).size(8.dp).clip(CircleShape).background(
                    when (item.healthStatus) {
                        "alive" -> Color(0xFF4CAF50)
                        "dead" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            )
        }
        Spacer(Modifier.width(ZenithDimens.paddingM))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.canonicalName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                item.regionHint?.let { Text(" ($it)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
            }
            Text(
                buildString {
                    append(if (item.streamCount == 1) "1 источник" else "${item.streamCount} источника(ов)")
                    append(" • ").append(ChannelCategoryFilter.categoryOf(item))
                    if (item.isMergeCandidate) append(" • не сопоставлен")
                    if (item.isHidden) append(" • скрыт") else if (item.categoryHidden) append(" • категория скрыта")
                },
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (item.isMergeCandidate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Icon(Icons.Filled.Edit, contentDescription = "Изменить", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    item: ChannelUiItem,
    style: CardStyle,
    isFavorite: Boolean,
    epgVersion: Int,
    viewModel: ChannelsViewModel,
    highlight: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Программа грузится только для реально показанных строк (LazyColumn компонует лишь видимые)
    // и обновляется раз в минуту.
    var now by remember(item.id) { mutableStateOf<Pair<String, Float>?>(null) }
    LaunchedEffect(item.id, epgVersion) {
        while (true) {
            now = viewModel.nowPlaying(item.id)
            kotlinx.coroutines.delay(60_000)
        }
    }
    Box(Modifier.background(if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)) {
        if (style != CardStyle.LIST) {
            // Плитка (логотип сверху) или широкая плитка (логотип слева) — карточка с фоном.
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            val tileModifier = Modifier.fillMaxWidth().clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(ZenithDimens.paddingS)
            val program = now
            if (style == CardStyle.WIDE) {
                Row(tileModifier, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(72.dp).height(46.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) {
                        if (item.logo != null) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.8f))
                        else Text(item.canonicalName.take(2).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    Column(Modifier.weight(1f)) {
                        Text(item.canonicalName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (program != null) {
                            Text(program.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { program.second }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
                        }
                    }
                    if (isFavorite) Icon(Icons.Filled.Star, "В избранном", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = ZenithDimens.paddingXS).size(18.dp))
                }
            } else {
                Column(tileModifier) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) {
                        if (item.logo != null) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
                        else Text(item.canonicalName.take(2).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        if (isFavorite) Icon(Icons.Filled.Star, "В избранном", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp))
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingXS))
                    Text(item.canonicalName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (program != null) {
                        Text(program.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { program.second }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
                    }
                }
            }
        } else Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = ZenithDimens.paddingM, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${item.channelNumber}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.width(34.dp))
            Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f)), Alignment.Center) {
                if (item.logo != null) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
                else Text(item.canonicalName.take(2).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Spacer(Modifier.width(ZenithDimens.paddingM))
            Column(Modifier.weight(1f)) {
                Text(item.canonicalName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val program = now
                if (program != null) {
                    Text(program.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { program.second }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
                }
            }
            if (isFavorite) Icon(Icons.Filled.Star, "В избранном", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = ZenithDimens.paddingS).size(18.dp))
        }
    }
}

/** Чип категории; долгое нажатие (если задано) открывает редактор категории. Свой чип, т.к. у FilterChip нет onLongClick. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Text(
            title, maxLines = 1, style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
