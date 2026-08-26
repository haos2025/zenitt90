package com.platinum.ott.presentation.screens.cache

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.CacheOverview
import com.platinum.ott.ui.theme.*

/**
 * PROMPT_CACHE_MANAGEMENT.md — заменяет прежнюю единственную кнопку
 * "Очистить кэш" на экране Настроек. Уровень экрана: размер по
 * категориям + отдельная кнопка очистки на каждую (не одна общая кнопка
 * на всё, но и не полная настройка TTL).
 *
 * Установленные плагины и избранное/история сюда сознательно не входят —
 * см. CacheManagementUseCase.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CacheManagementScreen(
    onBackPressed: () -> Unit,
    viewModel: CacheManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXXL)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Управление кэшем", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBackPressed) { Text("Назад") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingXL))

        when (val state = uiState) {
            is CacheUiState.Loading -> {
                Box(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXL), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is CacheUiState.Loaded -> CacheOverviewList(
                overview = state.overview,
                onClearCatalog = viewModel::clearCatalog,
                onClearPlaylist = viewModel::clearPlaylist,
                onClearMetadata = viewModel::clearMetadata,
                onClearPosters = viewModel::clearPosters,
                onClearCrashLogs = viewModel::clearCrashLogs
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CacheOverviewList(
    overview: CacheOverview,
    onClearCatalog: () -> Unit,
    onClearPlaylist: () -> Unit,
    onClearMetadata: () -> Unit,
    onClearPosters: () -> Unit,
    onClearCrashLogs: () -> Unit
) {
    Column {
        CacheCategoryRow("Каталог с бэкенда", "${overview.catalogEntries} записей", enabled = overview.catalogEntries > 0, onClear = onClearCatalog)
        CacheCategoryRow("Плейлист M3U/Xtream", "${overview.playlistEntries} записей", enabled = overview.playlistEntries > 0, onClear = onClearPlaylist)
        CacheCategoryRow("TMDB-метаданные", "${overview.metadataEntries} записей", enabled = overview.metadataEntries > 0, onClear = onClearMetadata)
        CacheCategoryRow("Постеры", formatCacheBytes(overview.posterCacheBytes), enabled = overview.posterCacheBytes > 0, onClear = onClearPosters)
        CacheCategoryRow("Краш-логи", "${formatCacheBytes(overview.crashLogBytes)} · ${overview.crashLogCount} файлов", enabled = overview.crashLogCount > 0, onClear = onClearCrashLogs)
    }
}

// Не переиспользует паттерн CycleSetting (вся строка = фокус-цель) — там
// сама строка выполняет действие. Здесь действие делает только кнопка
// "Очистить", строка — просто вывод текста, поэтому обычный Row с явной
// кнопкой внутри как D-pad фокус-цель, без лишнего оборачивания в Surface.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CacheCategoryRow(label: String, valueText: String, enabled: Boolean, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingSM, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White)
            Text(valueText, color = ZenithPrimaryMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(ZenithDimens.paddingSM))
        OutlinedButton(onClick = onClear, enabled = enabled) { Text("Очистить") }
    }
}
