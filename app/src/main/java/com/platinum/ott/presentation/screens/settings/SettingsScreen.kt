package com.platinum.ott.presentation.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import com.platinum.ott.core.CONTROLS_HIDE_DELAY_OPTIONS_MS
import com.platinum.ott.core.LiveArrowMode
import com.platinum.ott.core.PROGRESS_STRIP_DP_OPTIONS
import com.platinum.ott.core.formatControlsHideDelay
import com.platinum.ott.core.nextAfter
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.presentation.components.NavSidebar
import com.platinum.ott.presentation.settings.SettingsCategory
import com.platinum.ott.ui.theme.*

// ТВ: настройки по категориям (план 09). Слева — колонка категорий, справа — строки выбранной.
// Категория выбирается фокусом; Вправо/OK — в правую панель; Влево/Назад — обратно на выбранную категорию.
// Ключи SharedPreferences и логика не менялись — только расположение строк.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, onCacheManagementClick: () -> Unit, onForceOtaUpdateClick: () -> Unit, onPluginsClick: () -> Unit = {}, onSyncClick: () -> Unit = {}, onSourcesClick: () -> Unit = {}, onEpgClick: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    var selectedId by rememberSaveable { mutableStateOf(SettingsCategory.PLAYBACK.id) }
    val category = SettingsCategory.fromId(selectedId)
    val categoryRequesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val panelRequester = remember { FocusRequester() }
    var panelHasFocus by remember { mutableStateOf(false) }

    // Экран пересоздаётся при возврате с «Источники»/«Синхронизация» — статусы обновляем заново.
    LaunchedEffect(Unit) {
        viewModel.refreshStatusRows()
        withFrameNanos { }
        categoryRequesters[SettingsCategory.fromId(selectedId)]?.safeRequestFocus()
    }
    // Назад из правой панели — на выбранную категорию; со списка категорий — обычный выход.
    BackHandler(enabled = panelHasFocus) { categoryRequesters[category]?.safeRequestFocus() }

    Row(Modifier.fillMaxSize()) {
        NavSidebar(navController)
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = ZenithDimens.paddingL, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXL)
        ) {
            Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("Настройки", style = MaterialTheme.typography.displaySmall, color = ZenithOnSurface)
                Spacer(Modifier.height(ZenithDimens.paddingL))
                SettingsCategory.entries.forEach { cat ->
                    CategoryItem(
                        category = cat,
                        selected = cat == category,
                        requester = categoryRequesters.getValue(cat),
                        panelRequester = panelRequester,
                        onFocused = { selectedId = cat.id }
                    )
                }
            }
            Spacer(Modifier.width(ZenithDimens.paddingL))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(panelRequester)
                    // Влево из любой строки панели — на выбранную категорию, а не на ближайшую по геометрии.
                    .focusProperties { left = categoryRequesters.getValue(category) }
                    .onFocusChanged { panelHasFocus = it.hasFocus }
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(category.title, style = MaterialTheme.typography.headlineMedium, color = ZenithOnSurface)
                Text(category.subtitle, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurfaceVariant)
                Spacer(Modifier.height(ZenithDimens.paddingM))
                CategoryPage(
                    category = category,
                    viewModel = viewModel,
                    onCacheManagementClick = onCacheManagementClick,
                    onForceOtaUpdateClick = onForceOtaUpdateClick,
                    onPluginsClick = onPluginsClick,
                    onSyncClick = onSyncClick,
                    onSourcesClick = onSourcesClick,
                    onEpgClick = onEpgClick
                )
                Spacer(Modifier.height(ZenithDimens.paddingXL))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryItem(category: SettingsCategory, selected: Boolean, requester: FocusRequester, panelRequester: FocusRequester, onFocused: () -> Unit) {
    Surface(
        onClick = { panelRequester.safeRequestFocus() },
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .focusRequester(requester)
            // Вправо с категории — всегда в первую строку панели, а не в ближайшую по высоте.
            .focusProperties { right = panelRequester }
            .onFocusChanged { if (it.isFocused) onFocused() }
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(category.icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else ZenithOnSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(ZenithDimens.paddingSM))
            Text(category.title, color = ZenithOnSurface, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryPage(
    category: SettingsCategory,
    viewModel: SettingsViewModel,
    onCacheManagementClick: () -> Unit,
    onForceOtaUpdateClick: () -> Unit,
    onPluginsClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onEpgClick: () -> Unit
) {
    when (category) {
        SettingsCategory.PLAYBACK -> {
            val quality by viewModel.quality.collectAsStateWithLifecycle()
            val subsByDefault by viewModel.subsByDefault.collectAsStateWithLifecycle()
            val preferAccuracy by viewModel.preferAccuracy.collectAsStateWithLifecycle()
            SettingsCard {
                CycleSetting("Качество по умолчанию", quality) { viewModel.cycleQuality() }
                SettingsDivider()
                CycleSetting("Субтитры по умолчанию", if (subsByDefault) "Вкл" else "Выкл") { viewModel.setSubsByDefault(!subsByDefault) }
                // Влияет только на локальный Whisper-фолбэк автосубтитров — облачный путь настройку не читает.
                CycleSetting("Автосубтитры (офлайн)", if (preferAccuracy) "Точность" else "Скорость") { viewModel.setPreferAccuracy(!preferAccuracy) }
            }
        }
        SettingsCategory.PLAYER -> {
            val overlay by viewModel.playerOverlay.collectAsState()
            val hideDelay = overlay.hideDelayMs ?: 0
            SettingsCard {
                CycleSetting("Автоскрытие управления", formatControlsHideDelay(hideDelay)) {
                    viewModel.setControlsHideDelayMs(CONTROLS_HIDE_DELAY_OPTIONS_MS.nextAfter(hideDelay))
                }
                SettingsDivider()
                CycleSetting("Не скрывать на паузе", if (overlay.keepControlsWhenPaused) "Да" else "Нет") {
                    viewModel.setKeepControlsWhenPaused(!overlay.keepControlsWhenPaused)
                }
                SettingsDivider()
                CycleSetting("Полоска прогресса", if (overlay.showProgressStrip) "Вкл" else "Выкл") {
                    viewModel.setShowProgressStrip(!overlay.showProgressStrip)
                }
                if (overlay.showProgressStrip) {
                    SettingsDivider()
                    CycleSetting("Толщина полоски", "${overlay.progressStripDp} dp") {
                        viewModel.setProgressStripDp(PROGRESS_STRIP_DP_OPTIONS.nextAfter(overlay.progressStripDp))
                    }
                }
                SettingsDivider()
                CycleSetting("Показывать прогрузку", if (overlay.showBuffered) "Да" else "Нет") {
                    viewModel.setShowBuffered(!overlay.showBuffered)
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            // Бывшая группа «Управление пультом».
            val modes = LiveArrowMode.values().toList()
            SettingsCard {
                CycleSetting("Стрелки в эфире", overlay.liveArrowMode.title) {
                    viewModel.setLiveArrowMode(modes.nextAfter(overlay.liveArrowMode))
                }
                SettingsDivider()
                CycleSetting("«Назад» в окнах плеера", if (overlay.backClosesAll) "Закрывает всё сразу" else "По шагам") {
                    viewModel.setBackClosesAll(!overlay.backClosesAll)
                }
            }
        }
        SettingsCategory.APPEARANCE -> {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            var confirmReset by remember { mutableStateOf(false) }
            // Пример карточки с текущими размером/акцентом — не фокусируется, чтобы не мешать D-pad.
            Box(Modifier.focusProperties { canFocus = false }.padding(bottom = ZenithDimens.paddingM)) {
                com.platinum.ott.presentation.components.MovieCard(title = "Пример карточки", poster = "", year = 2024, onClick = {}, rating = 7.8, progress = 0.4f)
            }
            SettingsCard {
                CycleSetting("Размер элементов", com.platinum.ott.ui.theme.SIZE_STEP_TITLES[appearance.sizeStep]) { viewModel.cycleSizeStep() }
                SettingsDivider()
                CycleSetting("Акцентный цвет", appearance.accent.title) { viewModel.cycleAccent() }
                SettingsDivider()
                CycleSetting("Картинки в карточках", appearance.imageScale.title) { viewModel.cycleImageScale() }
                SettingsDivider()
                NavRow(
                    if (confirmReset) "Нажмите ещё раз — сбросить" else "Сбросить внешний вид",
                    "Размер, акцент и картинки вернутся к исходным",
                    arrow = false
                ) {
                    if (confirmReset) { viewModel.resetAppearance(); confirmReset = false } else confirmReset = true
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            // «Язык» оставлен заглушкой: реальный переключатель требует вынести весь текст в string-ресурсы.
            SettingsCard { SettingsItem("Язык", "Скоро") }
        }
        SettingsCategory.NETWORK -> {
            val timeout by viewModel.timeoutSeconds.collectAsStateWithLifecycle()
            val maxMobile by viewModel.maxMobileQuality.collectAsStateWithLifecycle()
            SettingsCard {
                CycleSetting("Таймаут запроса", "$timeout сек") { viewModel.cycleTimeout() }
                SettingsDivider()
                CycleSetting("Макс. качество на моб. данных", maxMobile) { viewModel.cycleMaxMobileQuality() }
                SettingsDivider()
                NavRow("Управление кэшем", "Размер по категориям и очистка", onClick = onCacheManagementClick)
            }
        }
        SettingsCategory.NOTIFICATIONS -> {
            val newEpisodes by viewModel.newEpisodes.collectAsStateWithLifecycle()
            SettingsCard {
                // Сериал должен быть в Избранном, чтобы отслеживаться.
                CycleSetting("Новые серии (избранные сериалы)", if (newEpisodes) "Вкл" else "Выкл") { viewModel.setNewEpisodes(!newEpisodes) }
                SettingsDivider()
                SettingsItem("Новый контент", "Скоро")
            }
        }
        SettingsCategory.SOURCES -> {
            val sourcesCount by viewModel.sourcesCount.collectAsStateWithLifecycle()
            SettingsCard {
                NavRow(
                    "Все источники (каналы, фильмы, сериалы)",
                    if (sourcesCount == 0) "Не настроено" else "$sourcesCount подключено",
                    onClick = onSourcesClick
                )
                SettingsDivider()
                NavRow("Телепрограмма (EPG)", "Статус и обновление", onClick = onEpgClick)
            }
        }
        SettingsCategory.PLUGINS -> {
            SettingsCard { NavRow("Управление плагинами", "Каталог и настройки", onClick = onPluginsClick) }
        }
        SettingsCategory.DEVICES -> {
            val lastSyncedAtMs by viewModel.lastSyncedAtMs.collectAsStateWithLifecycle()
            SettingsCard { NavRow("Синхронизация", "Последняя: ${formatLastSynced(lastSyncedAtMs)}", onClick = onSyncClick) }
        }
        SettingsCategory.MAINTENANCE -> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val versionName = remember {
                try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" } catch (_: Exception) { "—" }
            }
            val otaStatus = when (val state = uiState) {
                is SettingsUiState.Loading -> "Обновление…"
                is SettingsUiState.Success -> "Готово: обновлено ${state.result.updated}, пропущено ${state.result.skipped}, ошибок ${state.result.failed}"
                is SettingsUiState.Error -> "Ошибка: ${state.message}"
                else -> "Скачать свежие версии парсеров"
            }
            SettingsCard {
                NavRow("Обновить парсеры", otaStatus, arrow = false) {
                    if (uiState !is SettingsUiState.Loading) { viewModel.runOtaUpdate(); onForceOtaUpdateClick() }
                }
                SettingsDivider()
                SettingsItem("Версия приложения", versionName)
            }
        }
    }
}

// "N мин/ч/дн назад" — та же приблизительная точность, что и остальные
// текстовые значения на этом экране ("15 сек", "720p"), не претендует на
// грамматически верное согласование числительных для всех N.
private fun formatLastSynced(ms: Long): String {
    if (ms <= 0L) return "никогда"
    val diffMin = (System.currentTimeMillis() - ms) / 60000
    return when {
        diffMin < 1 -> "только что"
        diffMin < 60 -> "$diffMin мин назад"
        diffMin < 60 * 24 -> "${diffMin / 60} ч назад"
        else -> "${diffMin / (60 * 24)} дн назад"
    }
}

// Карточка группы строк внутри страницы категории: скругление, лёгкий фон и обводка,
// ряды внутри разделены SettingsDivider().
@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZenithShapeMedium)
            .background(ZenithSurfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, ZenithOnSurface.copy(alpha = 0.08f), ZenithShapeMedium)
            .padding(vertical = ZenithDimens.paddingXS, horizontal = ZenithDimens.paddingS),
        content = content
    )
}
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsDivider() {
    HorizontalDivider(color = ZenithOnSurface.copy(alpha = 0.08f))
}
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp)) { Text(label, color = ZenithOnSurface, modifier = Modifier.weight(1f)); Text(value, color = ZenithOnSurfaceVariant) }
}
// Строка-переход (было: отдельная Button вне секции — SettingsSection(...)
// рисовала только заголовок, а кнопка "Плагины"/"Источники"/"Синхронизация"
// шла отдельным Row ПОСЛЕ, визуально не читаясь как часть той же карточки).
// Теперь это ряд ВНУТРИ карточки, со статусом и стрелкой, тот же паттерн
// фокусируемой строки целиком, что и у CycleSetting.
//
// scale = ClickableSurfaceDefaults.scale(focusedScale = 1f) — по умолчанию
// у tv-material3 Surface фокус ещё и увеличивает сам компонент (~10%,
// стандартный TV-эффект "pop"), рассчитанный на карточки с гуттерами
// вокруг (постеры в сетке каталога, где вокруг есть запас). Тут ряд на
// всю ширину карточки почти впритык к её скруглённой рамке — при
// увеличении на фокусе строка вылезала за пределы этой рамки и даже за
// край экрана (реальный скриншот с TV: подсветка и текст обрезаны). Тот
// же фикс — в CycleSetting ниже.
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun NavRow(label: String, status: String, arrow: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = ZenithOnSurface)
                Text(status, color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (arrow) Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = ZenithOnSurfaceVariant)
        }
    }
}
// Раньше это была Row с Text + маленькой Button, прижатой к правому краю —
// с пульта фокус-цель получалась узкой (только сама кнопка), а строки шли
// плотно (vertical = 4.dp), из-за чего было тяжело понять, какая строка
// сейчас в фокусе, и легко было промахнуться курсором мимо кнопки на
// соседнюю строку. Теперь фокусируемая область — вся строка целиком
// (Surface с onClick), с явной подсветкой фона в фокусе и высотой,
// достаточной для уверенного попадания D-pad'ом.
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun CycleSetting(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(ZenithShapeSmall),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        // См. комментарий у NavRow выше — тот же оверфлоу подсветки за край
        // скруглённой карточки/экрана на реальном TV, тот же фикс.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = ZenithOnSurface, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
    }
}
