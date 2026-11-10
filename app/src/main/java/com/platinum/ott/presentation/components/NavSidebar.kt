package com.platinum.ott.presentation.components

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.*
import com.platinum.ott.core.SidebarLayout
import com.platinum.ott.core.SidebarLayoutStore
import com.platinum.ott.core.platform.DpadKeyTracker
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.navigation.navigateToTab
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithShapeLarge
import com.platinum.ott.ui.theme.ZenithShapeMedium
import com.platinum.ott.ui.theme.ZenithSurface

// Боковая панель TV. В покое — узкая колонка иконок (88 dp, место в Row экрана не меняется);
// при фокусе раскрывается до 300 dp ПОВЕРХ контента, остальной экран затемняется, видны подписи и группы.
//
// Управление пультом:
//  • Влево с края контента → фокус попадает на пункт ТЕКУЩЕГО раздела (а не на ближайший по высоте).
//  • «Назад» из контента → фокус в панель на текущий раздел; ещё раз «Назад» — обычный выход/возврат.
//  • Вправо из панели → панель сразу сворачивается и фокус уходит в контент.
//  • OK на уже открытом разделе → просто возвращает фокус в контент.
//  • Фокус, пришедший без нажатия стрелки (начальный фокус экрана), панель не раскрывает, а отдаёт контенту.
// Пункты: «Каналы», «Телепрограмма» и «Сериалы» — верхнеуровневые маршруты ("channels", "epg_grid", "series").
private enum class SidebarGroup(val title: String?) { MAIN(null), TV("Телевидение"), CINEMA("Кино"), BOTTOM(null) }

private enum class SidebarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val group: SidebarGroup,
) {
    HOME("home", "Главная", Icons.Default.Home, SidebarGroup.MAIN),
    SEARCH("search", "Поиск", Icons.Default.Search, SidebarGroup.MAIN),
    CHANNELS("channels", "Каналы", Icons.Default.Tv, SidebarGroup.TV),
    EPG("epg_grid", "Телепрограмма", Icons.Default.CalendarMonth, SidebarGroup.TV),
    SERIES("series", "Сериалы", Icons.Default.Movie, SidebarGroup.CINEMA),
    FAVORITES("favorites", "Избранное", Icons.Default.Favorite, SidebarGroup.CINEMA),
    HISTORY("history", "История", Icons.Default.History, SidebarGroup.CINEMA),
    SETTINGS("settings", "Настройки", Icons.Default.Settings, SidebarGroup.BOTTOM),
}

private val CollapsedWidth = 88.dp
private val ExpandedWidth = 300.dp

/** Пункты, которые можно переставлять и скрывать (всё, кроме «Настроек»), в порядке пользователя. */
private fun orderedItems(layout: SidebarLayout): List<SidebarItem> {
    val movable = SidebarItem.values().filter { it.group != SidebarGroup.BOTTOM }
    val byRoute = movable.associateBy { it.route }
    val saved = layout.order.mapNotNull { byRoute[it] }.distinct()
    return saved + movable.filter { it !in saved }
}

