package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.navigation.navigateToTab

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.platinum.ott.core.platform.ZenithDimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.components.CardCollection
import com.platinum.ott.presentation.components.PosterListRow
import com.platinum.ott.ui.theme.CardSection
import com.platinum.ott.presentation.screens.favorites.FolderDot
import com.platinum.ott.presentation.screens.favorites.FolderManagerDialog
import com.platinum.ott.presentation.screens.favorites.MoveToFolderDialog

// -1L — сентинел для чипа "Без папки", по аналогии с TV FavoritesScreen.kt
// (реальные id папок — autoGenerate начиная с 1).
private const val NO_FOLDER_SENTINEL = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneFavoritesScreen(navController: NavHostController) {

val viewModel: com.platinum.ott.presentation.screens.favorites.FavoritesViewModel = hiltViewModel()
val favorites by viewModel.favorites.collectAsState(initial = emptyList())
val folders by viewModel.folders.collectAsState(initial = emptyList())
val posterFallbacks by viewModel.posterFallbacks.collectAsState()
var selectedType by remember { mutableStateOf<String?>(null) }
var selectedFolderId by remember { mutableStateOf<Long?>(null) }
var showFolderManager by remember { mutableStateOf(false) }
var moveTargetContentId by remember { mutableStateOf<String?>(null) }

Scaffold(bottomBar = { PhoneBottomBar(navController) }) { padding ->
    Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ZenithDimens.paddingSM, vertical = ZenithDimens.paddingS),
            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)
        ) {
            FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("Все") })
            // ANIME фильтрует по isAnime, не по contentType — см. комментарий
            // в FavoriteEntity.kt: contentType занят под MOVIE/SERIES и нужен
            // для роутинга ниже (навигация на detail/ vs series/).
            listOf("MOVIE", "SERIES", "ANIME").forEach { type ->
                FilterChip(selected = selectedType == type, onClick = { selectedType = if (selectedType == type) null else type }, label = { Text(type) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = ZenithDimens.paddingSM),
            horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected = selectedFolderId == null, onClick = { selectedFolderId = null }, label = { Text("Все папки") })
            FilterChip(selected = selectedFolderId == NO_FOLDER_SENTINEL, onClick = { selectedFolderId = if (selectedFolderId == NO_FOLDER_SENTINEL) null else NO_FOLDER_SENTINEL }, label = { Text("Без папки") })
            folders.forEach { folder ->
                FilterChip(selected = selectedFolderId == folder.id, onClick = { selectedFolderId = if (selectedFolderId == folder.id) null else folder.id }, label = { Row(verticalAlignment = Alignment.CenterVertically) { FolderDot(folder.color); Spacer(Modifier.width(ZenithDimens.paddingXS)); Text(folder.name) } })
            }
            IconButton(onClick = { showFolderManager = true }) { Icon(Icons.Default.CreateNewFolder, "Управление папками") }
        }

        val filtered = favorites
            .filter { fav ->
                when (selectedType) {
                    null -> true
                    "ANIME" -> fav.isAnime
                    else -> fav.contentType == selectedType
                }
            }
            .filter { fav ->
                when (selectedFolderId) {
                    null -> true
                    NO_FOLDER_SENTINEL -> fav.folderId == null
                    else -> fav.folderId == selectedFolderId
                }
            }

        fun posterOf(fav: com.platinum.ott.data.local.entity.FavoriteEntity) = fav.poster?.takeIf { it.isNotBlank() } ?: posterFallbacks[fav.contentId] ?: ""
        fun open(fav: com.platinum.ott.data.local.entity.FavoriteEntity) = navController.navigate(if (fav.contentType == "SERIES") "series/${fav.contentId}" else "detail/${fav.contentId}")
        CardCollection(
            items = filtered,
            key = { it.contentId },
            section = CardSection.FAVORITES,
            isTv = false,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ZenithDimens.paddingSM),
            horizontalGap = ZenithDimens.paddingS,
            verticalGap = ZenithDimens.paddingSM,
            listItem = { fav, index ->
                var showMenu by remember(fav.contentId) { mutableStateOf(false) }
                PosterListRow(
                    title = fav.title,
                    poster = posterOf(fav),
                    subtitle = if (fav.contentType == "SERIES") "Сериал" else "Фильм",
                    onClick = { open(fav) },
                    trailing = {
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Действия", tint = ZenithOnSurface) }
                            FavoriteMenu(showMenu, { showMenu = false }, fav, index, filtered, viewModel) { moveTargetContentId = fav.contentId }
                        }
                    }
                )
            },
            cardItem = { fav, index, wide ->
                Box {
                    MovieCard(title = fav.title, poster = posterOf(fav), year = 0, onClick = { open(fav) }, contentScale = com.platinum.ott.presentation.components.contentScaleForId(fav.contentId), wide = wide)
                    // Единое меню на карточке (PROMPT_FAVORITES_REDESIGN.md, п.3): переместить в папку,
                    // переключить аниме, убрать из избранного.
                    var showMenu by remember(fav.contentId) { mutableStateOf(false) }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(ZenithDimens.paddingS).clip(CircleShape).background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    ) { Icon(Icons.Default.MoreVert, "Действия", tint = ZenithOnSurface) }
                    FavoriteMenu(showMenu, { showMenu = false }, fav, index, filtered, viewModel) { moveTargetContentId = fav.contentId }
                }
            }
        )
    }
}

if (showFolderManager) {
    FolderManagerDialog(
        folders = folders,
        onCreate = viewModel::createFolder,
        onDelete = viewModel::deleteFolder,
        onChangeColor = viewModel::setFolderColor,
        onDismiss = { showFolderManager = false }
    )
}

moveTargetContentId?.let { contentId ->
    MoveToFolderDialog(
        folders = folders,
        onSelect = { folderId -> viewModel.moveToFolder(contentId, folderId); moveTargetContentId = null },
        onDismiss = { moveTargetContentId = null }
    )
}
}

@Composable
private fun FavoriteMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    fav: com.platinum.ott.data.local.entity.FavoriteEntity,
    index: Int,
    filtered: List<com.platinum.ott.data.local.entity.FavoriteEntity>,
    viewModel: com.platinum.ott.presentation.screens.favorites.FavoritesViewModel,
    onMoveToFolder: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (index > 0) DropdownMenuItem(text = { Text("Сдвинуть раньше") }, onClick = { onDismiss(); viewModel.moveFavorite(fav.contentId, filtered[index - 1].contentId) })
        if (index < filtered.lastIndex) DropdownMenuItem(text = { Text("Сдвинуть позже") }, onClick = { onDismiss(); viewModel.moveFavorite(fav.contentId, filtered[index + 1].contentId) })
        DropdownMenuItem(text = { Text("Переместить в папку") }, onClick = { onDismiss(); onMoveToFolder() })
        DropdownMenuItem(text = { Text(if (fav.isAnime) "Аниме: выкл" else "Аниме: вкл") }, onClick = { onDismiss(); viewModel.setAnime(fav.contentId, !fav.isAnime) })
        DropdownMenuItem(text = { Text("Убрать из избранного") }, onClick = { onDismiss(); viewModel.removeFavorite(fav.contentId) })
    }
}
