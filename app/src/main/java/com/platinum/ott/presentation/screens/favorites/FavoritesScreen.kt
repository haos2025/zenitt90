package com.platinum.ott.presentation.screens.favorites

import com.platinum.ott.ui.theme.ZenithOnSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
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
import com.platinum.ott.presentation.components.CardCollection
import com.platinum.ott.presentation.components.PosterListRow
import com.platinum.ott.ui.theme.CardSection
import androidx.compose.material3.IconButton

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
    val posterFallbacks by viewModel.posterFallbacks.collectAsState()
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    var showFolderManager by remember { mutableStateOf(false) }
    var moveTargetContentId by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavSidebar(navController)
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
            Text("Избранное", style = MaterialTheme.typography.displaySmall, color = ZenithOnSurface)
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
                    FilterChip(selectedFolderId == folder.id, onClick = { selectedFolderId = if (selectedFolderId == folder.id) null else folder.id }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FolderDot(folder.color)
                            Spacer(Modifier.width(ZenithDimens.paddingXS))
                            Text(folder.name)
                        }
                    }
                }
                OutlinedButton(scale = OutlinedButtonDefaults.scale(focusedScale = 1f), onClick = { showFolderManager = true }) { Text("+ Папка") }
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

            fun posterOf(fav: FavoriteEntity) = fav.poster?.takeIf { it.isNotBlank() } ?: posterFallbacks[fav.contentId] ?: ""
            CardCollection(
                items = filtered,
                key = { it.contentId },
                section = CardSection.FAVORITES,
                isTv = true,
                listItem = { fav, index ->
                    var showMenu by remember(fav.contentId) { mutableStateOf(false) }
                    PosterListRow(
                        title = fav.title,
                        poster = posterOf(fav),
                        subtitle = if (fav.contentType == "SERIES") "Сериал" else "Фильм",
                        thumbWidth = 96.dp,
                        onClick = { onItemClick(fav) },
                        trailing = {
                            Box {
                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Действия", tint = ZenithOnSurface) }
                                FavoriteActionsMenu(showMenu, { showMenu = false }, fav, index, filtered, viewModel) { moveTargetContentId = fav.contentId }
                            }
                        }
                    )
                },
                cardItem = { fav, index, wide ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box {
                            MovieCard(title = fav.title, poster = posterOf(fav), year = 0, onClick = { onItemClick(fav) }, contentScale = com.platinum.ott.presentation.components.contentScaleForId(fav.contentId), wide = wide)
                        }
                        // Действия — отдельная кнопка ПОД постером (раньше «⋮» лежала на углу карточки,
                        // и пультом на неё нельзя было навести фокус).
                        Box {
                            var showMenu by remember(fav.contentId) { mutableStateOf(false) }
                            var actionFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { showMenu = true },
                                modifier = Modifier.padding(top = ZenithDimens.paddingXS).onFocusChanged { actionFocused = it.isFocused },
                                shape = ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(50)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.08f),
                                    focusedContainerColor = com.platinum.ott.ui.theme.ZenithFocusContainerActive
                                ),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary))
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MoreVert, null, tint = ZenithOnSurface, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Действия", style = MaterialTheme.typography.labelMedium, color = ZenithOnSurface)
                                }
                            }
                            FavoriteActionsMenu(showMenu, { showMenu = false }, fav, index, filtered, viewModel) { moveTargetContentId = fav.contentId }
                        }
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

// Меню действий над избранным — общее для карточки и строки списка.
@Composable
private fun FavoriteActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    fav: FavoriteEntity,
    index: Int,
    filtered: List<FavoriteEntity>,
    viewModel: FavoritesViewModel,
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
