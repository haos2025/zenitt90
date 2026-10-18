package com.platinum.ott.presentation.phone.screens

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.presentation.screens.channels.ChannelsUiState
import com.platinum.ott.presentation.screens.channels.ChannelsViewModel

private const val ALL_CATEGORY = "__all__"
private const val FAV_CATEGORY = "__fav__"
private const val PREFS = "phone_channels"
private const val KEY_FAVS = "favorite_ids"

/**
 * Телефон: список каналов «как в TiviMate» — строка = номер, логотип, название и
 * «сейчас в эфире» с полосой прогресса. Сверху поиск и чипы категорий («Все», «Избранные», группы).
 * Тап — смотреть; долгое нажатие — меню (избранное, телепрограмма).
 * «Избранные» хранятся локально на этом телефоне (SharedPreferences), в синхронизацию не входят.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(prefs.getStringSet(KEY_FAVS, emptySet()).orEmpty().toSet()) }
    fun toggleFavorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        prefs.edit().putStringSet(KEY_FAVS, favorites).apply()
    }

    val subscribedChannels = remember(uiState) {
        (uiState as? ChannelsUiState.Success)?.channels.orEmpty().filter { it.isSubscribed }.sortedBy { it.channelNumber }
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORY) }

    val categories = remember(subscribedChannels) {
        subscribedChannels.groupBy { it.category?.ifBlank { null } ?: "Без категории" }
            .mapValues { it.value.size }.toList().sortedByDescending { it.second }
    }
    val visibleChannels = remember(subscribedChannels, selectedCategory, searchQuery, favorites) {
        subscribedChannels
            .filter {
                when (selectedCategory) {
                    ALL_CATEGORY -> true
                    FAV_CATEGORY -> it.id in favorites
                    else -> (it.category?.ifBlank { null } ?: "Без категории") == selectedCategory
                }
            }
            .filter { searchQuery.isBlank() || it.canonicalName.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Каналы") },
                    navigationIcon = { IconButton(onClick = onBackPressed) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } },
                    actions = {
                        IconButton(onClick = { searchActive = !searchActive; if (!searchActive) searchQuery = "" }) { Icon(Icons.Filled.Search, contentDescription = "Поиск") }
                        IconButton(onClick = onManageClick) { Icon(Icons.Filled.Settings, contentDescription = "Управление каналами") }
                    }
                )
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск канала") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS)
                    )
                }
                if (subscribedChannels.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = ZenithDimens.paddingM, vertical = ZenithDimens.paddingXS),
                        horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
                    ) {
                        item("all") { CategoryChip("Все", selectedCategory == ALL_CATEGORY) { selectedCategory = ALL_CATEGORY } }
                        item("fav") { CategoryChip("★ Избранные", selectedCategory == FAV_CATEGORY) { selectedCategory = FAV_CATEGORY } }
                        items(categories, key = { "c_" + it.first }) { (name, _) ->
                            CategoryChip(name, selectedCategory == name) { selectedCategory = name }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            subscribedChannels.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    "Нет подписанных каналов — зайдите в \"Управление каналами\",\nчтобы подписаться на каналы из уже добавленных источников",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(ZenithDimens.paddingXL)
                )
            }
            visibleChannels.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    if (selectedCategory == FAV_CATEGORY && searchQuery.isBlank()) "Избранных каналов пока нет.\nДолгое нажатие на канал — «В избранное»." else "Ничего не найдено",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(ZenithDimens.paddingXL)
                )
            }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = ZenithDimens.paddingL)) {
                itemsIndexed(visibleChannels, key = { i, c -> "${i}_${c.id}" }) { _, ch ->
                    ChannelRow(
                        item = ch,
                        isFavorite = ch.id in favorites,
                        viewModel = viewModel,
                        onClick = { onPlayChannel(ch.id) },
                        onToggleFavorite = { toggleFavorite(ch.id) },
                        onOpenProgram = onOpenEpgGrid
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    item: ChannelUiItem,
    isFavorite: Boolean,
    viewModel: ChannelsViewModel,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenProgram: () -> Unit
) {
    // Программа грузится только для реально показанных строк (LazyColumn компонует лишь видимые)
    // и обновляется раз в минуту.
    var now by remember(item.id) { mutableStateOf<Pair<String, Float>?>(null) }
    LaunchedEffect(item.id) {
        while (true) {
            now = viewModel.nowPlaying(item.id)
            kotlinx.coroutines.delay(60_000)
        }
    }
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = ZenithDimens.paddingM, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${item.channelNumber}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.width(34.dp))
            Box(Modifier.size(46.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f)), Alignment.Center) {
                if (item.logo != null) AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.72f))
                else Text(item.canonicalName.take(2).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Spacer(Modifier.width(ZenithDimens.paddingM))
            Column(Modifier.weight(1f)) {
                Text(item.canonicalName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val program = now
                if (program != null) {
                    Text(program.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { program.second }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape))
                }
            }
            if (isFavorite) Icon(Icons.Filled.Star, "В избранном", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = ZenithDimens.paddingS).size(18.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Убрать из избранного" else "В избранное") },
                onClick = { menu = false; onToggleFavorite() }
            )
            DropdownMenuItem(text = { Text("Телепрограмма") }, onClick = { menu = false; onOpenProgram() })
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(title, maxLines = 1) })
}
