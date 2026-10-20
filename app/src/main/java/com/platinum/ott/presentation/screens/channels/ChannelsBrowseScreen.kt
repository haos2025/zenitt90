package com.platinum.ott.presentation.screens.channels

import android.content.Context
import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.safeRequestFocus
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.ZenithShapeSmall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALL_CATEGORY = "__all__"
private const val FAV_CATEGORY = "__fav__"
private const val PREFS = "tv_channels"
private const val KEY_FAVS = "favorite_ids"
private const val KEY_CATEGORY = "last_category"

/**
 * ТВ: три колонки «как в TiviMate» — слева категории («Все», «★ Избранные», группы), в центре список
 * каналов (номер, логотип, название, «сейчас в эфире» с полосой прогресса), справа панель выделенного
 * канала (логотип, сейчас/далее, описание). Категория выбирается фокусом, OK на канале — смотреть,
 * долгое OK — добавить/убрать из избранного (хранится локально на устройстве). Всё административное —
 * ChannelsManageScreen.kt.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsBrowseScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    // Режим «Править» (план 07, п. 12): вместо отдельного экрана «Управление».
    // OK по каналу — меню действий (переименовать, слить, удалить, проверить). Подписок на каналы нет.
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    // Пустой экран («Каналов пока нет») ведёт к добавлению источника; в «Править» — быстрый переход к списку источников.
    onAddSourceClick: () -> Unit,
    onOpenSources: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val epgBusy by viewModel.epgBusy.collectAsStateWithLifecycle()
    val epgVersion by viewModel.epgVersion.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(prefs.getStringSet(KEY_FAVS, emptySet()).orEmpty().toSet()) }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet(KEY_FAVS, favorites).apply()
    }

    val allChannels = remember(uiState) {
        (uiState as? ChannelsUiState.Success)?.channels.orEmpty().sortedBy { it.channelNumber }
    }
    // Подписок на каналы нет. В обычном режиме — только видимые (не скрытые сами и не в скрытой категории);
    // в «Править» — все, скрытые приглушены и подписаны «скрыт».
    val subscribedChannels = remember(allChannels, editMode) { if (editMode) allChannels else allChannels.filter { it.isVisible } }
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    var actionsTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var renameTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var mergeTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    // «Назад» в режиме «Править» сначала выходит из режима.
    BackHandler(enabled = editMode) { onEditModeChange(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Выбранная категория запоминается (в том числе «Избранные») — после просмотра канала экран возвращается в неё.
    var selectedCategoryRaw by remember { mutableStateOf(prefs.getString(KEY_CATEGORY, ALL_CATEGORY) ?: ALL_CATEGORY) }
    LaunchedEffect(selectedCategoryRaw) { prefs.edit().putString(KEY_CATEGORY, selectedCategoryRaw).apply() }
    var categoryTarget by remember { mutableStateOf<String?>(null) }
    val firstRowRequester = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<String?>(null) }

    val categoryCounts = remember(subscribedChannels) {
        subscribedChannels.groupBy { it.category?.trim()?.ifBlank { null } ?: "Без категории" }
            .map { (name, list) -> Triple(name, list.size, list.all { it.categoryHidden }) }
            .sortedByDescending { it.second }
    }
    // Запомненная категория могла исчезнуть (источник удалён, категория скрыта) — тогда «Все каналы».
    // Саму запись не затираем: категория может вернуться.
    val selectedCategory = when {
        selectedCategoryRaw == ALL_CATEGORY -> ALL_CATEGORY
        selectedCategoryRaw == FAV_CATEGORY -> FAV_CATEGORY
        categoryCounts.any { it.first == selectedCategoryRaw } -> selectedCategoryRaw
        else -> ALL_CATEGORY
    }
    val visibleChannels = remember(subscribedChannels, selectedCategory, searchQuery, favorites) {
        subscribedChannels
            .filter {
                when (selectedCategory) {
                    ALL_CATEGORY -> true
                    FAV_CATEGORY -> it.id in favorites
                    else -> (it.category?.trim()?.ifBlank { null } ?: "Без категории") == selectedCategory
                }
            }
            .filter { searchQuery.isBlank() || it.canonicalName.contains(searchQuery, ignoreCase = true) }
    }

    val hasVisible = visibleChannels.isNotEmpty()
    LaunchedEffect(hasVisible) {
        if (hasVisible && !initialFocusDone) {
            withFrameNanos { }
            firstRowRequester.safeRequestFocus()
            initialFocusDone = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Каналы", style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
            Spacer(Modifier.weight(1f))
            ChannelSearchField(query = searchQuery, onChange = { searchQuery = it }, modifier = Modifier.width(200.dp))
            Spacer(Modifier.width(ZenithDimens.paddingS))
            if (editMode) {
                OutlinedButton(
                    scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
                    enabled = !isChecking,
                    onClick = { viewModel.checkAll() }
                ) { Text(if (isChecking) "Проверка…" else "Проверить все") }
                Spacer(Modifier.width(ZenithDimens.paddingS))
                OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = onOpenSources) { Text("Источники") }
            } else {
                OutlinedButton(
                    scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
                    enabled = !epgBusy,
                    onClick = { viewModel.refreshEpg { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() } }
                ) { Text(if (epgBusy) "Загрузка программы…" else "Обновить программу") }
            }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = { onEditModeChange(!editMode) }) {
                Text(if (editMode) "Готово" else "Править")
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingL))

        if (subscribedChannels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                    Text(
                        if (allChannels.isEmpty()) "Каналов пока нет — добавьте плейлист или панель" else "Все каналы скрыты — нажмите «Править», чтобы показать",
                        color = ZenithOnSurface.copy(alpha = 0.6f)
                    )
                    if (allChannels.isEmpty()) {
                        OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = onAddSourceClick) { Text("Добавить источник") }
                    }
                }
            }
        } else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL)) {
                // 1. Категории.
                LazyColumn(modifier = Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)) {
                    item("all") { CategoryRow("Все каналы", subscribedChannels.size, selectedCategory == ALL_CATEGORY) { selectedCategoryRaw = ALL_CATEGORY } }
                    item("fav") { CategoryRow("★ Избранные", favorites.count { id -> subscribedChannels.any { it.id == id } }, selectedCategory == FAV_CATEGORY) { selectedCategoryRaw = FAV_CATEGORY } }
                    lazyColumnItems(categoryCounts, key = { "c_" + it.first }) { (category, count, hidden) ->
                        // В «Править» OK по категории открывает меню «Скрыть/Показать категорию»; фокус по-прежнему выбирает её.
                        CategoryRow(
                            title = category, count = count, selected = selectedCategory == category,
                            hiddenMark = editMode && hidden,
                            onClick = if (editMode) { { categoryTarget = category } } else null
                        ) { selectedCategoryRaw = category }
                    }
                }
                // 2. Список каналов.
                if (visibleChannels.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            if (selectedCategory == FAV_CATEGORY && searchQuery.isBlank()) "Избранных каналов пока нет.\nУдерживайте OK на канале, чтобы добавить." else "Ничего не найдено",
                            color = ZenithOnSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Плитки (решение владельца для ТВ): сетка вместо списка.
                    // Ширина плитки — минимум 150 dp, число колонок считается само.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                    ) {
                        // В режиме «Править» ключ только по id: порядок стабилен (по номеру), и фокус
                        // не должен «прыгать», когда канал подписывается/отписывается.
                        gridItemsIndexed(visibleChannels, key = { i, c -> if (editMode) c.id else "${i}_${c.id}" }) { index, ch ->
                            ChannelTileTv(
                                item = ch,
                                isFavorite = !editMode && ch.id in favorites,
                                editMode = editMode,
                                epgVersion = epgVersion,
                                viewModel = viewModel,
                                onClick = { if (editMode) actionsTarget = ch else onPlayChannel(ch.id) },
                                onLongClick = { if (editMode) actionsTarget = ch else toggleFavorite(ch.id) },
                                onFocused = { focusedId = ch.id },
                                modifier = if (index == 0) Modifier.focusRequester(firstRowRequester) else Modifier
                            )
                        }
                    }
                }
                // 3. Панель выделенного канала.
                val panelChannel = visibleChannels.firstOrNull { it.id == focusedId } ?: visibleChannels.firstOrNull()
                ChannelPanel(panelChannel, viewModel, epgVersion, Modifier.width(180.dp).fillMaxHeight())
            }
        }
    }

    actionsTarget?.let { target ->
        ChannelActionsDialog(
            channel = target,
            onToggleHidden = { viewModel.setHidden(target.id, !target.isHidden); actionsTarget = null },
            onCheck = { viewModel.checkOne(target.id); actionsTarget = null },
            onRename = { renameTarget = target; actionsTarget = null },
            onMerge = { mergeTarget = target; actionsTarget = null },
            onDelete = { deleteTarget = target; actionsTarget = null },
            onDismiss = { actionsTarget = null }
        )
    }
    categoryTarget?.let { name ->
        val info = categoryCounts.firstOrNull { it.first == name }
        CategoryActionsDialog(
            category = name,
            isHidden = info?.third == true,
            channelCount = info?.second ?: 0,
            onToggleHidden = {
                viewModel.setCategoryHidden(if (name == "Без категории") null else name, info?.third != true)
                categoryTarget = null
            },
            onDismiss = { categoryTarget = null }
        )
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
            candidates = allChannels.filter { it.id != target.id },
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
private fun ChannelTileTv(
    item: ChannelUiItem,
    isFavorite: Boolean,
    editMode: Boolean,
    epgVersion: Int,
    viewModel: ChannelsViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Программа грузится только для реально скомпонованных (видимых) плиток, обновление раз в минуту.
    var now by remember(item.id) { mutableStateOf<Pair<String, Float>?>(null) }
    LaunchedEffect(item.id, epgVersion) {
        while (true) {
            now = viewModel.nowPlaying(item.id)
            kotlinx.coroutines.delay(60_000)
        }
    }
    val tileShape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(tileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = ZenithOnSurface.copy(alpha = 0.06f),
            focusedContainerColor = ZenithFocusContainerActive
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        // В режиме «Править» скрытые каналы приглушены.
        Column(Modifier.padding(ZenithDimens.paddingS).alpha(if (editMode && !item.isVisible) 0.45f else 1f)) {
            // Верх плитки: логотип на светлой подложке (тёмные логотипы читаются), номер и звезда по углам.
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)),
                Alignment.Center
            ) {
                if (item.logo != null) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
                else Text(item.canonicalName.take(2).uppercase(), color = ZenithOnSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium)
                Text("${item.channelNumber}", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                if (editMode && !item.isVisible) Text("скрыт", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                else if (isFavorite) Text("★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Spacer(Modifier.height(ZenithDimens.paddingXS))
            Text(item.canonicalName, style = MaterialTheme.typography.titleSmall, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val program = now
            if (program != null) {
                Text(program.first, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                ProgressLine(program.second)
            } else {
                Text("—", style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.3f), maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelPanel(channel: ChannelUiItem?, viewModel: ChannelsViewModel, epgVersion: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(ZenithShapeSmall).background(ZenithOnSurface.copy(alpha = 0.05f)).padding(ZenithDimens.paddingSM),
        verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
    ) {
        if (channel == null) return@Column
        var info by remember(channel.id) { mutableStateOf<ChannelsViewModel.NowNext?>(null) }
        LaunchedEffect(channel.id, epgVersion) { info = null; info = viewModel.nowNext(channel.id) }
        val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        ChannelLogo(channel, 96.dp)
        Text("${channel.channelNumber}  ·  ${channel.canonicalName}", style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val i = info
        if (i == null) {
            Text("Нет данных о программе передач", style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.5f))
        } else {
            Text("Сейчас · ${fmt.format(Date(i.startMs))}–${fmt.format(Date(i.endMs))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(i.title, style = MaterialTheme.typography.bodyLarge, color = ZenithOnSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            ProgressLine(i.progress)
            if (!i.description.isNullOrBlank()) {
                Text(i.description, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.65f), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (i.nextTitle != null) {
                Spacer(Modifier.height(ZenithDimens.paddingXS))
                Text("Далее", style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface.copy(alpha = 0.5f))
                Text(i.nextTitle, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChannelLogo(item: ChannelUiItem, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(ZenithOnSurface.copy(alpha = 0.08f)), Alignment.Center) {
        if (item.logo != null) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
        else Text(item.canonicalName.take(2).uppercase(), color = ZenithOnSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ProgressLine(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(ZenithOnSurface.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

/** Категория выбирается фокусом (быстрый просмотр без лишнего OK). */
@Composable
private fun CategoryRow(
    title: String, count: Int, selected: Boolean,
    hiddenMark: Boolean = false, onClick: (() -> Unit)? = null,
    onSelect: () -> Unit
) {
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
            .onFocusChanged { isFocused = it.isFocused; if (it.isFocused) onSelect() }
            .clickable(onClick = onClick ?: onSelect)
            .padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(if (hiddenMark) "$title (скрыта)" else title, style = MaterialTheme.typography.bodyMedium, color = if (selected) ZenithOnSurface else ZenithOnSurface.copy(alpha = if (hiddenMark) 0.4f else 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f))
    }
}