/** «Главная» скрыть нельзя — иначе не останется явной точки возврата. */
private fun SidebarItem.canHide() = this != SidebarItem.HOME

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NavSidebar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    // Режим «поверх экрана» для экранов без постоянной колонки иконок: панель вообще не занимает места,
    // появляется по стрелке влево у левого края (см. TvSidebarOverlay) и скрывается после ухода фокуса.
    overlay: Boolean = false,
    overlayOpen: Boolean = false,
    onOverlayClosed: () -> Unit = {},
) {
    // "search" зарегистрирован как "search?q={q}" — сравниваем по базовому пути.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route?.substringBefore('?')
    val activeItem = SidebarItem.values().firstOrNull { it.route == currentRoute } ?: SidebarItem.HOME

    val context = LocalContext.current
    remember { SidebarLayoutStore.ensureLoaded(context); true }
    val layout by SidebarLayoutStore.layout.collectAsState()
    val visibleItems = orderedItems(layout).filter { it == SidebarItem.HOME || it.route !in layout.hidden }
    var editing by remember { mutableStateOf(false) }

    var expanded by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    // Панель открыта по явному действию (кнопка «Назад»), а не стрелкой.
    var userOpened by remember { mutableStateOf(false) }
    // Сворачиваем мгновенно, когда сразу уходим фокусом в контент: поиск следующего элемента идёт по готовой геометрии.
    var snapCollapse by remember { mutableStateOf(false) }
    var moveToContent by remember { mutableStateOf(0) }

    val focusManager = LocalFocusManager.current
    val composedAt = remember { SystemClock.uptimeMillis() }
    val requesters = remember { SidebarItem.values().associateWith { FocusRequester() } }
    val activeRequester = requesters.getValue(activeItem)

    LaunchedEffect(overlayOpen) {
        if (overlay && overlayOpen) {
            userOpened = true
            repeat(2) { withFrameNanos { } }
            activeRequester.safeRequestFocus()
        }
    }

    LaunchedEffect(moveToContent) {
        if (moveToContent > 0) {
            repeat(2) { withFrameNanos { } }
            focusManager.moveFocus(FocusDirection.Right)
            snapCollapse = false
        }
    }

    // Фокус в контенте → «Назад» ведёт в панель (на текущий раздел). Обработчики самих экранов
    // регистрируются позже и имеют приоритет, поэтому закрытие их окон и режимов не ломается.
    BackHandler(enabled = !overlay && !hasFocus) {
        userOpened = true
        activeRequester.safeRequestFocus()
    }
    // В режиме поверх экрана «Назад» просто закрывает открытую панель.
    BackHandler(enabled = overlay && hasFocus) {
        expanded = false; snapCollapse = true; moveToContent++
    }

    val colors = MaterialTheme.colorScheme
    val restWidth = if (overlay) 0.dp else CollapsedWidth
    val width by animateDpAsState(
        targetValue = if (expanded) ExpandedWidth else restWidth,
        animationSpec = if (expanded) tween(180) else if (snapCollapse) snap() else tween(110),
        label = "sidebarWidth"
    )
    val scrim = animateFloatAsState(if (expanded) 0.6f else 0f, tween(180), label = "sidebarScrim")
    val labelAlpha = animateFloatAsState(if (expanded) 1f else 0f, tween(if (expanded) 200 else 70), label = "sidebarLabels")

    // Внешний Box держит в Row ровно 88 dp и рисуется поверх соседей.
    Box(modifier = modifier.fillMaxHeight().width(restWidth).zIndex(1f)) {
        val panelVisible = !overlay || overlayOpen || expanded || width > 1.dp
        if (panelVisible) Column(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(width)
                // Затемнение остального экрана справа от панели.
                .drawBehind {
                    val a = scrim.value
                    if (a > 0f) drawRect(Color.Black.copy(alpha = a), topLeft = Offset(size.width, 0f), size = Size(4000f, size.height))
                }
                .background(colors.surface)
                .onFocusChanged {
                    val hadFocus = hasFocus
                    hasFocus = it.hasFocus
                    if (!it.hasFocus) {
                        expanded = false
                        userOpened = false
                        if (overlay && hadFocus) onOverlayClosed()
                    } else if (overlay || userOpened || DpadKeyTracker.lastDirectionalKeyMs >= composedAt) {
                        expanded = true
                    } else {
                        // Начальный фокус экрана — панель не раскрываем, фокус отдаём контенту.
                        expanded = false
                        snapCollapse = true
                        moveToContent++
                    }
                }
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown) {
                        when (it.key) {
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft -> expanded = true
                            // Кнопка «Меню» на пульте — редактор панели.
                            Key.Menu -> { editing = true; return@onPreviewKeyEvent true }
                            Key.DirectionRight -> {
                                expanded = false
                                snapCollapse = true
                                moveToContent++
                                return@onPreviewKeyEvent true
                            }
                            else -> Unit
                        }
                    }
                    false
                }
                // Вход в панель извне (стрелка влево из контента) — всегда на текущий раздел.
                .focusProperties { enter = { activeRequester } }
                .focusGroup()
                .padding(vertical = ZenithDimens.paddingL, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Знак бренда: на месте и в покое, и в раскрытом виде.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = ZenithDimens.paddingM).height(48.dp)
            ) {
                BrandMark(Modifier.size(40.dp))
                Text(
                    "ZENITH",
                    modifier = Modifier.padding(start = 12.dp).graphicsLayer { alpha = labelAlpha.value },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            // Пункты в прокручиваемой области (на 720p колонка не помещается по высоте),
            // «Настройки» закреплены под ней и видны всегда.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
                var lastGroup: SidebarGroup? = null
                visibleItems.forEach { item ->
                    if (item.group != lastGroup && (lastGroup != null || item.group.title != null)) {
                        // Заголовок группы — в раскрытом виде; в покое на его месте тонкий разделитель.
                        Box(Modifier.fillMaxWidth().height(32.dp)) {
                            Text(
                                item.group.title.orEmpty(),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 14.dp, bottom = 4.dp)
                                    .graphicsLayer { alpha = labelAlpha.value },
                                fontSize = 12.sp,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold,
                                color = ZenithOnSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                                    .width(28.dp)
                                    .height(1.dp)
                                    .graphicsLayer { alpha = (1f - labelAlpha.value) * 0.5f }
                                    .background(ZenithOnSurface)
                            )
                        }
                    }
                    lastGroup = item.group
                    SidebarEntry(
                        item = item,
                        selected = item == activeItem,
                        labelAlpha = labelAlpha,
                        requester = requesters.getValue(item),
                        onLongClick = { editing = true },
                        onClick = {
                            if (item == activeItem) {
                                // Уже в этом разделе — просто возвращаем фокус в контент.
                                expanded = false; snapCollapse = true; moveToContent++
                            } else navController.navigateToTab(item.route)
                        }
                    )
                }
            }

            Text(
                "Удерживайте OK — настроить меню",
                modifier = Modifier.padding(start = 16.dp, top = 4.dp).graphicsLayer { alpha = labelAlpha.value * 0.6f },
                fontSize = 11.sp,
                color = ZenithOnSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )

            // Настройки — отдельно внизу.
            Box(Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp)) {
                SidebarEntry(
                    item = SidebarItem.SETTINGS,
                    selected = SidebarItem.SETTINGS == activeItem,
                    labelAlpha = labelAlpha,
                    requester = requesters.getValue(SidebarItem.SETTINGS),
                    onLongClick = { editing = true },
                    onClick = {
                        if (SidebarItem.SETTINGS == activeItem) {
                            expanded = false; snapCollapse = true; moveToContent++
                        } else navController.navigateToTab(SidebarItem.SETTINGS.route)
                    }
                )
            }
        }
    }
    if (editing) SidebarEditorDialog(onDismiss = { editing = false })
}

