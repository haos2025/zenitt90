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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onClearCacheClick: () -> Unit, onForceOtaUpdateClick: () -> Unit, onLogoutClick: () -> Unit, onPluginsClick: () -> Unit = {}, onSyncClick: () -> Unit = {}, viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
            val context = LocalContext.current
            val qualityPrefs = remember { QualityPreferences(context) }
            val qualityOptions = listOf("Авто", "1080p", "720p", "480p")
            var selectedQuality by remember { mutableStateOf(qualityPrefs.getSelectedQuality() ?: "Авто") }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Качество по умолчанию", color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = {
                    val next = qualityOptions[(qualityOptions.indexOf(selectedQuality) + 1) % qualityOptions.size]
                    if (next == "Авто") qualityPrefs.clearSelectedQuality() else qualityPrefs.setSelectedQuality(next)
                    selectedQuality = next
                }) { Text(selectedQuality) }
            }
        }
        SettingsSection("Уведомления") { SettingsItem("Новые серии", "Вкл"); SettingsItem("Новый контент", "Вкл"); SettingsItem("Тихий режим", "23:00-08:00") }
        SettingsSection("Сеть") { SettingsItem("Таймаут", "15 сек"); SettingsItem("Макс. качество на моб.", "720p") }
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
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Тема", color = Color.White, modifier = Modifier.weight(1f))
                Button(onClick = { com.platinum.ott.core.ServiceLocator.setDarkTheme(!darkTheme) }) {
                    Text(if (darkTheme) "Тёмная" else "Светлая")
                }
            }
            SettingsItem("Язык", "Скоро")
        }
        SettingsSection("Плагины") { SettingsItem("Управление плагинами", "Каталог и настройки") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPluginsClick) { Text("Плагины") }
        }
        SettingsSection("Аккаунт") { SettingsItem("Источник", "Подключён") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
