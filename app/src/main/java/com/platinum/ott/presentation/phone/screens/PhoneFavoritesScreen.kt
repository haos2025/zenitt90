package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.navigation.navigateToTab

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
                FilterChip(selected = selectedFolderId == folder.id, onClick = { selectedFolderId = if (selectedFolderId == folder.id) null else folder.id }, label = { Text(folder.name) })
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

        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(ZenithDimens.paddingSM), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
            items(filtered, key = { it.contentId }) { fav ->
                Box {
                    MovieCard(title = fav.title, poster = fav.poster ?: "", year = 0, onClick = { navController.navigate(if (fav.contentType == "SERIES") "series/${fav.contentId}" else "detail/${fav.contentId}") })
                    // Единое меню на карточке (PROMPT_FAVORITES_REDESIGN.md,
                    // п.3) заменяет прежнюю отдельную иконку "Переместить в
                    // папку" — три действия в одном месте: переместить в
                    // папку, переключить аниме, убрать из избранного.
                    var showMenu by remember(fav.contentId) { mutableStateOf(false) }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(ZenithDimens.paddingS).clip(CircleShape).background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                    ) { Icon(Icons.Default.MoreVert, "Действия", tint = Color.White) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Переместить в папку") }, onClick = { showMenu = false; moveTargetContentId = fav.contentId })
                        DropdownMenuItem(text = { Text(if (fav.isAnime) "Аниме: выкл" else "Аниме: вкл") }, onClick = { showMenu = false; viewModel.setAnime(fav.contentId, !fav.isAnime) })
                        DropdownMenuItem(text = { Text("Убрать из избранного") }, onClick = { showMenu = false; viewModel.removeFavorite(fav.contentId) })
                    }
                }
            }
        }
    }
}

if (showFolderManager) {
    FolderManagerDialog(
        folders = folders,
        onCreate = viewModel::createFolder,
        onDelete = viewModel::deleteFolder,
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
