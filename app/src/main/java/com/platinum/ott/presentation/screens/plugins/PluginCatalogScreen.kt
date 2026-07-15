package com.platinum.ott.presentation.screens.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.core.plugin.PluginRepository
import com.platinum.ott.data.local.entity.PluginEntity

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginCatalogScreen(
    onBackPressed: () -> Unit,
    onPluginClick: (String) -> Unit,
    viewModel: PluginViewModel = ServiceLocator.pluginViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installed by viewModel.installedPlugins.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadCatalog() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Плагины", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBackPressed) { Text("Назад") }
        }
        Spacer(Modifier.height(16.dp))
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, onFocus = {}) { Text("Установленные") }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, onFocus = {}) { Text("Каталог") }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, onFocus = {}) { Text("Обновления") }
        }
        Spacer(Modifier.height(16.dp))
        when (selectedTab) {
            0 -> InstalledTab(installed, onPluginClick, viewModel)
            1 -> CatalogTab(uiState, viewModel)
            2 -> UpdatesTab(uiState, viewModel)
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
        is PluginViewModel.UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("⚠ ${uiState.message}", color = Color(0xFFFF6B6B)) }
        is PluginViewModel.UiState.Success -> {
            if (uiState.catalog.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Каталог пуст", color = Color.Gray) }
                return
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
        is PluginViewModel.UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("⚠ ${uiState.message}", color = Color(0xFFFF6B6B)) }
        is PluginViewModel.UiState.Success -> {
            if (uiState.updates.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Все плагины актуальны ✓", color = Color(0xFF4CAF50)) }
                return
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.updates.forEach { (installed, catalog) ->
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(plugin.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("v${plugin.installedVersion}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Text(plugin.pluginType, color = Color(0xFF6C63FF), style = MaterialTheme.typography.labelSmall)
            }
            Switch(checked = plugin.isEnabled, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogPluginCard(entry: PluginRepository.CatalogEntry, onInstall: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(entry.name, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("v${entry.version} • ${entry.author}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Text(entry.description, color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Установить") }
        }
    }
}
