package com.platinum.ott.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.NetworkPreferences
import com.platinum.ott.core.NotificationPreferences
import com.platinum.ott.core.SubtitlePreferences
import com.platinum.ott.presentation.components.NavSidebar
import com.platinum.ott.ui.theme.*

// PROMPT_NAVIGATION_SIDEBAR.md — добавлен navController для постоянного
// сайдбара; остальные параметры (переходы на не-табовые экраны — кэш,
// плагины, синхронизация, подключение источника) не трогаем.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, onCacheManagementClick: () -> Unit, onForceOtaUpdateClick: () -> Unit, onLogoutClick: () -> Unit, onPluginsClick: () -> Unit = {}, onSyncClick: () -> Unit = {}, onConnectSourceClick: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Row(Modifier.fillMaxSize()) {
        NavSidebar(navController)
        // Раньше Column был fillMaxSize() без verticalScroll() — тот же баг,
        // что чинили на телефоне (PhoneSettingsScreen.kt), просто не перенесённый
        // на TV: контент, не помещающийся по высоте (а тут 6 секций + кнопки),
        // просто обрезался снизу экрана, ничего не докрутить. Паддинг был
        // только start/top — без end/bottom нет overscan-запаса, часть TV
        // физически обрезает пару % по краям картинки. weight(1f) на нижнем
        // Spacer убран: в скроллящемся Column он несовместим (бесконечная
        // высота) и раньше просто держал кнопки прижатыми к низу — при скролле
        // это не нужно, они и так доступны докруткой.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXXL)
        ) {
            Text("Настройки", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(ZenithDimens.paddingXL))
            // Sections: Playback, Notifications, Network, Interface, Account, About
            SettingsSection("Воспроизведение") {
                // Раньше "Качество по умолчанию"/"Автовоспроизведение"/"Субтитры"
                // были тремя захардкоженными строками подряд, и для двух из них
                // не было НИКАКОЙ реализации (ни "следующего эпизода", ни
                // обработки субтитровых дорожек) — убрали как выдуманные
                // настройки. Теперь оба реализованы: следующий/предыдущий эпизод
                // — PlayerViewModel.playNextEpisode()/playPreviousEpisode()
                // (кнопки в PlayerController.kt/PhonePlayerController.kt вместо
                // перемотки при просмотре сериала), субтитры — TrackOption/
                // selectSubtitleTrack. "Автовоспроизведение следующей серии"
                // по-прежнему не заведено отдельной настройкой — сама
                // возможность есть, но нет автозапуска без нажатия, это другая
                // функция, не переименование того, что уже добавлено.
                val qualityPrefs = remember { QualityPreferences(context) }
                val qualityOptions = listOf("Авто", "1080p", "720p", "480p")
                var selectedQuality by remember { mutableStateOf(qualityPrefs.getSelectedQuality() ?: "Авто") }
                CycleSetting("Качество по умолчанию", selectedQuality) {
                    val next = qualityOptions[(qualityOptions.indexOf(selectedQuality) + 1) % qualityOptions.size]
                    if (next == "Авто") qualityPrefs.clearSelectedQuality() else qualityPrefs.setSelectedQuality(next)
                    selectedQuality = next
                }
                // Раньше настройки внешних субтитров не было нигде, кроме самого
                // плеера (ссылка на .srt вводилась заново на каждое видео) — это
                // единственная ЧАСТЬ субтитров, которая действительно глобальна
                // (не привязана к конкретному фильму), поэтому именно она здесь,
                // а не сам ввод ссылки — тот остаётся в плеере, см. комментарий
                // в PhonePlayerController.kt.
                val subtitlePrefs = remember { SubtitlePreferences(context) }
                var showSubsByDefault by remember { mutableStateOf(subtitlePrefs.getShowByDefault()) }
                CycleSetting("Субтитры по умолчанию", if (showSubsByDefault) "Вкл" else "Выкл") {
                    showSubsByDefault = !showSubsByDefault
                    subtitlePrefs.setShowByDefault(showSubsByDefault)
                }
            }
            SettingsSection("Уведомления") {
                // Раньше все три строки ("Новые серии: Вкл", "Новый контент: Вкл",
                // "Тихий режим: 23:00-08:00") были захардкожены и ни на что не
                // влияли. Теперь "Новые серии"/"Тихий режим" реально управляют
                // SeriesUpdateWorker (см. NotificationPreferences.kt) — сериал
                // должен быть в Избранном, чтобы отслеживаться.
                // "Новый контент" оставлен как "Скоро" — в бэкенде физически нет
                // сигнала "добавлено в каталог", реализовать нечем, не стал
                // изображать рабочую настройку.
                val notifPrefs = remember { NotificationPreferences(context) }
                var newEpisodesEnabled by remember { mutableStateOf(notifPrefs.isNewEpisodesEnabled()) }
                CycleSetting("Новые серии (избранные сериалы)", if (newEpisodesEnabled) "Вкл" else "Выкл") {
                    newEpisodesEnabled = !newEpisodesEnabled
                    notifPrefs.setNewEpisodesEnabled(newEpisodesEnabled)
                }
                SettingsItem("Новый контент", "Скоро")
                var quietEnabled by remember { mutableStateOf(notifPrefs.isQuietHoursEnabled()) }
                var quietStart by remember { mutableStateOf(notifPrefs.getQuietStartHour()) }
                var quietEnd by remember { mutableStateOf(notifPrefs.getQuietEndHour()) }
                CycleSetting("Тихий режим", if (quietEnabled) "${quietStart}:00–${quietEnd}:00" else "Выкл") {
                    quietEnabled = !quietEnabled
                    notifPrefs.setQuietHoursEnabled(quietEnabled)
                }
                if (quietEnabled) {
                    Row(Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS), verticalAlignment = Alignment.CenterVertically) {
                        Text("Диапазон", color = Color.Gray, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { quietStart = (quietStart + 23) % 24; notifPrefs.setQuietHours(quietStart, quietEnd) }) { Text("− начало") }
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        OutlinedButton(onClick = { quietEnd = (quietEnd + 1) % 24; notifPrefs.setQuietHours(quietStart, quietEnd) }) { Text("+ конец") }
                    }
                }
            }
            SettingsSection("Сеть") {
                // Раньше "Таймаут: 15 сек" и "Макс. качество на моб.: 720p" были
                // захардкожены. Таймаут теперь реально идёт в RetrofitFactory
                // через ServiceLocator (нужен reinitWithAuth(), чтобы применился
                // без перезапуска приложения — клиент пересобирается один раз
                // при инициализации). Ограничение качества на мобильных данных
                // читает QualityPreferences.getMaxQualityOnMobile() — само поле
                // существовало в коде с самого начала, просто не использовалось
                // нигде, включая этот экран.
                val networkPrefs = remember { NetworkPreferences(context) }
                val qualityPrefs = remember { QualityPreferences(context) }
                val timeoutOptions = listOf(10, 15, 20, 30)
                var timeoutSeconds by remember { mutableStateOf(networkPrefs.getTimeoutSeconds()) }
                CycleSetting("Таймаут запроса", "$timeoutSeconds сек") {
                    timeoutSeconds = timeoutOptions[(timeoutOptions.indexOf(timeoutSeconds).let { if (it == -1) 0 else it } + 1) % timeoutOptions.size]
                    networkPrefs.setTimeoutSeconds(timeoutSeconds)
                    viewModel.applyNetworkTimeoutChange()
                }
                val mobileQualityOptions = listOf("480p", "720p", "1080p", "Без ограничений")
                var maxMobileQuality by remember { mutableStateOf(qualityPrefs.getMaxQualityOnMobile()) }
                CycleSetting("Макс. качество на моб. данных", maxMobileQuality) {
                    val next = mobileQualityOptions[(mobileQualityOptions.indexOf(maxMobileQuality).let { if (it == -1) 0 else it } + 1) % mobileQualityOptions.size]
                    qualityPrefs.setMaxQualityOnMobile(next)
                    maxMobileQuality = next
                }
            }
            SettingsSection("Интерфейс") {
                // Раньше "Тема: Тёмная" и "Язык: Русский" были захардкоженными
                // строками, ни к чему не привязанными. Тема теперь реально
                // переключает ZenithTheme (см. MainActivity.kt) через
                // ServiceLocator.darkThemeFlow. Не использую androidx.tv.material3.Switch
                // — не смог достоверно подтвердить, что такой компонент вообще
                // есть в этой версии библиотеки, Button безопаснее (уже
                // используется в этом же файле). Язык оставлен видимым, но
                // намеренно не нажимается: реальный переключатель потребовал бы
                // вынести весь текст интерфейса в string-ресурсы (сейчас 0
                // использований stringResource во всём проекте) — отдельная
                // большая задача, не в этом заходе.
                val darkTheme by viewModel.darkThemeFlow.collectAsState()
                CycleSetting("Тема", if (darkTheme) "Тёмная" else "Светлая") { viewModel.setDarkTheme(!darkTheme) }
                SettingsItem("Язык", "Скоро")
            }
            SettingsSection("Плагины") { SettingsItem("Управление плагинами", "Каталог и настройки") }
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                Button(onClick = onPluginsClick) { Text("Плагины") }
            }
            // Раньше здесь всегда было захардкожено "Подключён" — не имело
            // значения, введён ли вообще источник, текст был одинаковым что
            // до, что после логина. Теперь этот экран достижим и БЕЗ
            // настроенного источника (см. SetupScreen.kt), поэтому статус
            // должен отражать реальность, а не врать по умолчанию.
            val isConnected = remember { viewModel.isConnected() }
            SettingsSection("Аккаунт") { SettingsItem("Источник", if (isConnected) "Подключён" else "Не подключён") }
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                if (!isConnected) Button(onClick = onConnectSourceClick) { Text("Подключить источник") }
                Button(onClick = onSyncClick) { Text("Синхронизация устройств") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingXL))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                Button(onClick = { viewModel.runOtaUpdate(); onForceOtaUpdateClick() }) { Text("Обновить парсеры") }
                OutlinedButton(onClick = onCacheManagementClick) { Text("Управление кэшем") }
                OutlinedButton(onClick = { viewModel.logout(); onLogoutClick() }) { Text("Сменить аккаунт") }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = ZenithDimens.paddingM)) { Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(ZenithDimens.paddingS)); content() }
}
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)) { Text(label, color = Color.White, modifier = Modifier.weight(1f)); Text(value, color = Color.Gray) }
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f))
            Text(value, color = ZenithPrimaryMuted)
        }
    }
}
