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
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.domain.model.CacheOverview
import com.platinum.ott.presentation.screens.cache.CacheManagementViewModel
import com.platinum.ott.presentation.screens.cache.CacheUiState
import com.platinum.ott.presentation.screens.cache.formatCacheBytes

/**
 * Телефон-версия PROMPT_CACHE_MANAGEMENT.md (TV — CacheManagementScreen.kt).
 * Тот же CacheManagementViewModel, та же логика — отличается только
 * версткой (Card вместо TV Surface-строк, как и остальные экраны
 * настроек на телефоне).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneCacheManagementScreen(navController: NavHostController, viewModel: CacheManagementViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление кэшем") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(ZenithDimens.paddingM)
                .verticalScroll(rememberScrollState())
        ) {
            when (val state = uiState) {
                is CacheUiState.Loading -> {
                    Box(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXL), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is CacheUiState.Loaded -> CacheOverviewCards(
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
}

@Composable
private fun CacheOverviewCards(
    overview: CacheOverview,
    onClearCatalog: () -> Unit,
    onClearPlaylist: () -> Unit,
    onClearMetadata: () -> Unit,
    onClearPosters: () -> Unit,
    onClearCrashLogs: () -> Unit
) {
    CacheCategoryCard("Каталог с бэкенда", "${overview.catalogEntries} записей", enabled = overview.catalogEntries > 0, onClear = onClearCatalog)
    CacheCategoryCard("Плейлист M3U/Xtream", "${overview.playlistEntries} записей", enabled = overview.playlistEntries > 0, onClear = onClearPlaylist)
    CacheCategoryCard("TMDB-метаданные", "${overview.metadataEntries} записей", enabled = overview.metadataEntries > 0, onClear = onClearMetadata)
    CacheCategoryCard("Постеры", formatCacheBytes(overview.posterCacheBytes), enabled = overview.posterCacheBytes > 0, onClear = onClearPosters)
    CacheCategoryCard("Краш-логи", "${formatCacheBytes(overview.crashLogBytes)} · ${overview.crashLogCount} файлов", enabled = overview.crashLogCount > 0, onClear = onClearCrashLogs)
}

@Composable
private fun CacheCategoryCard(label: String, valueText: String, enabled: Boolean, onClear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS)) {
        Row(
            Modifier.fillMaxWidth().padding(ZenithDimens.paddingM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(valueText, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(ZenithDimens.paddingSM))
            OutlinedButton(onClick = onClear, enabled = enabled) { Text("Очистить") }
        }
    }
}
