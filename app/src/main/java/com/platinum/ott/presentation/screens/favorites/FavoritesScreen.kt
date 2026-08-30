package com.platinum.ott.presentation.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.platinum.ott.core.platform.ZenithDimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.tv.material3.*
import com.platinum.ott.data.local.entity.FavoriteEntity
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.components.NavSidebar

// Папки: раньше бэкенд (FavoritesUseCase.createFolder/deleteFolder/moveToFolder,
// FavoritesViewModel.folders) был готов, но UI нигде их не показывал и не
// давал создать/переместить. -1L — служебный сентинел для чипа "Без папки"
// (реальные id папок — autoGenerate начиная с 1, так что с ними не пересечётся).
private const val NO_FOLDER_SENTINEL = -1L

// PROMPT_NAVIGATION_SIDEBAR.md — постоянный сайдбар вместо прежнего
// "Избранное" без выхода куда-либо, кроме возврата на Home. onBackPressed
// раньше принимался параметром, но нигде в теле не вызывался — убран вместе
// с добавлением navController (тот же параметр, что и NavSidebar на других
// верхнеуровневых TV-экранах).
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(navController: NavHostController, onItemClick: (FavoriteEntity) -> Unit, viewModel: FavoritesViewModel = hiltViewModel()) {
    val favorites by viewModel.favorites.collectAsState(initial = emptyList())
    val folders by viewModel.folders.collectAsState(initial = emptyList())
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var showFolderManager by remember { mutableStateOf(false) }
    var moveTargetContentId by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavSidebar(navController)
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
            Text("Избранное", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(ZenithDimens.paddingS))

            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                FilterChip(selectedType == null, onClick = { selectedType = null }) { Text("Все") }
                // ANIME теперь фильтрует по isAnime (см. FavoriteEntity), а не по
                // contentType — contentType занят под MOVIE/SERIES и нужен для
                // роутинга, аниме-флаг с ним не пересекается.
                listOf("MOVIE", "SERIES", "ANIME").forEach { type ->
                    FilterChip(selectedType == type, onClick = { selectedType = if (selectedType == type) null else type }) { Text(type) }
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))

            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                FilterChip(selectedFolderId == null, onClick = { selectedFolderId = null }) { Text("Все папки") }
                FilterChip(selectedFolderId == NO_FOLDER_SENTINEL, onClick = { selectedFolderId = if (selectedFolderId == NO_FOLDER_SENTINEL) null else NO_FOLDER_SENTINEL }) { Text("Без папки") }
                folders.forEach { folder ->
                    FilterChip(selectedFolderId == folder.id, onClick = { selectedFolderId = if (selectedFolderId == folder.id) null else folder.id }) { Text(folder.name) }
                }
                OutlinedButton(onClick = { showFolderManager = true }) { Text("+ Папка") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

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

            LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                items(filtered, key = { it.contentId }) { fav ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            MovieCard(title = fav.title, poster = fav.poster ?: "", year = 0, onClick = { onItemClick(fav) })
                            // Единое меню на карточке (PROMPT_FAVORITES_REDESIGN.md,
                            // п.3) заменяет собой прежнюю отдельную кнопку "📁 В
                            // папку" — три действия в одном месте: переместить в
                            // папку, переключить аниме, убрать из избранного.
                            // Отдельный фокусируемый элемент, не долгое нажатие —
                            // на TV-пульте "press and hold" не универсальный жест.
                            var showMenu by remember(fav.contentId) { mutableStateOf(false) }
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.align(Alignment.TopEnd)
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
