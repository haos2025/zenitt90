package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.components.ChannelTile
import com.platinum.ott.presentation.screens.channels.ChannelsUiState
import com.platinum.ott.presentation.screens.channels.ChannelsViewModel

private const val ALL_CATEGORY = "__all__"

/**
 * Телефон-версия ChannelsBrowseScreen.kt (TV) — та же идея (категории
 * слева + сетка справа), тот же ChannelsViewModel, material3 вместо
 * tv-material3. Уже сама плитка (ChannelTile.kt) общая для обеих платформ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneChannelsBrowseScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onOpenEpgGrid: () -> Unit,
    onManageClick: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val subscribedChannels = remember(uiState) {
        (uiState as? ChannelsUiState.Success)?.channels.orEmpty().filter { it.isSubscribed }
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORY) }

    val categoryCounts = remember(subscribedChannels) {
        subscribedChannels.groupBy { it.category?.ifBlank { null } ?: "Без категории" }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
    }
    val visibleChannels = remember(subscribedChannels, selectedCategory, searchQuery) {
        subscribedChannels
            .filter { selectedCategory == ALL_CATEGORY || (it.category?.ifBlank { null } ?: "Без категории") == selectedCategory }
            .filter { searchQuery.isBlank() || it.canonicalName.contains(searchQuery, ignoreCase = true) }
            .sortedBy { it.channelNumber }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Каналы") },
                    navigationIcon = { IconButton(onClick = onBackPressed) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
                    actions = {
                        IconButton(onClick = { searchActive = !searchActive }) { Icon(Icons.Filled.Search, contentDescription = "Поиск") }
                        IconButton(onClick = onOpenEpgGrid) { Icon(Icons.Filled.DateRange, contentDescription = "Программа передач") }
                        IconButton(onClick = onManageClick) { Icon(Icons.Filled.Settings, contentDescription = "Управление каналами") }
                    }
                )
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск канала") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingS)
                    )
                }
            }
        }
    ) { padding ->
        if (subscribedChannels.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет подписанных каналов — зайдите в \"Управление каналами\",\nчтобы подписаться на каналы из уже добавленных источников",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(ZenithDimens.paddingXL)
                )
            }
        } else {
            Row(Modifier.padding(padding).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.width(110.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(vertical = ZenithDimens.paddingS)
                ) {
                    item { CategoryChip("Все", subscribedChannels.size, selectedCategory == ALL_CATEGORY) { selectedCategory = ALL_CATEGORY } }
                    lazyColumnItems(categoryCounts, key = { it.first }) { (category, count) ->
                        CategoryChip(category, count, selectedCategory == category) { selectedCategory = category }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(ZenithDimens.paddingSM),
                    horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM),
                    verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)
                ) {
                    items(visibleChannels, key = { it.id }) { item ->
                        ChannelTile(item = item, onClick = { onPlayChannel(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingXS, vertical = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = ZenithDimens.paddingS, vertical = ZenithDimens.paddingSM)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
