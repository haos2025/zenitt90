package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.navigation.navigateToTab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum.ott.core.platform.ZenithDimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.settings.SettingsUiState
import com.platinum.ott.presentation.screens.settings.SettingsViewModel
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.SubtitlePreferences
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.ui.theme.ZenithError
import com.platinum.ott.ui.theme.ZenithSuccess
import com.platinum.ott.ui.theme.ZenithSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsScreen(navController: NavHostController, viewModel: SettingsViewModel = hiltViewModel()) {

Scaffold(bottomBar = { PhoneBottomBar(navController) }) { padding ->
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourcesCount by viewModel.sourcesCount.collectAsStateWithLifecycle()
    val lastSyncedAtMs by viewModel.lastSyncedAtMs.collectAsStateWithLifecycle()

    // Экран — обычный composable() в NavHost, не сохранённая вкладка, так
    // что при возврате сюда с "Источники"/"Синхронизация" он пересоздаётся
    // заново и это отрабатывает снова — статус в карточках не залипает на
    // значении, снятом при первом входе (см. SettingsViewModel.refreshStatusRows(),
    // тот же приём, что и на TV в SettingsScreen.kt).
    LaunchedEffect(Unit) { viewModel.refreshStatusRows() }

    Column(Modifier.padding(padding).background(MaterialTheme.colorScheme.background).padding(ZenithDimens.paddingM).verticalScroll(rememberScrollState())) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(ZenithDimens.paddingL))

        SectionHeader("Плагины", Icons.Outlined.Extension)
        NavCard(
            title = "Плагины",
            status = "Каталог и управление плагинами",
            onClick = { navController.navigate("plugins") }
        )
        Spacer(Modifier.height(ZenithDimens.paddingL))

        // Задача "Источники" (PROMPT_SOURCES_SCREEN.md) заменила единственный
        // источник (AuthPreferences, бинарное isConnected()) на список
        // PlaylistSource — карточка "Аккаунт" стала карточкой "Источники",
        // ведёт на полноценный список (SourcesScreen.kt) безусловно, без
        // отдельной ветки на sync_pairing (та развязка ниже — отдельная
        // карточка "Синхронизация устройств", независима от источников).
        // Статус ("N подключено"/"Не настроено") — то же значение, что и на
        // TV, см. SettingsViewModel.sourcesCount.
        SectionHeader("Источники", Icons.Outlined.Source)
        NavCard(
            title = "Источники",
            status = if (sourcesCount == 0) "Не настроено" else "$sourcesCount подключено",
            onClick = { navController.navigate("sources") }
        )
        Spacer(Modifier.height(ZenithDimens.paddingS))
        // Синхронизация — независимая система (пара по 6-значному коду,
        // AuthPreferences.getOrCreateSyncToken()), от источников не зависит,
        // всегда доступна отдельной карточкой (та же развязка, что и на TV
        // в SettingsScreen.kt). "Последняя: ..." — то же lastSyncTimestamp,
        // что уже показывает SyncPairingScreen.kt самому, просто здесь видно
        // не заходя внутрь.
        NavCard(
            title = "Синхронизация устройств",
            status = "Последняя: ${formatLastSynced(lastSyncedAtMs)}",
            onClick = { navController.navigate("sync_pairing") }
        )
        Spacer(Modifier.height(ZenithDimens.paddingL))

        // Телефон-компаньон (ROADMAP.md п.6, PROMPT_PHONE_COMPANION.md) — не
        // то же самое, что "Синхронизация устройств" выше: тот канал через
        // zenith-backend по 6-значному коду (SyncPairingViewModel), этот —
        // напрямую по локальной сети, без бэкенда (см. QrScanScreen.kt на TV).
        // Отдельная карточка, чтобы не путать два разных механизма.
        SectionHeader("Телефон-компаньон", Icons.Outlined.QrCode)
        NavCard(
            title = "Телефон-компаньон",
            status = "Отсканировать QR с TV — например, чтобы отправить ссылку на субтитры",
            onClick = { navController.navigate("qr_scan") }
        )
        Spacer(Modifier.height(ZenithDimens.paddingL))

        // Раньше на телефоне не было НИКАКОГО способа управлять кэшем —
        // даже той единственной блочной кнопки, что была на TV
        // (SettingsViewModel.clearCache(), теперь удалена в пользу этого
        // экрана — см. CacheManagementScreen.kt/PhoneCacheManagementScreen.kt).
        SectionHeader("Кэш", Icons.Outlined.Storage)
        NavCard(
            title = "Кэш",
            status = "Размер по категориям и очистка",
            onClick = { navController.navigate("cache_management") }
        )
        Spacer(Modifier.height(ZenithDimens.paddingL))

        // Раньше это была карточка-заглушка из общего списка без единого
        // обработчика нажатия. "Автовоспроизведение следующей серии" по-
        // прежнему не заведено отдельной настройкой — сама возможность
        // переключиться на следующий эпизод есть (PlayerViewModel.
        // playNextEpisode(), кнопки вместо перемотки при просмотре
        // сериала — см. PhonePlayerController.kt), но автозапуска БЕЗ
        // нажатия нет, это другая функция. "Субтитры по умолчанию" —
        // теперь реальная настройка (было убрано как выдуманное, пока не
        // было обработки субтитровых дорожек вообще, см. TrackOption/
        // selectSubtitleTrack) — управляет тем, пытается ли ExoPlayer
        // сразу показать субтитры, а не саму ссылку на .srt: та по-прежнему
        // вводится в плеере на конкретное видео (см. PhonePlayerController.kt) —
        // это не то же самое, что глобальный переключатель "вкл/выкл по умолчанию".
        SectionHeader("Воспроизведение", Icons.Outlined.PlayCircle)
        SettingsCard {
            val context = LocalContext.current
            val qualityPrefs = remember { QualityPreferences(context) }
            val qualityOptions = listOf("Авто", "1080p", "720p", "480p")
            var selectedQuality by remember { mutableStateOf(qualityPrefs.getSelectedQuality() ?: "Авто") }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Качество по умолчанию", color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val next = qualityOptions[(qualityOptions.indexOf(selectedQuality) + 1) % qualityOptions.size]
                    if (next == "Авто") qualityPrefs.clearSelectedQuality() else qualityPrefs.setSelectedQuality(next)
                    selectedQuality = next
                }) { Text(selectedQuality) }
            }
            CardDivider()
            val subtitlePrefs = remember { SubtitlePreferences(context) }
            var showSubsByDefault by remember { mutableStateOf(subtitlePrefs.getShowByDefault()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Субтитры по умолчанию", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = showSubsByDefault, onCheckedChange = {
                    showSubsByDefault = it
                    subtitlePrefs.setShowByDefault(it)
                })
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingL))

        // Раньше это была карточка без единого обработчика нажатия из
        // общего списка-заглушки — та же, что чинил на TV несколько шагов
        // назад, просто эта правка никогда не переносилась на телефон.
        // "Новый контент" сюда намеренно не включён — см. комментарий в
        // TV SettingsScreen.kt: в бэкенде нет поля "добавлено в каталог".
        SectionHeader("Уведомления", Icons.Outlined.Notifications)
        SettingsCard {
            val context = LocalContext.current
            val notifPrefs = remember { com.platinum.ott.core.NotificationPreferences(context) }
            var newEpisodesEnabled by remember { mutableStateOf(notifPrefs.isNewEpisodesEnabled()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Новые серии (избранные сериалы)", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = newEpisodesEnabled, onCheckedChange = { newEpisodesEnabled = it; notifPrefs.setNewEpisodesEnabled(it) })
            }
            CardDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Новый контент", color = Color.Gray, modifier = Modifier.weight(1f))
                Text("Скоро", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            CardDivider()
            var quietEnabled by remember { mutableStateOf(notifPrefs.isQuietHoursEnabled()) }
            var quietStart by remember { mutableStateOf(notifPrefs.getQuietStartHour()) }
            var quietEnd by remember { mutableStateOf(notifPrefs.getQuietEndHour()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Тихий режим", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = quietEnabled, onCheckedChange = { quietEnabled = it; notifPrefs.setQuietHoursEnabled(it) })
            }
            if (quietEnabled) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$quietStart:00–$quietEnd:00", color = Color.Gray, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { quietStart = (quietStart + 23) % 24; notifPrefs.setQuietHours(quietStart, quietEnd) }) { Text("− начало") }
                    Spacer(Modifier.width(ZenithDimens.paddingS))
                    OutlinedButton(onClick = { quietEnd = (quietEnd + 1) % 24; notifPrefs.setQuietHours(quietStart, quietEnd) }) { Text("+ конец") }
                }
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingL))

        // Раньше раздела "Сеть" на телефоне не было вообще ни в каком
        // виде — ни рабочего, ни даже карточки-заглушки. На TV уже есть.
        SectionHeader("Сеть", Icons.Outlined.Wifi)
        SettingsCard {
            val context = LocalContext.current
            val networkPrefs = remember { com.platinum.ott.core.NetworkPreferences(context) }
            val qualityPrefsNet = remember { QualityPreferences(context) }
            val timeoutOptions = listOf(10, 15, 20, 30)
            var timeoutSeconds by remember { mutableStateOf(networkPrefs.getTimeoutSeconds()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Таймаут запроса", color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val idx = timeoutOptions.indexOf(timeoutSeconds).let { if (it == -1) 0 else it }
                    timeoutSeconds = timeoutOptions[(idx + 1) % timeoutOptions.size]
                    networkPrefs.setTimeoutSeconds(timeoutSeconds)
                    viewModel.applyNetworkTimeoutChange()
                }) { Text("$timeoutSeconds сек") }
            }
            CardDivider()
            val mobileQualityOptions = listOf("480p", "720p", "1080p", "Без ограничений")
            var maxMobileQuality by remember { mutableStateOf(qualityPrefsNet.getMaxQualityOnMobile()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Макс. качество на моб. данных", color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val idx = mobileQualityOptions.indexOf(maxMobileQuality).let { if (it == -1) 0 else it }
                    maxMobileQuality = mobileQualityOptions[(idx + 1) % mobileQualityOptions.size]
                    qualityPrefsNet.setMaxQualityOnMobile(maxMobileQuality)
                }) { Text(maxMobileQuality) }
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingL))

        // Раньше это была ещё одна карточка без единого обработчика нажатия
        // из общего списка-заглушки. Тема теперь реально переключает
        // ZenithTheme (см. MainActivity.kt/ThemeManager) — язык оставлен
        // видимым, но намеренно не нажимается: реальный языковой
        // переключатель потребовал бы вынести весь текст интерфейса в
        // string-ресурсы (сейчас 0 использований stringResource во всём
        // проекте) — отдельная большая задача, не в этом заходе.
        SectionHeader("Интерфейс", Icons.Outlined.Palette)
        SettingsCard {
            val darkTheme by viewModel.darkThemeFlow.collectAsState()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Тёмная тема", color = Color.White, modifier = Modifier.weight(1f))
                Switch(checked = darkTheme, onCheckedChange = { viewModel.setDarkTheme(it) })
            }
            CardDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Язык", color = Color.Gray, modifier = Modifier.weight(1f))
                Text("Скоро", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingXL))

        // Технические действия — паритет с TV (PROMPT_SETTINGS_UPGRADE.md
        // п.1): раньше SettingsViewModel.runOtaUpdate()/uiState были доступны
        // и на телефоне через тот же общий SettingsViewModel, но ни одна
        // кнопка здесь их не вызывала — "Обновить парсеры" существовала
        // только на TV. "Очистить кэш"/"Сменить аккаунт" сюда не добавляю:
        // первое — уже отдельный экран выше ("Кэш"), второе — понятие
        // одного аккаунта удалено вместе с AuthRepository, см.
        // SettingsViewModel.kt.
        Text("ТЕХНИЧЕСКОЕ", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(ZenithDimens.paddingS))
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
        // Раньше uiState (Loading/Success/Error) велось во ViewModel, но
        // нигде не отображалось — единственным признаком того, что кнопка
        // вообще что-то сделала, было отсутствие краша. Теперь исход виден
        // явно под кнопкой, тем же текстом, что и на TV.
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
        Spacer(Modifier.height(ZenithDimens.paddingL))
    }
}
}

