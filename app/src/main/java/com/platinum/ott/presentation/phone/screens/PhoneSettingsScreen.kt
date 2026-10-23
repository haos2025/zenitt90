package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.platinum.ott.core.CONTROLS_HIDE_DELAY_OPTIONS_MS
import com.platinum.ott.core.LiveArrowMode
import com.platinum.ott.core.PROGRESS_STRIP_DP_OPTIONS
import com.platinum.ott.core.formatControlsHideDelay
import com.platinum.ott.core.nextAfter
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.settings.SettingsUiState
import com.platinum.ott.presentation.screens.settings.SettingsViewModel
import com.platinum.ott.presentation.settings.SettingsCategory
import com.platinum.ott.ui.theme.ZenithError
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithShapeMedium
import com.platinum.ott.ui.theme.ZenithSuccess
import com.platinum.ott.ui.theme.ZenithSurfaceVariant

// Телефон: корневой экран настроек — список категорий (план 09), по нажатию —
// страница категории (маршрут settings_page/{cat}). Список и порядок те же, что на ТВ.
@Composable
fun PhoneSettingsScreen(navController: NavHostController) {
    Scaffold(bottomBar = { PhoneBottomBar(navController) }) { padding ->
        Column(
            Modifier.padding(padding).background(MaterialTheme.colorScheme.background)
                .padding(ZenithDimens.paddingM).verticalScroll(rememberScrollState())
        ) {
            Text("Настройки", style = MaterialTheme.typography.headlineLarge, color = ZenithOnSurface)
            Spacer(Modifier.height(ZenithDimens.paddingL))
            SettingsCategory.entries.forEach { cat ->
                NavCard(cat.title, cat.subtitle, cat.icon) { navController.navigate("settings_page/${cat.id}") }
                Spacer(Modifier.height(ZenithDimens.paddingS))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsCategoryScreen(navController: NavHostController, categoryId: String?, viewModel: SettingsViewModel = hiltViewModel()) {
    val category = SettingsCategory.fromId(categoryId)
    // Экран пересоздаётся при возврате с «Источники»/«Синхронизация» — статусы обновляем заново.
    LaunchedEffect(Unit) { viewModel.refreshStatusRows() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = { PhoneBottomBar(navController) }
    ) { padding ->
        Column(
            Modifier.padding(padding).background(MaterialTheme.colorScheme.background)
                .padding(ZenithDimens.paddingM).verticalScroll(rememberScrollState())
        ) {
            CategoryPage(category, navController, viewModel)
            Spacer(Modifier.height(ZenithDimens.paddingL))
        }
    }
}

@Composable
private fun CategoryPage(category: SettingsCategory, navController: NavHostController, viewModel: SettingsViewModel) {
    when (category) {
        SettingsCategory.PLAYBACK -> {
            val quality by viewModel.quality.collectAsStateWithLifecycle()
            val subsByDefault by viewModel.subsByDefault.collectAsStateWithLifecycle()
            val preferAccuracy by viewModel.preferAccuracy.collectAsStateWithLifecycle()
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Качество по умолчанию", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.cycleQuality() }) { Text(quality) }
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Субтитры по умолчанию", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Switch(checked = subsByDefault, onCheckedChange = { viewModel.setSubsByDefault(it) })
                }
                CardDivider()
                // Влияет только на локальный Whisper-фолбэк автосубтитров.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Автосубтитры (офлайн)", color = ZenithOnSurface)
                        Text(if (preferAccuracy) "Точность" else "Скорость", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = preferAccuracy, onCheckedChange = { viewModel.setPreferAccuracy(it) })
                }
            }
        }
        SettingsCategory.PLAYER -> {
            val overlay by viewModel.playerOverlay.collectAsState()
            var delayMenu by remember { mutableStateOf(false) }
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Автоскрытие управления", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { delayMenu = true }) { Text(formatControlsHideDelay(overlay.hideDelayMs ?: 3000)) }
                        DropdownMenu(expanded = delayMenu, onDismissRequest = { delayMenu = false }) {
                            CONTROLS_HIDE_DELAY_OPTIONS_MS.forEach { ms ->
                                DropdownMenuItem(
                                    text = { Text(formatControlsHideDelay(ms)) },
                                    onClick = { viewModel.setControlsHideDelayMs(ms); delayMenu = false }
                                )
                            }
                        }
                    }
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Не скрывать на паузе", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Switch(checked = overlay.keepControlsWhenPaused, onCheckedChange = { viewModel.setKeepControlsWhenPaused(it) })
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Полоска прогресса", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Switch(checked = overlay.showProgressStrip, onCheckedChange = { viewModel.setShowProgressStrip(it) })
                }
                if (overlay.showProgressStrip) {
                    CardDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Толщина", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                        PROGRESS_STRIP_DP_OPTIONS.forEach { dp ->
                            FilterChip(
                                selected = overlay.progressStripDp == dp,
                                onClick = { viewModel.setProgressStripDp(dp) },
                                label = { Text("$dp") },
                                modifier = Modifier.padding(start = ZenithDimens.paddingXS)
                            )
                        }
                    }
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Показывать прогрузку", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Switch(checked = overlay.showBuffered, onCheckedChange = { viewModel.setShowBuffered(it) })
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            val modes = LiveArrowMode.values().toList()
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Стрелки в эфире", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.setLiveArrowMode(modes.nextAfter(overlay.liveArrowMode)) }) { Text(overlay.liveArrowMode.title) }
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("«Назад» закрывает все окна плеера", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Switch(checked = overlay.backClosesAll, onCheckedChange = { viewModel.setBackClosesAll(it) })
                }
            }
        }
        SettingsCategory.APPEARANCE -> {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            var confirmReset by remember { mutableStateOf(false) }
            Box(Modifier.padding(bottom = ZenithDimens.paddingM)) {
                com.platinum.ott.presentation.components.MovieCard(title = "Пример карточки", poster = "", year = 2024, onClick = {}, rating = 7.8, progress = 0.4f)
            }
            SettingsCard {
                Text("Размер элементов", color = ZenithOnSurface)
                Row(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingS).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)) {
                    com.platinum.ott.ui.theme.SIZE_STEP_TITLES.forEachIndexed { i, title ->
                        FilterChip(selected = appearance.sizeStep == i, onClick = { viewModel.setSizeStep(i) }, label = { Text(title, maxLines = 1) })
                    }
                }
                CardDivider()
                Text("Акцентный цвет", color = ZenithOnSurface)
                Row(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingS), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                    com.platinum.ott.ui.theme.AccentColor.entries.forEach { a ->
                        Box(
                            Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(a.color)
                                .border(if (appearance.accent == a) 3.dp else 0.dp, ZenithOnSurface, androidx.compose.foundation.shape.CircleShape)
                                .clickable { viewModel.setAccent(a) }
                        )
                    }
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Картинки в карточках", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.cycleImageScale() }) { Text(appearance.imageScale.title) }
                }
                CardDivider()
                TextButton(onClick = { confirmReset = true }) { Text("Сбросить внешний вид") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            // «Язык» — заглушка: нужен вынос текста в string-ресурсы.
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Язык", color = ZenithOnSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Скоро", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (confirmReset) {
                AlertDialog(
                    onDismissRequest = { confirmReset = false },
                    title = { Text("Сбросить внешний вид?") },
                    text = { Text("Размер, акцент и картинки вернутся к исходным. Настройки плеера не изменятся.") },
                    confirmButton = { TextButton(onClick = { viewModel.resetAppearance(); confirmReset = false }) { Text("Сбросить") } },
                    dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
                )
            }
        }
        SettingsCategory.NETWORK -> {
            val timeout by viewModel.timeoutSeconds.collectAsStateWithLifecycle()
            val maxMobile by viewModel.maxMobileQuality.collectAsStateWithLifecycle()
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Таймаут запроса", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.cycleTimeout() }) { Text("$timeout сек") }
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Макс. качество на моб. данных", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Button(onClick = { viewModel.cycleMaxMobileQuality() }) { Text(maxMobile) }
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            NavCard("Управление кэшем", "Размер по категориям и очистка") { navController.navigate("cache_management") }
        }
        SettingsCategory.NOTIFICATIONS -> {
            val newEpisodes by viewModel.newEpisodes.collectAsStateWithLifecycle()
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Новые серии (избранные сериалы)", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Switch(checked = newEpisodes, onCheckedChange = { viewModel.setNewEpisodes(it) })
                }
                CardDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Новый контент", color = ZenithOnSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Скоро", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SettingsCategory.SOURCES -> {
            val sourcesCount by viewModel.sourcesCount.collectAsStateWithLifecycle()
            NavCard(
                "Все источники (каналы, фильмы, сериалы)",
                if (sourcesCount == 0) "Не настроено" else "$sourcesCount подключено"
            ) { navController.navigate("sources") }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            NavCard("Телепрограмма (EPG)", "Статус и обновление") { navController.navigate("epg_settings") }
        }
        SettingsCategory.PLUGINS -> {
            NavCard("Плагины", "Каталог и управление плагинами") { navController.navigate("plugins") }
        }
        SettingsCategory.DEVICES -> {
            val lastSyncedAtMs by viewModel.lastSyncedAtMs.collectAsStateWithLifecycle()
            NavCard("Синхронизация устройств", "Последняя: ${formatLastSynced(lastSyncedAtMs)}") { navController.navigate("sync_pairing") }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            // Не то же, что синхронизация: этот канал — напрямую по локальной сети, без бэкенда.
            NavCard("Телефон-компаньон", "Отсканировать QR с TV — например, чтобы отправить ссылку на субтитры") { navController.navigate("qr_scan") }
        }
        SettingsCategory.MAINTENANCE -> {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            OutlinedButton(
                onClick = { viewModel.runOtaUpdate() },
                enabled = uiState !is SettingsUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is SettingsUiState.Loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(ZenithDimens.paddingXS))
                }
                Text("Обновить парсеры")
            }
            when (val state = uiState) {
                is SettingsUiState.Success -> {
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Text("Готово: обновлено ${state.result.updated}, пропущено ${state.result.skipped}, ошибок ${state.result.failed}", color = ZenithSuccess, style = MaterialTheme.typography.bodySmall)
                }
                is SettingsUiState.Error -> {
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Text("Ошибка: ${state.message}", color = ZenithError, style = MaterialTheme.typography.bodySmall)
                }
                else -> {}
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            val context = androidx.compose.ui.platform.LocalContext.current
            val versionName = remember {
                try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—" } catch (_: Exception) { "—" }
            }
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Версия приложения", color = ZenithOnSurface, modifier = Modifier.weight(1f))
                    Text(versionName, color = ZenithOnSurfaceVariant)
                }
            }
        }
    }
}

// "N мин/ч/дн назад" — приблизительная точность, как и на ТВ (SettingsScreen.kt).
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

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ZenithShapeMedium,
        colors = CardDefaults.cardColors(containerColor = ZenithSurfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, ZenithOnSurface.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(ZenithDimens.paddingM), content = content)
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = ZenithOnSurface.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = ZenithDimens.paddingSM))
}

// Карточка-переход: заголовок + статус слева, стрелка справа, вся карточка кликабельна.
@Composable
private fun NavCard(title: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ZenithShapeMedium,
        colors = CardDefaults.cardColors(containerColor = ZenithSurfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, ZenithOnSurface.copy(alpha = 0.08f))
    ) {
        Row(Modifier.fillMaxWidth().padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(ZenithDimens.paddingM))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface)
                Text(status, color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = ZenithOnSurfaceVariant)
        }
    }
}
