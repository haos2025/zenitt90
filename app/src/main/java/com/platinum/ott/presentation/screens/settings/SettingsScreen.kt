package com.platinum.ott.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CircularProgressIndicator
// Явный импорт Icon/HorizontalDivider поверх звёздочного
// `androidx.tv.material3.*` ниже — тот же приём, что уже применён в
// NavSidebar.kt для Icon: явный импорт побеждает звёздочный, оба
// компонента из обычного material3 работают поверх tv-material3 экрана
// без конфликтов (Icon — проверено там же; HorizontalDivider — тот же
// принцип, что и androidx.compose.material3.CircularProgressIndicator
// выше, уже используемый на других TV-экранах проекта).
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun SettingsScreen(navController: NavHostController, onCacheManagementClick: () -> Unit, onForceOtaUpdateClick: () -> Unit, onPluginsClick: () -> Unit = {}, onSyncClick: () -> Unit = {}, onSourcesClick: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourcesCount by viewModel.sourcesCount.collectAsStateWithLifecycle()
    val lastSyncedAtMs by viewModel.lastSyncedAtMs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Экран — обычный composable() в NavHost, не сохранённая вкладка, так
    // что при возврате сюда с "Источники"/"Синхронизация" он пересоздаётся
    // заново и это отрабатывает снова — статус в карточках не залипает на
    // значении, снятом при первом входе (см. SettingsViewModel.refreshStatusRows()).
    LaunchedEffect(Unit) { viewModel.refreshStatusRows() }

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
            // Sections: Playback, Notifications, Network, Interface, Plugins, Sources
            SettingsGroup("Воспроизведение", Icons.Outlined.PlayCircle) {
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
                SettingsDivider()
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
            Spacer(Modifier.height(ZenithDimens.paddingL))
            SettingsGroup("Уведомления", Icons.Outlined.Notifications) {
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
                SettingsDivider()
                SettingsItem("Новый контент", "Скоро")
                SettingsDivider()
                var quietEnabled by remember { mutableStateOf(notifPrefs.isQuietHoursEnabled()) }
                var quietStart by remember { mutableStateOf(notifPrefs.getQuietStartHour()) }
                var quietEnd by remember { mutableStateOf(notifPrefs.getQuietEndHour()) }
                CycleSetting("Тихий режим", if (quietEnabled) "${quietStart}:00–${quietEnd}:00" else "Выкл") {
                    quietEnabled = !quietEnabled
                    notifPrefs.setQuietHoursEnabled(quietEnabled)
                }
                if (quietEnabled) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = ZenithDimens.paddingXS), verticalAlignment = Alignment.CenterVertically) {
                        Text("Диапазон", color = Color.Gray, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { quietStart = (quietStart + 23) % 24; notifPrefs.setQuietHours(quietStart, quietEnd) }) { Text("− начало") }
                        Spacer(Modifier.width(ZenithDimens.paddingS))
                        OutlinedButton(onClick = { quietEnd = (quietEnd + 1) % 24; notifPrefs.setQuietHours(quietStart, quietEnd) }) { Text("+ конец") }
                    }
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
            SettingsGroup("Сеть", Icons.Outlined.Wifi) {
                // Раньше "Таймаут: 15 сек" и "Макс. качество на моб.: 720p" были
                // захардкожены. Таймаут теперь реально идёт в RetrofitFactory
                // через sessionGraph.reinitWithAuth(), чтобы применился без
                // перезапуска приложения — клиент пересобирается один раз при
                // инициализации. Ограничение качества на мобильных данных
                // читает QualityPreferences.getMaxQualityOnMobile() — само поле
                // существовало в коде с самого начала, просто не использовалось
                // нигде, включая этот экран.
                val networkPrefs = remember { NetworkPreferences(context) }
                val qualityPrefsNet = remember { QualityPreferences(context) }
                val timeoutOptions = listOf(10, 15, 20, 30)
                var timeoutSeconds by remember { mutableStateOf(networkPrefs.getTimeoutSeconds()) }
                CycleSetting("Таймаут запроса", "$timeoutSeconds сек") {
                    timeoutSeconds = timeoutOptions[(timeoutOptions.indexOf(timeoutSeconds).let { if (it == -1) 0 else it } + 1) % timeoutOptions.size]
                    networkPrefs.setTimeoutSeconds(timeoutSeconds)
                    viewModel.applyNetworkTimeoutChange()
                }
                SettingsDivider()
                val mobileQualityOptions = listOf("480p", "720p", "1080p", "Без ограничений")
                var maxMobileQuality by remember { mutableStateOf(qualityPrefsNet.getMaxQualityOnMobile()) }
                CycleSetting("Макс. качество на моб. данных", maxMobileQuality) {
                    val next = mobileQualityOptions[(mobileQualityOptions.indexOf(maxMobileQuality).let { if (it == -1) 0 else it } + 1) % mobileQualityOptions.size]
                    qualityPrefsNet.setMaxQualityOnMobile(next)
                    maxMobileQuality = next
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
            SettingsGroup("Интерфейс", Icons.Outlined.Palette) {
                // Раньше "Тема: Тёмная" и "Язык: Русский" были захардкоженными
                // строками, ни к чему не привязанными. Тема теперь реально
                // переключает ZenithTheme (см. MainActivity.kt) через
                // ThemeManager.darkThemeFlow. Не использую androidx.tv.material3.Switch
                // — не смог достоверно подтвердить, что такой компонент вообще
                // есть в этой версии библиотеки, CycleSetting безопаснее (уже
                // используется в этом же файле). Язык оставлен видимым, но
                // намеренно не нажимается: реальный переключатель потребовал бы
                // вынести весь текст интерфейса в string-ресурсы (сейчас 0
                // использований stringResource во всём проекте) — отдельная
                // большая задача, не в этом заходе.
                val darkTheme by viewModel.darkThemeFlow.collectAsState()
                CycleSetting("Тема", if (darkTheme) "Тёмная" else "Светлая") { viewModel.setDarkTheme(!darkTheme) }
                SettingsDivider()
                SettingsItem("Язык", "Скоро")
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
            SettingsGroup("Плагины", Icons.Outlined.Extension) {
                NavRow("Управление плагинами", "Каталог и настройки", onClick = onPluginsClick)
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
            // Задача "Источники" (PROMPT_SOURCES_SCREEN.md) заменила прежний
            // единственный источник (AuthPreferences) на список PlaylistSource —
            // бинарного "подключён/не подключён" (isConnected(), читал старый
            // AuthPreferences.type) больше не существует как понятия: источников
            // может быть 0, 1 или много, у каждого свой статус. Вместо секции
            // "Аккаунт" с раздельными "Подключить источник"/"Сменить аккаунт" —
            // один переход на полноценный список источников (SourcesScreen.kt),
            // где статус/добавление/удаление каждого показаны по отдельности.
            // "Синхронизация" — независимый механизм (пара по 6-значному коду),
            // но живёт в той же карточке, т.к. обе строки — про подключение
            // к внешнему состоянию, а не про поведение самого приложения.
            SettingsGroup("Источники", Icons.Outlined.Source) {
                NavRow("Источники", if (sourcesCount == 0) "Не настроено" else "$sourcesCount подключено", onClick = onSourcesClick)
                SettingsDivider()
                NavRow("Синхронизация", "Последняя: ${formatLastSynced(lastSyncedAtMs)}", onClick = onSyncClick)
            }
            Spacer(Modifier.height(ZenithDimens.paddingXL))
            // Технические действия — сознательно вне карточек-секций выше и
            // визуально приглушены (серая обводка/подпись вместо цветной), чтобы
            // читаться как второстепенные инструменты, а не как настройки.
            // "Сменить аккаунт"/"Очистить кэш" отсюда убраны не по недосмотру:
            // логин на один аккаунт удалён вместе с AuthRepository
            // (PROMPT_REVISION.md), а полноценная очистка по категориям — уже
            // отдельный экран (onCacheManagementClick, см. CacheManagementScreen.kt),
            // эта кнопка теперь просто ведёт туда, а не чистит сама.
            Text("ТЕХНИЧЕСКОЕ", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { viewModel.runOtaUpdate(); onForceOtaUpdateClick() }, enabled = uiState !is SettingsUiState.Loading) {
                    if (uiState is SettingsUiState.Loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Gray)
                        Spacer(Modifier.width(ZenithDimens.paddingXS))
                    }
                    Text("Обновить парсеры")
                }
                OutlinedButton(onClick = onCacheManagementClick) { Text("Управление кэшем") }
            }
            // Раньше uiState (Loading/Success/Error) велось во ViewModel, но
            // нигде на экране не отображалось — единственным признаком того,
            // что "Обновить парсеры" вообще что-то сделало, было отсутствие
            // краша. Теперь исход виден явно под кнопками.
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

// Иконка + подпись капсом над карточкой секции — раньше это был просто
// Text(title, titleMedium), не читалось как визуальная группа с рядами
// ниже. Сама карточка — со скруглением и лёгким фоном/обводкой, ряды
// внутри разделены SettingsDivider(), не голый список без обрамления.
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = ZenithDimens.paddingXS, bottom = ZenithDimens.paddingS)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(ZenithDimens.paddingXS))
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ZenithSurfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(vertical = ZenithDimens.paddingXS, horizontal = ZenithDimens.paddingS),
            content = content
        )
    }
}
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
}
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp)) { Text(label, color = Color.White, modifier = Modifier.weight(1f)); Text(value, color = Color.Gray) }
}
// Строка-переход (было: отдельная Button вне секции — SettingsSection(...)
// рисовала только заголовок, а кнопка "Плагины"/"Источники"/"Синхронизация"
// шла отдельным Row ПОСЛЕ, визуально не читаясь как часть той же карточки).
// Теперь это ряд ВНУТРИ карточки, со статусом и стрелкой, тот же паттерн
// фокусируемой строки целиком, что и у CycleSetting.
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun NavRow(label: String, status: String, onClick: () -> Unit) {
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
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White)
                Text(status, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
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
