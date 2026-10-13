package com.platinum.ott.presentation.components

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import android.os.SystemClock
import com.platinum.ott.core.platform.DpadKeyTracker
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.navigation.navigateToTab
import com.platinum.ott.ui.theme.ZenithShapeMedium

// Боковая панель TV: в покое — узкая колонка иконок (88 dp, место в Row
// экрана не меняется), при фокусе на любом пункте раскрывается до 300 dp
// ПОВЕРХ контента (zIndex), с подписями и группами. Контент не сдвигается и
// не перекомпоновывается, поэтому нет скачков фокуса и лишней работы.
//
// Куда ведут пункты: "Каналы", "Телепрограмма" и "Сериалы" — верхнеуровневые
// маршруты ("channels", "epg_grid", "series"); сами эти экраны остаются со
// своей кнопкой "Назад" (панель на них пока не добавлена), "Назад" возвращает
// на предыдущую вкладку. "Источники" по-прежнему только через Настройки.
private enum class SidebarGroup(val title: String?) { MAIN(null), TV("Телевидение"), CINEMA("Кино") }

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
}

private val CollapsedWidth = 88.dp
private val ExpandedWidth = 300.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavSidebar(navController: NavHostController, modifier: Modifier = Modifier) {
    // "search" зарегистрирован как "search?q={q}" — сравниваем по базовому пути.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route?.substringBefore('?')
    var expanded by remember { mutableStateOf(false) }
    // ФИКС «панель сама открывается»: при входе на экран система ставит начальный
    // фокус на первый фокусируемый элемент — это панель (она первая в Row). Раньше
    // это раскрывало её на каждом экране и после каждого перехода из панели.
    // Теперь: если фокус пришёл БЕЗ нажатия стрелки после появления экрана — панель
    // остаётся свёрнутой и передаёт фокус контенту (moveFocus вправо).
    val focusManager = LocalFocusManager.current
    val composedAt = remember { SystemClock.uptimeMillis() }
    var handoffToContent by remember { mutableStateOf(false) }
    LaunchedEffect(handoffToContent) {
        if (handoffToContent) {
            handoffToContent = false
            focusManager.moveFocus(FocusDirection.Right)
        }
    }
    val width by animateDpAsState(if (expanded) ExpandedWidth else CollapsedWidth, tween(180), label = "sidebarWidth")
    val colors = MaterialTheme.colorScheme

    // Внешний Box держит в Row ровно 88 dp и рисуется поверх соседей.
    Box(modifier = modifier.fillMaxHeight().width(CollapsedWidth).zIndex(1f)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(width)
                .then(if (expanded) Modifier.shadow(24.dp) else Modifier)
                .background(colors.surface)
                // hasFocus = фокус на любом из пунктов внутри панели.
                .onFocusChanged {
                    if (!it.hasFocus) expanded = false
                    else if (DpadKeyTracker.lastDirectionalKeyMs >= composedAt) expanded = true
                    else { expanded = false; handoffToContent = true }
                }
                // Стрелка, нажатая уже внутри (свёрнутой) панели, раскрывает её.
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown &&
                        (it.key == Key.DirectionUp || it.key == Key.DirectionDown || it.key == Key.DirectionLeft)) expanded = true
                    false
                }
                .padding(vertical = ZenithDimens.paddingL, horizontal = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Знак бренда: на месте и в покое, и в раскрытом виде.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = ZenithDimens.paddingM).height(48.dp)
            ) {
                BrandMark(Modifier.size(40.dp))
                AnimatedVisibility(expanded, enter = fadeIn(tween(160)), exit = fadeOut(tween(80))) {
                    Text(
                        "ZENITH",
                        modifier = Modifier.padding(start = 12.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                        maxLines = 1,
                    )
                }
            }

            // ФИКС: на ТВ с невысоким экраном (720p при плотности 1.33 ≈ 540 dp)
            // колонка не помещалась по высоте, и закреплённые внизу «Настройки»
            // уходили за край — выбрать их было нельзя. Пункты меню теперь в
            // прокручиваемой области (focus сам докручивает до выбранного),
            // а «Настройки» закреплены под ней и видны всегда.
            // Горизонтальный запас 6 dp — чтобы увеличенная при фокусе плитка не обрезалась краем прокрутки.
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
            var lastGroup: SidebarGroup? = null
            SidebarItem.values().forEach { item ->
                if (item.group != lastGroup && item.group.title != null) {
                    // Заголовок группы виден только раскрытым; в покое — тонкий разделитель,
                    // чтобы колонка иконок не слипалась в одну кучу.
                    if (expanded) {
                        Text(
                            item.group.title,
                            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp),
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    } else {
                        Spacer(Modifier.height(14.dp))
                    }
                }
                lastGroup = item.group

                val selected = currentRoute == item.route
                Surface(
                    onClick = { if (!selected) navController.navigateToTab(item.route) },
                    shape = ClickableSurfaceDefaults.shape(ZenithShapeMedium),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) colors.primary.copy(alpha = 0.3f) else Color.Transparent,
                        focusedContainerColor = colors.primary.copy(alpha = 0.5f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.padding(vertical = 3.dp).fillMaxWidth().height(52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(start = 14.dp)
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (selected) colors.primary else ZenithOnSurface,
                            modifier = Modifier.size(26.dp)
                        )
                        AnimatedVisibility(expanded, enter = fadeIn(tween(160)), exit = fadeOut(tween(60))) {
                            Text(
                                item.label,
                                modifier = Modifier.padding(start = 16.dp),
                                fontSize = 18.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) colors.primary else ZenithOnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }

            }

            // Настройки — отдельно внизу, как на макете.
            val settingsSelected = currentRoute == "settings"
            Surface(
                onClick = { if (!settingsSelected) navController.navigateToTab("settings") },
                shape = ClickableSurfaceDefaults.shape(ZenithShapeMedium),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (settingsSelected) colors.primary.copy(alpha = 0.3f) else Color.Transparent,
                    focusedContainerColor = colors.primary.copy(alpha = 0.5f)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                modifier = Modifier.padding(top = 6.dp, start = 6.dp, end = 6.dp).fillMaxWidth().height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(start = 14.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Настройки",
                        tint = if (settingsSelected) colors.primary else ZenithOnSurface,
                        modifier = Modifier.size(26.dp)
                    )
                    AnimatedVisibility(expanded, enter = fadeIn(tween(160)), exit = fadeOut(tween(60))) {
                        Text(
                            "Настройки",
                            modifier = Modifier.padding(start = 16.dp),
                            fontSize = 18.sp,
                            fontWeight = if (settingsSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (settingsSelected) colors.primary else ZenithOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
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
