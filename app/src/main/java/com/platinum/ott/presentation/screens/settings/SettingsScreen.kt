package com.platinum.ott.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.NetworkPreferences
import com.platinum.ott.core.NotificationPreferences

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onClearCacheClick: () -> Unit, onForceOtaUpdateClick: () -> Unit, onLogoutClick: () -> Unit, onPluginsClick: () -> Unit = {}, onSyncClick: () -> Unit = {}, onConnectSourceClick: () -> Unit = {}, viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(start = 56.dp, top = 56.dp)) {
        Text("Настройки", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(32.dp))
        // Sections: Playback, Notifications, Network, Interface, Account, About
        SettingsSection("Воспроизведение") {
            // Раньше "Качество по умолчанию"/"Автовоспроизведение"/"Субтитры"
            // были тремя захардкоженными строками подряд. Реально существует
            // только первое — QualityPreferences уже читается в
            // PlayerViewModel.loadMovie() как стартовое качество. У
            // автовоспроизведения следующей серии и субтитров нет вообще
            // никакой реализации в коде (ни понятия "следующий эпизод", ни
            // обработки субтитровых дорожек в ExoPlayer/PlayerView) — не
            // стал выдумывать настройки под несуществующие фичи.
            val qualityPrefs = remember { QualityPreferences(context) }
            val qualityOptions = listOf("Авто", "1080p", "720p", "480p")
            var selectedQuality by remember { mutableStateOf(qualityPrefs.getSelectedQuality() ?: "Авто") }
            CycleSetting("Качество по умолчанию", selectedQuality) {
                val next = qualityOptions[(qualityOptions.indexOf(selectedQuality) + 1) % qualityOptions.size]
                if (next == "Авто") qualityPrefs.clearSelectedQuality() else qualityPrefs.setSelectedQuality(next)
                selectedQuality = next
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
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Диапазон", color = Color.Gray, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { quietStart = (quietStart + 23) % 24; notifPrefs.setQuietHours(quietStart, quietEnd) }) { Text("− начало") }
                    Spacer(Modifier.width(8.dp))
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
                com.platinum.ott.core.ServiceLocator.reinitWithAuth()
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
            val darkTheme by com.platinum.ott.core.ServiceLocator.darkThemeFlow.collectAsState()
            CycleSetting("Тема", if (darkTheme) "Тёмная" else "Светлая") { com.platinum.ott.core.ServiceLocator.setDarkTheme(!darkTheme) }
            SettingsItem("Язык", "Скоро")
        }
        SettingsSection("Плагины") { SettingsItem("Управление плагинами", "Каталог и настройки") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPluginsClick) { Text("Плагины") }
        }
        // Раньше здесь всегда было захардкожено "Подключён" — не имело
        // значения, введён ли вообще источник, текст был одинаковым что
        // до, что после логина. Теперь этот экран достижим и БЕЗ
        // настроенного источника (см. SetupScreen.kt), поэтому статус
        // должен отражать реальность, а не врать по умолчанию.
        val isConnected = remember { com.platinum.ott.core.ServiceLocator.checkAuthUseCase.execute() }
        SettingsSection("Аккаунт") { SettingsItem("Источник", if (isConnected) "Подключён" else "Не подключён") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isConnected) Button(onClick = onConnectSourceClick) { Text("Подключить источник") }
            Button(onClick = onSyncClick) { Text("Синхронизация устройств") }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.runOtaUpdate(); onForceOtaUpdateClick() }) { Text("Обновить парсеры") }
            OutlinedButton(onClick = { viewModel.clearCache(); onClearCacheClick() }) { Text("Очистить кэш") }
            OutlinedButton(onClick = { viewModel.logout(); onLogoutClick() }) { Text("Сменить аккаунт") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) { Text(title, style = MaterialTheme.typography.titleLarge, color = Color(0xFF6C63FF)); Spacer(Modifier.height(8.dp)); content() }
}
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun SettingsItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(label, color = Color.White, modifier = Modifier.weight(1f)); Text(value, color = Color.Gray) }
}
@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun CycleSetting(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Button(onClick = onClick) { Text(value) }
    }
}
