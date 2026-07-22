package com.platinum.ott.presentation.screens.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.data.local.entity.PluginEntity

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginDetailScreen(
    pluginId: String,
    onBackPressed: () -> Unit,
    viewModel: PluginViewModel = viewModel(factory = PluginViewModel.factory(ServiceLocator.pluginManager, ServiceLocator.pluginRepository))
) {
    val plugin by viewModel.getPluginById(pluginId)
        .collectAsStateWithLifecycle(initialValue = null)
    val current = plugin

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(32.dp)) {
        if (current == null) {
            Text("Плагин не найден", color = Color(0xFFFF6B6B), modifier = Modifier.align(Alignment.Center))
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.name, style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onBackPressed) { Text("Назад") }
            }
            Spacer(Modifier.height(8.dp))
            DetailRow("ID", current.id)
            DetailRow("Версия", current.installedVersion)
            DetailRow("Тип", current.pluginType)
            DetailRow("Автор", current.author.ifEmpty { "Неизвестен" })
            DetailRow("Описание", current.description.ifEmpty { "Нет описания" })
            DetailRow("Источник", current.repoUrl.ifEmpty { "Локальный" })
            DetailRow("Статус", if (current.isEnabled) "Включён" else "Отключён")

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.togglePlugin(pluginId, !current.isEnabled) }) {
                    Text(if (current.isEnabled) "Отключить" else "Включить")
                }
                OutlinedButton(onClick = { viewModel.uninstallPlugin(pluginId); onBackPressed() }) {
                    Text("Удалить", color = Color(0xFFFF6B6B))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("$label: ", color = Color(0xFF6C63FF), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(120.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
