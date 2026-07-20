package com.platinum.ott.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onClearCacheClick: () -> Unit, onForceOtaUpdateClick: () -> Unit, onLogoutClick: () -> Unit, onPluginsClick: () -> Unit = {}, onSyncClick: () -> Unit = {}, viewModel: SettingsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().padding(start = 56.dp, top = 56.dp)) {
        Text("Настройки", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(32.dp))
        // Sections: Playback, Notifications, Network, Interface, Account, About
        SettingsSection("Воспроизведение") { SettingsItem("Качество по умолчанию", "Авто"); SettingsItem("Автовоспроизведение", "Вкл"); SettingsItem("Субтитры", "Выкл") }
        SettingsSection("Уведомления") { SettingsItem("Новые серии", "Вкл"); SettingsItem("Новый контент", "Вкл"); SettingsItem("Тихий режим", "23:00-08:00") }
        SettingsSection("Сеть") { SettingsItem("Таймаут", "15 сек"); SettingsItem("Макс. качество на моб.", "720p") }
        SettingsSection("Интерфейс") { SettingsItem("Тема", "Тёмная"); SettingsItem("Язык", "Русский") }
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