/**
 * Пункт панели. Фокус — мягкая заливка акцентом и цветная рамка (без белой заливки tv-material),
 * текущий раздел — слабая заливка, цветная иконка/подпись и полоска слева.
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SidebarEntry(
    item: SidebarItem,
    selected: Boolean,
    labelAlpha: State<Float>,
    requester: FocusRequester,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeMedium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = ZenithOnSurface,
            focusedContainerColor = accent.copy(alpha = 0.30f),
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.42f),
            pressedContentColor = ZenithOnSurface,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, accent))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.focusRequester(requester).padding(vertical = 3.dp).fillMaxWidth().height(52.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 3.dp)
                        .width(4.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(start = 16.dp).clipToBounds()
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) accent else ZenithOnSurface,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    item.label,
                    modifier = Modifier.padding(start = 16.dp).graphicsLayer { alpha = labelAlpha.value },
                    fontSize = 18.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) accent else ZenithOnSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/**
 * Редактор меню: у каждого пункта три кнопки — выше, ниже, показать/скрыть. Список двигается вместе с фокусом
 * (строки с ключом по маршруту), поэтому можно подряд нажимать «вниз» и двигать пункт дальше.
 * Доступен из любого экрана с боковой панелью: удержание OK на пункте или кнопка «Меню» на пульте.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarEditorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val layout by SidebarLayoutStore.layout.collectAsState()
    val items = orderedItems(layout)
    val firstFocus = remember { FocusRequester() }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in items.indices) return
        val routes = items.map { it.route }.toMutableList()
        routes.add(target, routes.removeAt(index))
        SidebarLayoutStore.update(context) { it.copy(order = routes) }
    }
    fun toggle(item: SidebarItem) {
        if (!item.canHide()) return
        SidebarLayoutStore.update(context) {
            it.copy(hidden = if (item.route in it.hidden) it.hidden - item.route else it.hidden + item.route)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface)
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.10f), ZenithShapeLarge)
                .padding(ZenithDimens.paddingL)
        ) {
            Text("Меню", style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "Переставляйте разделы стрелками и скрывайте ненужные. «Главная» и «Настройки» всегда на месте.",
                style = MaterialTheme.typography.bodySmall,
                color = ZenithOnSurfaceVariant
            )
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                items.forEachIndexed { index, item ->
                    key(item.route) {
                        val hidden = item.route in layout.hidden
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                item.icon, contentDescription = null,
                                tint = if (hidden) ZenithOnSurface.copy(alpha = 0.35f) else ZenithOnSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                item.label,
                                modifier = Modifier.weight(1f).padding(start = 14.dp),
                                fontSize = 17.sp,
                                color = if (hidden) ZenithOnSurface.copy(alpha = 0.35f) else ZenithOnSurface,
                                maxLines = 1
                            )
                            TvPillButton(
                                Icons.Default.KeyboardArrowUp, { move(index, -1) },
                                Modifier.then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                                contentDescription = "Выше"
                            )
                            Spacer(Modifier.width(6.dp))
                            TvPillButton(Icons.Default.KeyboardArrowDown, { move(index, +1) }, contentDescription = "Ниже")
                            Spacer(Modifier.width(6.dp))
                            if (item.canHide()) {
                                TvPillButton(
                                    if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    { toggle(item) },
                                    contentDescription = if (hidden) "Показать" else "Скрыть"
                                )
                            } else {
                                Spacer(Modifier.width(40.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TvPillButton(Icons.Default.Refresh, { SidebarLayoutStore.reset(context) }, label = "Сбросить")
                Spacer(Modifier.width(ZenithDimens.paddingSM))
                TvPillButton(Icons.Default.Check, onDismiss, label = "Готово")
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        firstFocus.safeRequestFocus()
    }
}

// Знак «Zenith» (как в иконке): дуга, точка-зенит и треугольник-play.
// Рисуется кодом в системе координат 108x108, как адаптивная иконка.
@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val k = size.minDimension / 108f
        val arc = Path().apply {
            arcTo(Rect(Offset(30f * k, 30f * k), Size(48f * k, 48f * k)), 228.7f, -277.4f, true)
        }
        drawPath(arc, accent, style = Stroke(width = 6f * k, cap = StrokeCap.Round))
        drawCircle(ZenithOnSurface, radius = 4.6f * k, center = Offset(54f * k, 29.5f * k))
        val play = Path().apply {
            moveTo(46f * k, 47f * k); lineTo(46f * k, 69f * k); lineTo(67f * k, 58f * k); close()
        }
        drawPath(play, ZenithOnSurface)
    }
}

/**
 * Оборачивает TV-экран, у которого нет собственной боковой панели (Каналы,
 * Телепрограмма, Сериалы): панель слева, экран справа. Сам экран не меняется.
 */
@Composable
fun TvWithSidebar(navController: NavHostController, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        NavSidebar(navController)
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

/**
 * Для экранов без постоянной боковой панели (карточка фильма, сериал, источники, плагины…): весь экран остаётся
 * как был, а стрелка влево у левого края вызывает панель поверх него — так же, как на главной.
 * Если фокус ещё может сдвинуться влево внутри экрана, стрелка работает как обычно.
 * После закрытия панели («Назад» или вправо) фокус возвращается на тот же элемент экрана.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun TvSidebarOverlay(navController: NavHostController, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRestorer()
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
                        if (!focusManager.moveFocus(FocusDirection.Left)) open = true
                        true
                    } else false
                }
        ) { content() }
        NavSidebar(navController, overlay = true, overlayOpen = open, onOverlayClosed = { open = false })
    }
}
