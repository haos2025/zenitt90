package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.epg.EpgSettingsViewModel
import com.platinum.ott.presentation.screens.epg.epgAdvice
import com.platinum.ott.presentation.screens.epg.epgHeadline
import com.platinum.ott.presentation.screens.epg.epgSummary
import com.platinum.ott.ui.theme.ZenithShapeMedium

/** Телефон: «Настройки → Телепрограмма (EPG)» — тот же статус и журнал, что на ТВ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneEpgSettingsScreen(
    navController: NavHostController,
    viewModel: EpgSettingsViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Телепрограмма (EPG)") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(ZenithDimens.paddingM),
            verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                Button(
                    onClick = { viewModel.refresh() },
                    enabled = !status.running,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = ZenithShapeMedium
                ) { Text(if (status.running) "Идёт загрузка…" else "Обновить сейчас") }
                if (status.running) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Text(epgHeadline(status), style = MaterialTheme.typography.titleMedium)
            epgSummary(status)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            Text("Журнал", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            if (status.lines.isEmpty()) {
                Text("Пока пусто — нажмите «Обновить сейчас».", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                status.lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
            epgAdvice(status)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
