package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.presentation.screens.plugins.PluginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhonePluginDetailScreen(pluginId: String, navController: NavHostController) {
    val viewModel: PluginViewModel = viewModel(factory = PluginViewModel.factory(ServiceLocator.pluginManager, ServiceLocator.pluginRepository))
    val plugin by viewModel.getPluginById(pluginId)
        .collectAsStateWithLifecycle(initialValue = null)
    val current = plugin

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "Плагин") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1C1C1C))
            )
        }
    ) { padding ->
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Плагин не найден", color = Color(0xFFFF6B6B))
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).background(Color(0xFF101010)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(current.name, style = MaterialTheme.typography.headlineLarge, color = Color.White)
            Text("v${current.installedVersion}", color = Color(0xFF6C63FF))
            Spacer(Modifier.height(8.dp))

            DetailItem("ID", current.id)
            DetailItem("Тип", current.pluginType)
            DetailItem("Автор", current.author.ifEmpty { "Неизвестен" })
            DetailItem("Описание", current.description.ifEmpty { "Нет описания" })
            DetailItem("Источник", current.repoUrl.ifEmpty { "Локальный" })
            DetailItem("Статус", if (current.isEnabled) "Включён ✓" else "Отключён")

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.togglePlugin(pluginId, !current.isEnabled) },
                    modifier = Modifier.weight(1f)
                ) { Text(if (current.isEnabled) "Отключить" else "Включить") }
                OutlinedButton(
                    onClick = { viewModel.uninstallPlugin(pluginId); navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) { Text("Удалить", color = Color(0xFFFF6B6B)) }
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF6C63FF), style = MaterialTheme.typography.labelMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
