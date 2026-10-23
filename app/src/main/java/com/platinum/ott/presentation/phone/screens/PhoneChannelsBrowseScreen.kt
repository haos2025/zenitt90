package com.platinum.ott.presentation.phone.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import com.platinum.ott.ui.theme.CardSection
import com.platinum.ott.ui.theme.CardStyle
import com.platinum.ott.ui.theme.LocalAppearance
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.presentation.screens.channels.ChannelsUiState
import com.platinum.ott.presentation.screens.channels.ChannelsViewModel
import com.platinum.ott.presentation.screens.channels.DeleteChannelConfirmDialog
import com.platinum.ott.presentation.screens.channels.MergeChannelDialog
import com.platinum.ott.presentation.screens.channels.RenameChannelDialog

private const val ALL_CATEGORY = "__all__"
private const val FAV_CATEGORY = "__fav__"
private const val PREFS = "phone_channels"
private const val KEY_FAVS = "favorite_ids"
private const val KEY_CATEGORY = "last_category"

/**
 * Телефон: список каналов «как в TiviMate» — строка = номер, логотип, название и
 * «сейчас в эфире» с полосой прогресса. Сверху поиск и чипы категорий («Все», «Избранные», группы).
 * Тап — смотреть; долгое нажатие — меню (избранное, телепрограмма).
 * «Избранные» хранятся локально на этом телефоне (SharedPreferences), в синхронизацию не входят.
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
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val epgBusy by viewModel.epgBusy.collectAsStateWithLifecycle()
    val epgVersion by viewModel.epgVersion.collectAsStateWithLifecycle()
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
    // Режим «Править» (вместо отдельного «Управления»). Вкладка «Источники» убрана (21.10.2026) — источники в Настройках.
    var editMode by rememberSaveable { mutableStateOf(initialEdit) }
    // Подписок нет. Обычный режим — только видимые каналы; «Править» — все, скрытые приглушены.
    val subscribedChannels = remember(allChannels, editMode) { if (editMode) allChannels else allChannels.filter { it.isVisible } }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    // Выбранная категория запоминается (в том числе «Избранные») — после просмотра канала экран возвращается в неё.
    var selectedCategoryRaw by remember { mutableStateOf(prefs.getString(KEY_CATEGORY, ALL_CATEGORY) ?: ALL_CATEGORY) }
    LaunchedEffect(selectedCategoryRaw) { prefs.edit().putString(KEY_CATEGORY, selectedCategoryRaw).apply() }
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val hiddenCategoryNames by viewModel.hiddenCategories.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var mergeTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    BackHandler(enabled = editMode) { editMode = false }

    val categories = remember(subscribedChannels) {
        subscribedChannels.groupBy { it.category?.trim()?.ifBlank { null } ?: "Без категории" }
            .map { (name, list) -> Triple(name, list.size, list.all { it.categoryHidden }) }
            .sortedByDescending { it.second }
    }
    // Запомненная категория могла исчезнуть (источник удалён, категория скрыта) — тогда «Все»; запись не затираем.
    // «Избранные» в режиме «Править» не показываются — там тоже «Все».
    val selectedCategory = when {
        selectedCategoryRaw == ALL_CATEGORY -> ALL_CATEGORY
        selectedCategoryRaw == FAV_CATEGORY -> if (editMode) ALL_CATEGORY else FAV_CATEGORY
        categories.any { it.first == selectedCategoryRaw } -> selectedCategoryRaw
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

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Каналы") },
                    navigationIcon = { IconButton(onClick = onBackPressed) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
                    actions = {
                        if (!editMode) {
                            IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) { Icon(Icons.Filled.Search, contentDescription = "Поиск") }
                            IconButton(
                                enabled = !epgBusy,
                                onClick = { viewModel.refreshEpg { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() } }
                            ) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить программу") }
                        }
                        if (editMode) {
                            IconButton(onClick = { viewModel.checkAll() }, enabled = !isChecking) {
                                if (isChecking) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Filled.Refresh, contentDescription = "Проверить все каналы")
                            }
                        }
                        IconButton(onClick = { editMode = !editMode; searchActive = false; searchQuery = "" }) {
                            if (editMode) Icon(Icons.Filled.Check, contentDescription = "Готово")
                            else Icon(Icons.Filled.Edit, contentDescription = "Править")
                        }
                    }
                )
                if (!editMode && searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск канала") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS)
                    )
                }
                if (subscribedChannels.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS),
                        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                    ) {
                        item("all") { CategoryChip("Все", selectedCategory == ALL_CATEGORY) { selectedCategoryRaw = ALL_CATEGORY } }
                        if (!editMode) item("fav") { CategoryChip("★ Избранные", selectedCategory == FAV_CATEGORY) { selectedCategoryRaw = FAV_CATEGORY } }
                        items(categories, key = { "c_" + it.first }) { (name, _, hidden) ->
                            CategoryChip(if (editMode && hidden) "$name (скрыта)" else name, selectedCategory == name) { selectedCategoryRaw = name }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            editMode -> {
                val selectedInfo = categories.firstOrNull { it.first == selectedCategory }
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(ZenithDimens.paddingM),
                    verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                ) {
                    item("sources") {
                        OutlinedButton(onClick = onOpenSources, modifier = Modifier.fillMaxWidth()) { Text("Источники") }
                    }
                    // Скрыть/показать выбранную категорию целиком (новые каналы в ней тоже скрыты).
                    if (selectedInfo != null) {
                        item("category_action") {
                            val hidden = selectedInfo.third
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Категория «${selectedInfo.first}»", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            if (hidden) "скрыта (каналов: ${selectedInfo.second})" else "каналов: ${selectedInfo.second}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    TextButton(onClick = {
                                        viewModel.setCategoryHidden(if (selectedInfo.first == "Без категории") null else selectedInfo.first, !hidden)
                                    }) { Text(if (hidden) "Показать" else "Скрыть категорию") }
                                }
                            }
                        }
                    }
                    // Список скрытых категорий — чтобы вернуть их, даже если в них сейчас нет каналов.
                    if (hiddenCategoryNames.isNotEmpty()) {
                        item("hidden_title") {
                            Text("Скрытые категории", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = ZenithDimens.paddingS))
                        }
                        items(hiddenCategoryNames, key = { "hc_$it" }) { name ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { viewModel.setCategoryHidden(if (name == "Без категории") null else name, false) }) { Text("Показать") }
                            }
                        }
                    }
                    if (visibleChannels.isEmpty()) {
                        item("empty") {
                            Text(
                                if (allChannels.isEmpty()) "Пока нет каналов — добавьте плейлист каналов или Xtream-панель в «Источники»" else "Ничего не найдено",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth().padding(ZenithDimens.paddingXL)
                            )
                        }
                    }
                    items(visibleChannels, key = { it.id }) { item ->
                        PhoneChannelEditCard(
                            item = item,
                            onToggleHidden = { viewModel.setHidden(item.id, !item.isHidden) },
                            onRename = { renameTarget = item },
                            onMerge = { mergeTarget = item },
                            onDelete = { deleteTarget = item },
                            onCheckNow = { viewModel.checkOne(item.id) }
                        )
                    }
                }
            }
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
                    if (selectedCategory == FAV_CATEGORY && searchQuery.isBlank()) "Избранных каналов пока нет.\nДолгое нажатие на канал — «В избранное»." else "Ничего не найдено",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(ZenithDimens.paddingXL)
                )
            }
            else -> {
                // Форма — настройка «Внешний вид → Карточки: Каналы»: список (как было), плитка или широкая плитка.
                val appearance = LocalAppearance.current
                val chStyle = appearance.styleOf(CardSection.CHANNELS, false)
                @Composable
                fun RowFor(ch: ChannelUiItem) {
                    ChannelRow(
                        item = ch,
                        style = chStyle,
                        isFavorite = ch.id in favorites,
                        epgVersion = epgVersion,
                        viewModel = viewModel,
                        onClick = { onPlayChannel(ch.id) },
                        onToggleFavorite = { toggleFavorite(ch.id) },
                        onOpenProgram = onOpenEpgGrid
                    )
                }
                if (chStyle == CardStyle.LIST) {
                    LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = ZenithDimens.paddingL)) {
                        itemsIndexed(visibleChannels, key = { i, c -> "${i}_${c.id}" }) { _, ch -> RowFor(ch) }
                    }
                } else {
                    val minCell = (if (chStyle == CardStyle.WIDE) 240.dp else 104.dp) * appearance.sizeMultiplier
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minCell),
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(start = ZenithDimens.paddingS, end = ZenithDimens.paddingS, bottom = ZenithDimens.paddingL),
                        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                        verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                    ) {
                        gridItemsIndexed(visibleChannels, key = { i, c -> "${i}_${c.id}" }) { _, ch -> RowFor(ch) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    item: ChannelUiItem,
    style: CardStyle,
    isFavorite: Boolean,
    epgVersion: Int,
    viewModel: ChannelsViewModel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenProgram: () -> Unit
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
    var menu by remember { mutableStateOf(false) }
    Box {
        if (style != CardStyle.LIST) {
            // Плитка (логотип сверху) или широкая плитка (логотип слева) — карточка с фоном.
            val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            val tileModifier = Modifier.fillMaxWidth().clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
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
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
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
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Убрать из избранного" else "В избранное") },
                onClick = { menu = false; onToggleFavorite() }
            )
            DropdownMenuItem(text = { Text("Телепрограмма") }, onClick = { menu = false; onOpenProgram() })
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(title, maxLines = 1) })
}
