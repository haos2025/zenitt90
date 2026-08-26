package com.platinum.ott.presentation.screens.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum.ott.core.platform.ZenithDimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import com.platinum.ott.core.companion.CompanionHttpServer
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.core.plugin.PluginRepository
import com.platinum.ott.data.local.entity.PluginEntity
import com.platinum.ott.presentation.screens.qr.QrScanScreen
import com.platinum.ott.ui.theme.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginCatalogScreen(
    onBackPressed: () -> Unit,
    onPluginClick: (String) -> Unit,
    viewModel: PluginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installed by viewModel.installedPlugins.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadCatalog() }

    // Телефон-компаньон (PROMPT_QR_PANELS.md), третье применение канала —
    // "/plugin_url". Поле URL поднято сюда из InstallFromUrlRow (было
    // локальным remember внутри неё) — текст со скана нужно подставить в
    // то же поле, что и ручной ввод, кнопку "Установить по URL" всё равно
    // нажимает пользователь сам (см. промт: установка необратимее поиска,
    // автоматической установки по приезду текста быть не должно).
    var installUrl by remember { mutableStateOf("") }
    var showCompanionQr by remember { mutableStateOf(false) }
    var companionAddress by remember { mutableStateOf<String?>(null) }
    DisposableEffect(showCompanionQr) {
        var server: CompanionHttpServer? = null
        if (showCompanionQr) {
            server = CompanionHttpServer(endpointPath = "/plugin_url") { text ->
                // Обработчик NanoHTTPD вызывается не в главном потоке — то
                // же самое, что уже сделано в SearchScreen.kt/PlayerScreen.kt.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    installUrl = text
                    showCompanionQr = false
                }
            }
            val port = server.startServer()
            val ip = LocalNetworkUtils.getLocalIpAddress()
            companionAddress = if (ip != null) "http://$ip:$port#plugin_url" else null
        } else {
            companionAddress = null
        }
        onDispose { server?.stop() }
    }
    androidx.activity.compose.BackHandler(enabled = showCompanionQr) { showCompanionQr = false }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(ZenithDimens.paddingXL)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Плагины", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBackPressed) { Text("Назад") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, onFocus = {}) { Text("Установленные") }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, onFocus = {}) { Text("Каталог") }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, onFocus = {}) { Text("Обновления") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))
        // Раньше в приложении не было НИ ОДНОГО поля для ручного ввода URL
        // плагина — только каталог (fetchCatalog(), который по умолчанию
        // упирается в несуществующий домен-заглушку, см.
        // PluginRepository.DEFAULT_CATALOG) и installFromCatalog(). Сам
        // installFromUrl() в PluginViewModel уже был готов и рабочий,
        // просто ничто в UI его не вызывало. Поле — на уровне всего
        // экрана (не только вкладки "Каталог"), потому что это не часть
        // каталога, а независимый способ установки.
        InstallFromUrlRow(
            url = installUrl,
            onUrlChange = { installUrl = it },
            installState = installState,
            onInstall = viewModel::installFromUrl,
            onReset = viewModel::resetInstallState,
            onQrClick = { showCompanionQr = true }
        )
        Spacer(Modifier.height(ZenithDimens.paddingM))
        when (selectedTab) {
            0 -> InstalledTab(installed, onPluginClick, viewModel)
            1 -> CatalogTab(uiState, viewModel)
            2 -> UpdatesTab(uiState, viewModel)
        }
    }

    if (showCompanionQr) {
        QrScanScreen(
            content = companionAddress,
            onDismiss = { showCompanionQr = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InstallFromUrlRow(
    url: String,
    onUrlChange: (String) -> Unit,
    installState: PluginViewModel.InstallState,
    onInstall: (String) -> Unit,
    onReset: () -> Unit,
    onQrClick: () -> Unit
) {
    val installing = installState is PluginViewModel.InstallState.Installing
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = url,
                onValueChange = onUrlChange,
                singleLine = true,
                enabled = !installing,
                textStyle = TextStyle(Color.White, 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).background(Color.White.copy(0.08f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(ZenithDimens.paddingM, ZenithDimens.paddingSM),
                decorationBox = { inner ->
                    if (url.isEmpty()) Text("Ссылка на плагин (URL .js)…", style = TextStyle(Color.White.copy(0.3f), 16.sp))
                    inner()
                }
            )
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(enabled = !installing, onClick = onQrClick) { Text("По QR с телефона") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            Button(enabled = url.isNotBlank() && !installing, onClick = { onInstall(url.trim()) }) {
                Text(if (installing) "Установка…" else "Установить по URL")
            }
        }
        when (val state = installState) {
            is PluginViewModel.InstallState.Done -> {
                Text("✓ Установлен: ${state.manifest.name}", color = ZenithSuccess, modifier = Modifier.padding(top = ZenithDimens.paddingS))
                LaunchedEffect(state) { onUrlChange(""); onReset() }
            }
            is PluginViewModel.InstallState.Failed -> {
                Text("⚠ ${state.error}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = ZenithDimens.paddingS))
                LaunchedEffect(state) { onReset() }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InstalledTab(plugins: List<PluginEntity>, onPluginClick: (String) -> Unit, viewModel: PluginViewModel) {
    if (plugins.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет установленных плагинов", color = Color.Gray)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM),
        verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)
    ) {
        items(plugins, key = { it.id }) { plugin ->
            PluginCard(plugin, onClick = { onPluginClick(plugin.id) }, onToggle = { viewModel.togglePlugin(plugin.id, it) })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogTab(uiState: PluginViewModel.UiState, viewModel: PluginViewModel) {
    when (uiState) {
        is PluginViewModel.UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is PluginViewModel.UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("⚠ ${uiState.message}", color = MaterialTheme.colorScheme.error) }
        is PluginViewModel.UiState.Success -> {
            if (uiState.catalog.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Каталог пуст", color = Color.Gray) }
                return
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM),
                verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)
            ) {
                items(uiState.catalog, key = { it.id }) { entry ->
                    CatalogPluginCard(entry, onInstall = { viewModel.installFromCatalog(entry) })
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdatesTab(uiState: PluginViewModel.UiState, viewModel: PluginViewModel) {
    when (uiState) {
        is PluginViewModel.UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is PluginViewModel.UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("⚠ ${uiState.message}", color = MaterialTheme.colorScheme.error) }
        is PluginViewModel.UiState.Success -> {
            if (uiState.updates.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Все плагины актуальны ✓", color = ZenithSuccess) }
                return
            }
            Column(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                uiState.updates.forEach { (installed, catalog) ->
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(ZenithDimens.paddingM), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(installed.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("${installed.installedVersion} → ${catalog.version}", color = Color.Gray)
                            }
                            Button(onClick = { viewModel.updatePlugin(installed.id, catalog) }) { Text("Обновить") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PluginCard(plugin: PluginEntity, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Column(Modifier.padding(ZenithDimens.paddingSM), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(plugin.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("v${plugin.installedVersion}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Text(plugin.pluginType, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = plugin.isEnabled, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogPluginCard(entry: PluginRepository.CatalogEntry, onInstall: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        Column(Modifier.padding(ZenithDimens.paddingSM), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(entry.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("v${entry.version} • ${entry.author}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Text(entry.description, color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Установить") }
        }
    }
}
