package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.data.local.entity.PluginEntity
import com.platinum.ott.presentation.screens.plugins.PluginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhonePluginCatalogScreen(navController: NavHostController) {
    val viewModel = ServiceLocator.pluginViewModel
    val installed by viewModel.installedPlugins.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadCatalog() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Плагины") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1C1C))
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(false, onClick = { navController.navigate("home") }, icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Главная") })
                NavigationBarItem(false, onClick = { navController.navigate("favorites") }, icon = { Icon(Icons.Default.Favorite, "Fav") }, label = { Text("Избранное") })
                NavigationBarItem(false, onClick = { navController.navigate("history") }, icon = { Icon(Icons.Default.History, "Hist") }, label = { Text("История") })
                NavigationBarItem(false, onClick = { navController.navigate("settings") }, icon = { Icon(Icons.Default.Settings, "Set") }, label = { Text("Настройки") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).background(Color(0xFF101010))) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF1C1C1C)) {
                Tab(selectedTab == 0, onClick = { selectedTab = 0 }) { Text("Установленные") }
                Tab(selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Каталог") }
            }

            // Install status
            when (val state = installState) {
                is PluginViewModel.InstallState.Installing -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is PluginViewModel.InstallState.Done -> {
                    LaunchedEffect(state) { viewModel.resetInstallState() }
                }
                is PluginViewModel.InstallState.Failed -> {
                    Text("⚠ ${state.error}", color = Color(0xFFFF6B6B), modifier = Modifier.padding(12.dp))
                    LaunchedEffect(state) { viewModel.resetInstallState() }
                }
                else -> {}
            }

            when (selectedTab) {
                0 -> PhoneInstalledTab(installed, navController, viewModel)
                1 -> PhoneCatalogTab(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun PhoneInstalledTab(plugins: List<PluginEntity>, navController: NavHostController, viewModel: PluginViewModel) {
    if (plugins.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет установленных плагинов", color = Color.Gray)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(plugins, key = { it.id }) { plugin ->
            Card(
                onClick = { navController.navigate("plugin/${plugin.id}") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(plugin.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("v${plugin.installedVersion} • ${plugin.pluginType}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        if (plugin.description.isNotEmpty()) {
                            Text(plugin.description, color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    Switch(
                        checked = plugin.isEnabled,
                        onCheckedChange = { viewModel.togglePlugin(plugin.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneCatalogTab(uiState: PluginViewModel.UiState, viewModel: PluginViewModel) {
    when (uiState) {
        is PluginViewModel.UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is PluginViewModel.UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("⚠ ${uiState.message}", color = Color(0xFFFF6B6B)) }
        is PluginViewModel.UiState.Success -> {
            if (uiState.catalog.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Каталог пуст", color = Color.Gray) }
                return
            }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.catalog, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("v${entry.version} • ${entry.author}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                Text(entry.description, color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Button(onClick = { viewModel.installFromCatalog(entry) }) { Text("Уст.") }
                        }
                    }
                }
            }
        }
    }
}