// "N мин/ч/дн назад" — та же приблизительная точность, что и остальные
// текстовые значения на этом экране, не претендует на грамматически верное
// согласование числительных для всех N. Идентична TV-версии в SettingsScreen.kt
// (не вынесена в общий файл, чтобы не создавать новый модуль ради одной функции).
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

// Иконка + подпись капсом над карточкой — тот же визуальный язык, что и в
// новом SettingsScreen.kt на TV (см. SettingsGroup там), чтобы секции
// читались одинаково на обеих платформах.
@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = ZenithDimens.paddingXS, bottom = ZenithDimens.paddingXS)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(ZenithDimens.paddingXS))
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
    }
}

// Карточка-контейнер для многострочных секций (Воспроизведение/Уведомления/
// Сеть/Интерфейс) — скругление + лёгкая обводка вместо стандартной плоской
// Card без обрамления, ряды внутри разделены CardDivider().
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ZenithSurfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(ZenithDimens.paddingM), content = content)
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = ZenithDimens.paddingSM))
}

// Карточка-переход (Плагины/Источники/Синхронизация/Телефон-компаньон/Кэш) —
// заголовок + статус слева, стрелка справа, вся карточка кликабельна. Тот
// же паттерн, что и NavRow на TV, просто как отдельная Card (на телефоне
// нет фокуса пультом, которому нужна была бы строка-на-всю-ширину внутри
// общей карточки — каждая тут самостоятельна).
@Composable
private fun NavCard(title: String, status: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ZenithSurfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(Modifier.fillMaxWidth().padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(status, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
