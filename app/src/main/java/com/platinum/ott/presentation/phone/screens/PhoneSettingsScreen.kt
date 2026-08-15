package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.navigation.navigateToTab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.platinum.ott.core.platform.ZenithDimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.screens.settings.SettingsViewModel
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.presentation.components.MovieCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSettingsScreen(navController: NavHostController, viewModel: SettingsViewModel = hiltViewModel()) {

Scaffold(bottomBar = { PhoneBottomBar(navController) }) { padding ->
    Column(Modifier.padding(padding).background(MaterialTheme.colorScheme.background).padding(ZenithDimens.paddingM).verticalScroll(rememberScrollState())) {
        Text("Настройки", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(ZenithDimens.paddingM))
        Card(
            onClick = { navController.navigate("plugins") },
            modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)
        ) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Плагины", style = MaterialTheme.typography.titleMedium)
                Text("Каталог и управление плагинами", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        // Раньше подзаголовок был статичным текстом всегда одинаковым,
        // независимо от того, подключён ли реально источник — та же
        // правка, что уже сделана на TV (SettingsScreen.kt), перенесена сюда.
        val isConnected = remember { viewModel.isConnected() }
        Card(
            onClick = { navController.navigate(if (isConnected) "sync_pairing" else "setup") },
            modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)
        ) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Аккаунт", style = MaterialTheme.typography.titleMedium)
                Text(if (isConnected) "Источник подключён · синхронизация между устройствами" else "Источник не подключён · нажмите, чтобы подключить", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        // Раньше единственный путь к экрану синхронизации шёл через карточку
        // "Аккаунт" и был завязан на isConnected (= есть M3U/Xtream). Но
        // синхронизация — независимая система (пара по 6-значному коду,
        // AuthPreferences.getOrCreateSyncToken()), M3U/Xtream ей не нужен —
        // это та же путаница двух разных проверок, что была в MainActivity.
        // Без источника карточка "Аккаунт" всегда вела на "setup" (ввод M3U),
        // и попасть на sync_pairing было нельзя вообще. На TV
        // (SettingsScreen.kt) это уже две отдельные кнопки — здесь та же
        // развязка, просто перенесённая на телефон.
        Card(
            onClick = { navController.navigate("sync_pairing") },
            modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)
        ) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Синхронизация устройств", style = MaterialTheme.typography.titleMedium)
                Text("Перенести избранное и историю на другое устройство", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        // Телефон-компаньон (ROADMAP.md п.6, PROMPT_PHONE_COMPANION.md) — не
        // то же самое, что "Синхронизация устройств" выше: тот канал через
        // zenith-backend по 6-значному коду (SyncPairingViewModel), этот —
        // напрямую по локальной сети, без бэкенда (см. QrScanScreen.kt на TV).
        // Отдельная карточка, чтобы не путать два разных механизма.
        Card(
            onClick = { navController.navigate("qr_scan") },
            modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)
        ) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Телефон-компаньон", style = MaterialTheme.typography.titleMedium)
                Text("Отсканировать QR с TV — например, чтобы отправить ссылку на субтитры", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        // Раньше это была карточка-заглушка из общего списка без единого
        // обработчика нажатия. "Качество по умолчанию" — единственная
        // реально существующая часть "Воспроизведения": QualityPreferences
        // уже читается в PlayerViewModel.loadMovie() как стартовое качество
        // для любого фильма. "Автовоспроизведение"/"Субтитры" НЕ сделаны —
        // в коде нет ни понятия "следующая серия", ни обработки субтитровых
        // дорожек вообще, это была бы иллюзия настройки без реальной фичи
        // за ней — сознательно оставлено на будущее, не выдумывается здесь.
        Card(Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Воспроизведение", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(ZenithDimens.paddingSM))
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
            }
        }
        // Раньше это была карточка без единого обработчика нажатия из
        // общего списка-заглушки — та же, что чинил на TV несколько шагов
        // назад, просто эта правка никогда не переносилась на телефон.
        // "Новый контент" сюда намеренно не включён — см. комментарий в
        // TV SettingsScreen.kt: в бэкенде нет поля "добавлено в каталог".
        Card(Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Уведомления", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(ZenithDimens.paddingSM))
                val context = LocalContext.current
                val notifPrefs = remember { com.platinum.ott.core.NotificationPreferences(context) }
                var newEpisodesEnabled by remember { mutableStateOf(notifPrefs.isNewEpisodesEnabled()) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Новые серии (избранные сериалы)", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = newEpisodesEnabled, onCheckedChange = { newEpisodesEnabled = it; notifPrefs.setNewEpisodesEnabled(it) })
                }
                Text("Новый контент", color = Color.Gray, modifier = Modifier.padding(top = ZenithDimens.paddingS))
                Text("Скоро", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(ZenithDimens.paddingS))
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
        }
        // Раньше раздела "Сеть" на телефоне не было вообще ни в каком
        // виде — ни рабочего, ни даже карточки-заглушки. На TV уже есть.
        Card(Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Сеть", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(ZenithDimens.paddingSM))
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
                Spacer(Modifier.height(ZenithDimens.paddingS))
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
        }
        // Раньше это была ещё одна карточка без единого обработчика нажатия
        // из общего списка-заглушки. Тема теперь реально переключает
        // ZenithTheme (см. MainActivity.kt/ServiceLocator.darkThemeFlow) —
        // язык оставлен видимым, но намеренно не нажимается: реальный
        // языковой переключатель потребовал бы вынести весь текст
        // интерфейса в string-ресурсы (сейчас 0 использований stringResource
        // во всём проекте) — отдельная большая задача, не в этом заходе.
        Card(Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text("Интерфейс", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(ZenithDimens.paddingSM))
                val darkTheme by viewModel.darkThemeFlow.collectAsState()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Тёмная тема", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = darkTheme, onCheckedChange = { viewModel.setDarkTheme(it) })
                }
                Spacer(Modifier.height(ZenithDimens.paddingS))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Язык", color = Color.Gray, modifier = Modifier.weight(1f))
                    Text("Скоро", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
}