/**
 * Поиск по каналам: в покое — обычная плашка (фокусируется пультом, клавиатура НЕ
 * появляется сама); по OK переходит в режим ввода и показывает клавиатуру.
 * Тот же приём, что в SearchScreen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ChannelSearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "Поиск") {
    var isEditing by remember { mutableStateOf(false) }
    var gotFocus by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    val inputRequester = remember { FocusRequester() }
    val idleRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(isEditing) { if (!isEditing && wasEditing) idleRequester.safeRequestFocus() }

    if (!isEditing) {
        Surface(
            onClick = { gotFocus = false; wasEditing = true; isEditing = true },
            shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = ZenithOnSurface.copy(alpha = 0.08f),
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = modifier.focusRequester(idleRequester)
        ) {
            Text(
                if (query.isEmpty()) hint else query,
                style = TextStyle(if (query.isEmpty()) ZenithOnSurface.copy(alpha = 0.4f) else ZenithOnSurface, 16.sp),
                maxLines = 1,
                modifier = Modifier.padding(ZenithDimens.paddingM, ZenithDimens.paddingSM)
            )
        }
    } else {
        LaunchedEffect(Unit) { inputRequester.safeRequestFocus(); keyboard?.show() }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(ZenithOnSurface, 16.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { isEditing = false; keyboard?.hide() },
                onDone = { isEditing = false; keyboard?.hide() }
            ),
            modifier = modifier
                .focusRequester(inputRequester)
                .background(ZenithOnSurface.copy(alpha = 0.08f), ZenithShapeSmall)
                .padding(ZenithDimens.paddingM, ZenithDimens.paddingSM)
                .onFocusChanged {
                    if (it.isFocused) gotFocus = true
                    else if (gotFocus) { isEditing = false; keyboard?.hide() }
                },
            decorationBox = { inner -> inner() }
        )
    }
}
