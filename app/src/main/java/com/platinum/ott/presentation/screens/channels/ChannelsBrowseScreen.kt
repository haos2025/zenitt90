package com.platinum.ott.presentation.screens.channels

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelNameMatcher
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.data.repository.HiddenChannelsStore
import com.platinum.ott.presentation.components.TvConfirmDialog
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.CardSection
import com.platinum.ott.ui.theme.CardStyle
import com.platinum.ott.ui.theme.LocalAppearance
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithShapeSmall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALL_CATEGORY = "__all__"
private const val FAV_CATEGORY = "__fav__"
private const val PREFS = "tv_channels"
private const val KEY_FAVS = "favorite_ids"
private const val KEY_CATEGORY = "last_category"
private const val KEY_LAYOUT = "layout_mode"
private val NO_CATEGORY = HiddenChannelsStore.NO_CATEGORY

private fun categoryOf(ch: ChannelUiItem): String = ch.category?.trim()?.ifBlank { null } ?: NO_CATEGORY

/**
 * ТВ: экран «Каналы» — одна раскладка: слева категории, в центре каналы, справа сведения.
 *  • Категория выбирается фокусом (каналы справа меняются сразу); OK или «Вправо» на категории прячет
 *    панель категорий — остаются каналы и сведения; «Влево» у левого края каналов возвращает категории
 *    (фокус — на выбранной категории). Сведения меняются по фокусу: у канала — передача сейчас/далее,
 *    у категории — сводка по ней.
 *  • OK на канале — смотреть; долгое OK на канале или категории — полный редактор. «Переместить»
 *    в редакторе включает режим захвата: канал стрелками двигается прямо в списке (видно, куда он
 *    встаёт), OK — оставить, «Назад» — вернуть на прежнее место.
 *  • «Меню»: проверка источников, программа, правка, источники и вид карточек каналов (плитка / широкая / список).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsBrowseScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    // Режим «Править» (план 07, п. 12): показывает скрытые каналы и категории; OK по каналу/категории — редактор.
    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    // Пустой экран («Каналов пока нет») ведёт к добавлению источника.
    onAddSourceClick: () -> Unit,
    onOpenSources: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val epgBusy by viewModel.epgBusy.collectAsStateWithLifecycle()
    val epgVersion by viewModel.epgVersion.collectAsStateWithLifecycle()
    val catOrder by viewModel.categoryOrder.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(prefs.getStringSet(KEY_FAVS, emptySet()).orEmpty().toSet()) }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet(KEY_FAVS, favorites).apply()
    }
    val appearance = LocalAppearance.current
    // Панель категорий: OK/«Вправо» на категории прячет её, «Влево» у левого края каналов — возвращает.
    var categoriesVisible by remember { mutableStateOf(true) }
    var focusCategoriesRequest by remember { mutableIntStateOf(0) }
    val selectedCatRequester = remember { FocusRequester() }
    // Режим «захвата»: канал двигают стрелками; [grabOrigin] — место до захвата (для отмены).
    var grabbedId by remember { mutableStateOf<String?>(null) }
    var grabOrigin by remember { mutableIntStateOf(-1) }
    // Плитка, которой нужно вернуть фокус (после начала/конца захвата ключи плиток меняются и фокус теряется).
    val grabRequester = remember { FocusRequester() }
    var focusTileId by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    var gridLeftPx by remember { mutableFloatStateOf(0f) }
    var gridWidthPx by remember { mutableIntStateOf(0) }

    val allChannels = remember(uiState) {
        (uiState as? ChannelsUiState.Success)?.channels.orEmpty().sortedBy { it.channelNumber }
    }
    // В обычном режиме — только видимые (не скрытые сами и не в скрытой категории); в «Править» — все,
    // скрытые приглушены и подписаны «скрыт».
    val subscribedChannels = remember(allChannels, editMode) { if (editMode) allChannels else allChannels.filter { it.isVisible } }
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()

    // Окна: показ меню и редакторов. Канал в редакторе хранится по id, чтобы после перемещения показывать свежие данные.
    var menuOpen by remember { mutableStateOf(false) }
    var editorId by remember { mutableStateOf<String?>(null) }
    var categoryEditor by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var mergeTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var deleteTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var positionTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var numberTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var categoryPickTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var newCategoryTarget by remember { mutableStateOf<ChannelUiItem?>(null) }
    var renameCategoryTarget by remember { mutableStateOf<String?>(null) }
    // «Назад» в режиме «Править» сначала выходит из режима.
    BackHandler(enabled = editMode) { onEditModeChange(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Выбранная категория запоминается (в том числе «Избранные») — после просмотра канала экран возвращается в неё.
    var selectedCategoryRaw by remember { mutableStateOf(prefs.getString(KEY_CATEGORY, ALL_CATEGORY) ?: ALL_CATEGORY) }
    LaunchedEffect(selectedCategoryRaw) { prefs.edit().putString(KEY_CATEGORY, selectedCategoryRaw).apply() }
    val firstRowRequester = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    var focusedId by remember { mutableStateOf<String?>(null) }
    // Фокус сейчас на категории (в панели сведений — сводка по ней) или на канале (передача сейчас/далее).
    var focusOnCategory by remember { mutableStateOf(false) }

    // Категории: свой порядок (если пользователь его задал), остальные — по числу каналов.
    val categoryCounts = remember(subscribedChannels, catOrder) {
        subscribedChannels.groupBy { categoryOf(it) }
            .map { (name, list) -> Triple(name, list.size, list.all { it.categoryHidden }) }
            .sortedWith(
                compareBy<Triple<String, Int, Boolean>> { t -> catOrder.indexOf(t.first).let { if (it < 0) Int.MAX_VALUE else it } }
                    .thenByDescending { it.second }
            )
    }
    val categoryNames = remember(categoryCounts) { categoryCounts.map { it.first } }
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
                    else -> categoryOf(it) == selectedCategory
                }
            }
            // Поиск понимает транслитерацию: «sts» находит «СТС», «матч» — «Match TV».
            .filter { ChannelNameMatcher.matchesQuery(it.canonicalName, searchQuery) }
    }

    // Вернуть панель категорий и поставить фокус на выбранную категорию.
    LaunchedEffect(focusCategoriesRequest) {
        if (focusCategoriesRequest > 0) {
            categoriesVisible = true
            repeat(3) { withFrameNanos { } }
            selectedCatRequester.safeRequestFocus()
        }
    }
    fun collapseCategories() {
        if (visibleChannels.isNotEmpty()) { firstRowRequester.safeRequestFocus(); categoriesVisible = false }
    }
    val categoryTitle = when (selectedCategory) { ALL_CATEGORY -> "Все каналы"; FAV_CATEGORY -> "★ Избранные"; else -> selectedCategory }
    // Колонок в сетке каналов (та же формула, что у GridCells.Adaptive) — для шага «вверх/вниз» в режиме захвата.
    val chStyleNow = appearance.styleOf(CardSection.CHANNELS, true)
    val density = LocalDensity.current
    val gridColumns = run {
        val minCellDp = when (chStyleNow) { CardStyle.WIDE -> 280.dp; CardStyle.LIST -> 340.dp; else -> 150.dp } * appearance.sizeMultiplier
        val minPx = with(density) { minCellDp.toPx() }
        val spPx = with(density) { ZenithDimens.paddingS.toPx() }
        maxOf(1, ((gridWidthPx + spPx) / (minPx + spPx)).toInt())
    }
    fun grabMove(delta: Int) {
        val id = grabbedId ?: return
        val cur = visibleChannels.indexOfFirst { it.id == id }
        if (cur < 0) return
        val target = (cur + delta).coerceIn(0, visibleChannels.lastIndex)
        if (target != cur) viewModel.reorder(visibleChannels.map { it.id }, id, target)
    }
    fun grabEnd() { focusTileId = grabbedId; grabbedId = null }
    fun grabCancel() {
        val id = grabbedId ?: return
        if (grabOrigin >= 0) viewModel.reorder(visibleChannels.map { it.id }, id, grabOrigin)
        grabEnd()
    }
    LaunchedEffect(grabbedId) {
        // Начало захвата: прячем категории (канал виден целиком) и ставим фокус на перемещаемую плитку.
        if (grabbedId != null) { categoriesVisible = false; focusTileId = grabbedId }
    }
    LaunchedEffect(focusTileId) {
        if (focusTileId != null) {
            repeat(3) { withFrameNanos { } }
            grabRequester.safeRequestFocus()
            focusTileId = null
        }
    }
    BackHandler(enabled = grabbedId != null) { grabCancel() }
    // За перемещаемым каналом прокручиваем список, если он ушёл за край.
    val grabIndex = grabbedId?.let { id -> visibleChannels.indexOfFirst { it.id == id } } ?: -1
    LaunchedEffect(grabIndex) {
        if (grabIndex >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == grabIndex }) gridState.animateScrollToItem(grabIndex)
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
            .onPreviewKeyEvent { e ->
                if (grabbedId == null) return@onPreviewKeyEvent false
                // Режим захвата: стрелки двигают канал, OK — оставить на месте. «Назад» — BackHandler выше.
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionLeft -> { grabMove(-1); true }
                        Key.DirectionRight -> { grabMove(1); true }
                        Key.DirectionUp -> { grabMove(-gridColumns); true }
                        Key.DirectionDown -> { grabMove(gridColumns); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { grabEnd(); true }
                        else -> false
                    }
                } else e.key in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Каналы", style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
            if (editMode) {
                Spacer(Modifier.width(ZenithDimens.paddingSM))
                Text("Правка · «Назад» — выйти", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            ChannelSearchField(query = searchQuery, onChange = { searchQuery = it }, modifier = Modifier.width(200.dp))
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = { menuOpen = true }) {
                Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Меню")
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingL))

        if (subscribedChannels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                    Text(
                        if (allChannels.isEmpty()) "Каналов пока нет — добавьте плейлист или панель" else "Все каналы скрыты — в «Меню» выберите «Править каналы», чтобы показать",
                        color = ZenithOnSurface.copy(alpha = 0.6f)
                    )
                    if (allChannels.isEmpty()) {
                        OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = onAddSourceClick) { Text("Добавить источник") }
                    }
                }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Ширина панелей — доля экрана в разумных пределах.
                val categoriesWidth = (maxWidth * 0.24f).coerceIn(170.dp, 240.dp)
                val detailsWidth = (maxWidth * 0.30f).coerceIn(200.dp, 300.dp)

                Row(Modifier.fillMaxSize()) {
                    // 1. Категории: выбираются фокусом; OK/«Вправо» — спрятать панель и перейти к каналам.
                    AnimatedVisibility(
                        visible = categoriesVisible,
                        enter = expandHorizontally(),
                        exit = shrinkHorizontally()
                    ) {
                        Box(Modifier.width(categoriesWidth + ZenithDimens.paddingL).fillMaxHeight().padding(end = ZenithDimens.paddingL)) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(170.dp),
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS),
                                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)
                            ) {
                                item(key = "all") {
                                    CategoryRow("Все каналы", subscribedChannels.size, selectedCategory == ALL_CATEGORY,
                                        requester = if (selectedCategory == ALL_CATEGORY) selectedCatRequester else null,
                                        onPick = { selectedCategoryRaw = ALL_CATEGORY; focusOnCategory = true; if (it) collapseCategories() })
                                }
                                item(key = "fav") {
                                    CategoryRow("★ Избранные", favorites.count { id -> subscribedChannels.any { it.id == id } }, selectedCategory == FAV_CATEGORY,
                                        requester = if (selectedCategory == FAV_CATEGORY) selectedCatRequester else null,
                                        onPick = { selectedCategoryRaw = FAV_CATEGORY; focusOnCategory = true; if (it) collapseCategories() })
                                }
                                gridItems(categoryCounts, key = { "c_" + it.first }) { (category, count, hidden) ->
                                    // Долгое OK — редактор категории; в «Править» его открывает и обычное OK.
                                    CategoryRow(
                                        title = category, count = count, selected = selectedCategory == category,
                                        hiddenMark = editMode && hidden,
                                        requester = if (selectedCategory == category) selectedCatRequester else null,
                                        onClick = if (editMode) { { categoryEditor = category } } else null,
                                        onLongClick = { categoryEditor = category },
                                        onPick = { selectedCategoryRaw = category; focusOnCategory = true; if (it) collapseCategories() }
                                    )
                                }
                            }
                        }
                    }
                    // 2. Каналы.
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (!categoriesVisible) {
                            Text(
                                "◀ $categoryTitle · ${visibleChannels.size}  —  «Влево» у края возвращает категории",
                                style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface.copy(alpha = 0.55f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = ZenithDimens.paddingXS)
                            )
                        }
                        if (grabbedId != null) {
                            Text(
                                "Перемещение: стрелки — двигать канал, OK — оставить здесь, «Назад» — отменить",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = ZenithDimens.paddingXS)
                            )
                        }
                        if (visibleChannels.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (selectedCategory == FAV_CATEGORY && searchQuery.isBlank()) "Избранных каналов пока нет.\nУдерживайте OK на канале и выберите «В избранное»." else "Ничего не найдено",
                                    color = ZenithOnSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            // Форма — «Меню каналов → Вид карточек»: плитка, широкая плитка или список.
                            val chStyle = chStyleNow
                            val minCell = when (chStyle) {
                                CardStyle.WIDE -> 280.dp
                                CardStyle.LIST -> 340.dp
                                else -> 150.dp
                            } * appearance.sizeMultiplier
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minCell),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                                    .onGloballyPositioned { gridLeftPx = it.positionInRoot().x }
                                    .onSizeChanged { gridWidthPx = it.width },
                                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
                                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                            ) {
                                // Ключ только по id в «Править» и при захвате: порядок меняется на глазах,
                                // а фокус должен оставаться на том же канале.
                                gridItemsIndexed(visibleChannels, key = { i, c -> if (editMode || grabbedId != null) c.id else "${i}_${c.id}" }) { index, ch ->
                                    val leftHolder = remember { FloatArray(1) }
                                    ChannelTileTv(
                                        item = ch,
                                        style = chStyle,
                                        isFavorite = !editMode && ch.id in favorites,
                                        editMode = editMode,
                                        grabbed = grabbedId == ch.id,
                                        epgVersion = epgVersion,
                                        viewModel = viewModel,
                                        onClick = { if (editMode) editorId = ch.id else onPlayChannel(ch.id) },
                                        onLongClick = { editorId = ch.id },
                                        onFocused = { focusedId = ch.id; focusOnCategory = false },
                                        modifier = (if (index == 0) Modifier.focusRequester(firstRowRequester) else Modifier)
                                            .then(if (ch.id == (grabbedId ?: focusTileId)) Modifier.focusRequester(grabRequester) else Modifier)
                                            .onGloballyPositioned { leftHolder[0] = it.positionInRoot().x }
                                            .onPreviewKeyEvent { e ->
                                                // «Влево» у левого края каналов при скрытых категориях — вернуть категории.
                                                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && !categoriesVisible &&
                                                    grabbedId == null && leftHolder[0] - gridLeftPx < 4f
                                                ) { focusCategoriesRequest++; true } else false
                                            }
                                    )
                                }
                            }
                        }
                    }
                    // 3. Сведения о выбранном канале / категории.
                    Spacer(Modifier.width(ZenithDimens.paddingL))
                    if (focusOnCategory) {
                        CategorySummaryPanel(categoryTitle, visibleChannels, favorites, Modifier.width(detailsWidth).fillMaxHeight())
                    } else {
                        val panelChannel = visibleChannels.firstOrNull { it.id == focusedId } ?: visibleChannels.firstOrNull()
                        ChannelPanel(panelChannel, viewModel, epgVersion, Modifier.width(detailsWidth).fillMaxHeight())
                    }
                }
            }
        }
    }

    // ---------- Окна ----------
    val viewIds = visibleChannels.map { it.id }

    if (menuOpen) {
        ChannelsMenuDialog(
            editMode = editMode,
            isChecking = isChecking,
            epgBusy = epgBusy,
            cardStyle = chStyleNow,
            onCheckAll = { viewModel.checkAll() },
            onRefreshEpg = { viewModel.refreshEpg { msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() } },
            onToggleEdit = { onEditModeChange(!editMode) },
            onOpenSources = onOpenSources,
            onCardStyle = { viewModel.setChannelCardStyle(it) },
            onDismiss = { menuOpen = false }
        )
    }

    editorId?.let { id -> allChannels.firstOrNull { it.id == id } }?.let { ch ->
        val pos = visibleChannels.indexOfFirst { it.id == ch.id } + 1
        ChannelEditorDialog(
            channel = ch,
            position = pos,
            total = visibleChannels.size,
            isFavorite = ch.id in favorites,
            isChecking = isChecking,
            onToggleFavorite = { toggleFavorite(ch.id) },
            onToggleHidden = { viewModel.setHidden(ch.id, !ch.isHidden) },
            onRename = { renameTarget = ch; editorId = null },
            onCheck = { viewModel.checkOne(ch.id) },
            onUp = { viewModel.reorder(viewIds, ch.id, pos - 2) },
            onDown = { viewModel.reorder(viewIds, ch.id, pos) },
            onTop = { viewModel.reorder(viewIds, ch.id, 0) },
            onBottom = { viewModel.reorder(viewIds, ch.id, viewIds.lastIndex) },
            onGrab = {
                grabbedId = ch.id; grabOrigin = visibleChannels.indexOfFirst { it.id == ch.id }; editorId = null
            },
            onToPosition = { positionTarget = ch; editorId = null },
            onSetNumber = { numberTarget = ch; editorId = null },
            onCategory = { categoryPickTarget = ch; editorId = null },
            onSimilar = { mergeTarget = ch; editorId = null },
            onDelete = { deleteTarget = ch; editorId = null },
            onDismiss = { editorId = null }
        )
    }
    categoryEditor?.let { name ->
        val info = categoryCounts.firstOrNull { it.first == name }
        if (info == null) {
            // Категория исчезла (скрыли, переименовали, объединили) — окно закрываем.
            LaunchedEffect(name) { categoryEditor = null }
        } else {
            val pos = categoryNames.indexOf(name) + 1
            CategoryEditorDialog(
                name = name,
                channelCount = info.second,
                isHidden = info.third,
                position = pos,
                total = categoryNames.size,
                onToggleHidden = { viewModel.setCategoryHidden(if (name == NO_CATEGORY) null else name, !info.third) },
                onRename = { renameCategoryTarget = name; categoryEditor = null },
                onUp = { viewModel.moveCategory(categoryNames, name, pos - 2) },
                onDown = { viewModel.moveCategory(categoryNames, name, pos) },
                onTop = { viewModel.moveCategory(categoryNames, name, 0) },
                onBottom = { viewModel.moveCategory(categoryNames, name, categoryNames.lastIndex) },
                onDismiss = { categoryEditor = null }
            )
        }
    }
    renameTarget?.let { target ->
        RenameChannelDialog(
            channel = target,
            onConfirm = { name, regionHint -> viewModel.rename(target.id, name, regionHint); renameTarget = null; editorId = target.id },
            onDismiss = { renameTarget = null; editorId = target.id }
        )
    }
    positionTarget?.let { target ->
        TvTextInputDialog(
            title = "На позицию",
            subtitle = "«${target.canonicalName}» — место в списке от 1 до ${viewIds.size}",
            hint = "Номер места",
            initial = (viewIds.indexOf(target.id) + 1).coerceAtLeast(1).toString(),
            numeric = true,
            confirmLabel = "Переместить",
            onConfirm = { text ->
                text.toIntOrNull()?.let { viewModel.reorder(viewIds, target.id, it - 1) }
                positionTarget = null; editorId = target.id
            },
            onDismiss = { positionTarget = null; editorId = target.id }
        )
    }
    numberTarget?.let { target ->
        TvTextInputDialog(
            title = "Номер канала",
            subtitle = "Его набирают на пульте. Если номер занят, каналы поменяются номерами.",
            hint = "Номер",
            initial = target.channelNumber.toString(),
            numeric = true,
            confirmLabel = "Сохранить",
            onConfirm = { text ->
                text.toIntOrNull()?.let { viewModel.setNumber(target.id, it) }
                numberTarget = null; editorId = target.id
            },
            onDismiss = { numberTarget = null; editorId = target.id }
        )
    }
    categoryPickTarget?.let { target ->
        CategoryPickerDialog(
            channelName = target.canonicalName,
            categories = (categoryNames + NO_CATEGORY).distinct(),
            current = categoryOf(target),
            noCategoryName = NO_CATEGORY,
            onPick = { cat -> viewModel.moveToCategory(target.id, cat); categoryPickTarget = null; editorId = target.id },
            onNew = { newCategoryTarget = target; categoryPickTarget = null },
            onDismiss = { categoryPickTarget = null; editorId = target.id }
        )
    }
    newCategoryTarget?.let { target ->
        TvTextInputDialog(
            title = "Новая категория",
            subtitle = "Канал «${target.canonicalName}» будет перенесён в неё",
            hint = "Название категории",
            initial = "",
            numeric = false,
            confirmLabel = "Создать",
            onConfirm = { text ->
                if (text.isNotBlank()) viewModel.moveToCategory(target.id, text.trim())
                newCategoryTarget = null; editorId = target.id
            },
            onDismiss = { newCategoryTarget = null; editorId = target.id }
        )
    }
    renameCategoryTarget?.let { name ->
        TvTextInputDialog(
            title = "Переименовать категорию",
            subtitle = "«$name» — новое имя получат все её каналы",
            hint = "Название категории",
            initial = name,
            numeric = false,
            confirmLabel = "Сохранить",
            onConfirm = { text ->
                if (text.isNotBlank()) {
                    viewModel.renameCategory(name, text.trim())
                    if (selectedCategoryRaw == name) selectedCategoryRaw = text.trim()
                }
                renameCategoryTarget = null
            },
            onDismiss = { renameCategoryTarget = null }
        )
    }
    mergeTarget?.let { target ->
        SimilarChannelsDialog(
            source = target,
            candidates = allChannels.filter { it.id != target.id },
            onSelect = { chosen -> viewModel.merge(target.id, chosen.id); mergeTarget = null },
            onDismiss = { mergeTarget = null; editorId = target.id }
        )
    }
    deleteTarget?.let { target ->
        TvConfirmDialog(
            title = "Удалить канал?",
            text = "«${target.canonicalName}» и все его ссылки от источников будут удалены из списка каналов.",
            confirmLabel = "Удалить",
            onConfirm = { viewModel.delete(target.id); deleteTarget = null },
            onDismiss = { deleteTarget = null; editorId = target.id }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelTileTv(
    item: ChannelUiItem,
    style: CardStyle,
    isFavorite: Boolean,
    editMode: Boolean,
    grabbed: Boolean = false,
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
        // Захваченный канал выделен акцентной рамкой и «↕» — видно, что именно двигается.
        border = ClickableSurfaceDefaults.border(
            border = if (grabbed) Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = tileShape) else Border.None,
            focusedBorder = Border(BorderStroke(if (grabbed) 3.dp else 2.dp, MaterialTheme.colorScheme.primary), shape = tileShape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        if (style != CardStyle.POSTER) {
            // Широкая плитка / строка списка: логотип слева, справа название и «сейчас в эфире».
            Row(
                Modifier.fillMaxWidth().padding(ZenithDimens.paddingS).alpha(if (editMode && !item.isVisible) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (style == CardStyle.LIST) {
                    Text("${item.channelNumber}", style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface.copy(alpha = 0.5f), modifier = Modifier.width(44.dp))
                }
                Box(Modifier.width(88.dp).height(56.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) {
                    if (item.logo != null) com.platinum.ott.presentation.components.ChannelLogoImage(model = item.logo, name = item.canonicalName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.8f))
                    else Text(item.canonicalName.take(2).uppercase(), color = ZenithOnSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(ZenithDimens.paddingSM))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.canonicalName, style = MaterialTheme.typography.titleSmall, color = ZenithOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (grabbed) Text("  ↕", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                        else if (editMode && !item.isVisible) Text("  скрыт", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        else if (isFavorite) Text("  ★", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
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
            return@Surface
        }
        // В режиме «Править» скрытые каналы приглушены.
        Column(Modifier.padding(ZenithDimens.paddingS).alpha(if (editMode && !item.isVisible) 0.45f else 1f)) {
            // Верх плитки: логотип на светлой подложке (тёмные логотипы читаются), номер и звезда по углам.
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)),
                Alignment.Center
            ) {
                if (item.logo != null) com.platinum.ott.presentation.components.ChannelLogoImage(model = item.logo, name = item.canonicalName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
                else Text(item.canonicalName.take(2).uppercase(), color = ZenithOnSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium)
                Text("${item.channelNumber}", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                if (grabbed) Text("↕", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                else if (editMode && !item.isVisible) Text("скрыт", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
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
        modifier.clip(ZenithShapeSmall).background(ZenithOnSurface.copy(alpha = 0.05f)).verticalScroll(rememberScrollState()).padding(ZenithDimens.paddingSM),
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

/** Сводка по категории: показывается в панели сведений, пока фокус на категории. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategorySummaryPanel(title: String, channels: List<ChannelUiItem>, favorites: Set<String>, modifier: Modifier = Modifier) {
    val alive = channels.count { it.healthStatus == "alive" }
    val dead = channels.count { it.healthStatus == "dead" }
    val hidden = channels.count { it.isHidden || it.categoryHidden }
    val favCount = channels.count { it.id in favorites }
    val archive = channels.count { it.catchupDaysAvailable > 0 }
    Column(
        modifier.clip(ZenithShapeSmall).background(ZenithOnSurface.copy(alpha = 0.05f)).verticalScroll(rememberScrollState()).padding(ZenithDimens.paddingSM),
        verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Каналов: ${channels.size}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        if (channels.isNotEmpty()) {
            Text("Работают: $alive", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.85f))
            if (dead > 0) Text("Не отвечают: $dead", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.85f))
            Text("Не проверялись: ${channels.size - alive - dead}", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.6f))
            if (favCount > 0) Text("В избранном: $favCount", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.85f))
            if (archive > 0) Text("С архивом передач: $archive", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.85f))
            if (hidden > 0) Text("Скрыто: $hidden", style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(ZenithDimens.paddingXS))
        Text("Удерживайте OK на категории — редактор", style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun ChannelLogo(item: ChannelUiItem, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(ZenithOnSurface.copy(alpha = 0.08f)), Alignment.Center) {
        if (item.logo != null) com.platinum.ott.presentation.components.ChannelLogoImage(model = item.logo, name = item.canonicalName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
        else Text(item.canonicalName.take(2).uppercase(), color = ZenithOnSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ProgressLine(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(ZenithOnSurface.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
    }
}

/**
 * Категория выбирается фокусом (быстрый просмотр без лишнего OK): при фокусе вызывается [onPick] с false.
 * OK (или «Вправо») — [onPick] с true: выбрать и спрятать панель категорий, перейти к каналам.
 * Долгое OK — редактор категории ([onLongClick]); в «Править» обычное OK открывает его же ([onClick]).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryRow(
    title: String, count: Int, selected: Boolean,
    hiddenMark: Boolean = false,
    requester: FocusRequester? = null,
    onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
    onPick: (confirm: Boolean) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick ?: { onPick(true) },
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) ZenithOnSurface.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = ZenithOnSurface,
            focusedContainerColor = ZenithFocusContainerActive,
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.30f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, accent))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { if (it.isFocused) onPick(false) }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { onPick(true); true } else false
            }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (hiddenMark) "$title (скрыта)" else title, style = MaterialTheme.typography.bodyMedium, color = if (selected) ZenithOnSurface else ZenithOnSurface.copy(alpha = if (hiddenMark) 0.4f else 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelSmall, color = ZenithOnSurface.copy(alpha = 0.5f))
        }
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
