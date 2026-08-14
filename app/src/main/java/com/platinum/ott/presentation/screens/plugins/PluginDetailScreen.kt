package com.platinum.ott.presentation.screens.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.platinum.ott.data.local.entity.PluginEntity

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginDetailScreen(
    pluginId: String,
    onBackPressed: () -> Unit,
    viewModel: PluginViewModel = hiltViewModel()
) {
    val plugin by viewModel.getPluginById(pluginId)
        .collectAsStateWithLifecycle(initialValue = null)
    val current = plugin

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(ZenithDimens.paddingXL)) {
        if (current == null) {
            Text("Плагин не найден", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.name, style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onBackPressed) { Text("Назад") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            DetailRow("ID", current.id)
            DetailRow("Версия", current.installedVersion)
            DetailRow("Тип", current.pluginType)
            DetailRow("Автор", current.author.ifEmpty { "Неизвестен" })
            DetailRow("Описание", current.description.ifEmpty { "Нет описания" })
            DetailRow("Источник", current.repoUrl.ifEmpty { "Локальный" })
            DetailRow("Статус", if (current.isEnabled) "Включён" else "Отключён")

            Spacer(Modifier.height(ZenithDimens.paddingM))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                Button(onClick = { viewModel.togglePlugin(pluginId, !current.isEnabled) }) {
                    Text(if (current.isEnabled) "Отключить" else "Включить")
                }
                OutlinedButton(onClick = { viewModel.uninstallPlugin(pluginId); onBackPressed() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("$label: ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(120.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}
