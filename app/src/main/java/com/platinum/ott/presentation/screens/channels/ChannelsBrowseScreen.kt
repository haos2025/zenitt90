package com.platinum.ott.presentation.screens.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.components.ChannelTile
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.ZenithShapeSmall

private const val ALL_CATEGORY = "__all__"

/**
 * РЕДИЗАЙН (разбор IPTV с нуля, по запросу Shadow — "вариант B": просмотр
 * отдельно от управления; визуальная структура — "категории слева
 * колонкой + сетка справа"). Это теперь основной путь "посмотреть
 * телевизор": быстро, только подписанные каналы, логотипы крупно,
 * категории слева для мгновенного перехода без прокрутки всего списка.
 * Всё административное (переименовать/слить/удалить/подписка) —
 * ChannelsManageScreen.kt, отдельный экран из Настроек.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelsBrowseScreen(
    onBackPressed: () -> Unit,
    onPlayChannel: (String) -> Unit,
    onOpenEpgGrid: () -> Unit,
    onManageClick: () -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Тот же фикс, что и в ChannelsManageScreen.kt — обновляем список при
    // возврате (например, после того как что-то поменяли в "Управлении").
    LaunchedEffect(Unit) { viewModel.load() }

    // Браузинг показывает ТОЛЬКО подписанные каналы — кандидаты на
    // слияние/неподписанные тут ни к чему, это дело ChannelsManageScreen.kt.
    val subscribedChannels = remember(uiState) {
        (uiState as? ChannelsUiState.Success)?.channels.orEmpty().filter { it.isSubscribed }
    }

    var searchQuery by remember { mutableStateOf("") }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.paddingXXL, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingXXL)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Каналы", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.weight(1f))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(Color.White, 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(220.dp).background(Color.White.copy(alpha = 0.08f), ZenithShapeSmall).padding(ZenithDimens.paddingM, ZenithDimens.paddingSM),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("Поиск", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.bodyMedium)
                    inner()
                }
            )
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(onClick = onOpenEpgGrid) { Text("Программа передач") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(onClick = onManageClick) { Text("Управление") }
            Spacer(Modifier.width(ZenithDimens.paddingS))
            OutlinedButton(onClick = onBackPressed) { Text("Назад") }
        }
        Spacer(Modifier.height(ZenithDimens.paddingXL))

        if (subscribedChannels.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Нет подписанных каналов — зайдите в \"Управление\", чтобы\nподписаться на каналы из уже добавленных источников",
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        } else {
            Row(Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.width(220.dp),
                    verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingXS)
                ) {
                    item {
                        CategoryRow(
                            title = "Все каналы", count = subscribedChannels.size,
                            selected = selectedCategory == ALL_CATEGORY,
                            onClick = { selectedCategory = ALL_CATEGORY }
                        )
                    }
                    lazyColumnItems(categoryCounts, key = { it.first }) { (category, count) ->
                        CategoryRow(
                            title = category, count = count,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
                Spacer(Modifier.width(ZenithDimens.paddingL))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.weight(1f),
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
private fun CategoryRow(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZenithShapeSmall)
            .background(
                when {
                    isFocused -> ZenithFocusContainerActive
                    selected -> Color.White.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingSM),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
            maxLines = 1
        )
        Text("$count", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
    }
}
