package com.platinum.ott.presentation.screens.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.ui.theme.ZenithOnSurface

/**
 * ТВ: «Настройки → Телепрограмма (EPG)». Статус, кнопка «Обновить сейчас» и живой журнал загрузки
 * (какой адрес качается, сколько программ, сколько каналов сопоставлено). Выход — системной «Назад».
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpgSettingsScreen(viewModel: EpgSettingsViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val buttonRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { buttonRequester.safeRequestFocus() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM),
            modifier = Modifier.align(Alignment.TopCenter).width(760.dp).padding(vertical = ZenithDimens.paddingXL)
        ) {
            Text("Телепрограмма (EPG)", style = MaterialTheme.typography.displaySmall, color = ZenithOnSurface)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                Button(
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    onClick = { viewModel.refresh() },
                    enabled = !status.running,
                    modifier = Modifier.focusRequester(buttonRequester)
                ) { Text(if (status.running) "Идёт загрузка…" else "Обновить сейчас") }
                if (status.running) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            Text(epgHeadline(status), style = MaterialTheme.typography.titleMedium, color = ZenithOnSurface)
            epgSummary(status)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = ZenithOnSurface.copy(alpha = 0.8f))
            }

            Text("Журнал", style = MaterialTheme.typography.titleSmall, color = ZenithOnSurface.copy(alpha = 0.6f))
            if (status.lines.isEmpty()) {
                Text("Пока пусто — нажмите «Обновить сейчас».", color = ZenithOnSurface.copy(alpha = 0.5f))
            } else {
                // На ТВ нет прокрутки без фокуса — показываем последние строки журнала.
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    status.lines.takeLast(8).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.75f))
                    }
                }
            }

            epgAdvice(status)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ZenithOnSurface.copy(alpha = 0.55f))
            }
        }
    }
}
