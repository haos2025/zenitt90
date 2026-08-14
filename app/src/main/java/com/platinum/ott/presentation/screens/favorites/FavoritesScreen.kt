package com.platinum.ott.presentation.screens.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.platinum.ott.core.platform.ZenithDimens
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.platinum.ott.presentation.components.MovieCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FavoritesScreen(onBackPressed: () -> Unit, onItemClick: (com.platinum.ott.data.local.entity.FavoriteEntity) -> Unit, viewModel: FavoritesViewModel = hiltViewModel()) {
    val favorites by viewModel.favorites.collectAsState(initial = emptyList())
    val folders by viewModel.folders.collectAsState(initial = emptyList())
    var selectedType by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
        Text("Избранное", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(ZenithDimens.paddingS))
        Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
            FilterChip(selectedType == null, onClick = { selectedType = null }) { Text("Все") }
            listOf("MOVIE", "SERIES", "ANIME").forEach { type ->
                FilterChip(selectedType == type, onClick = { selectedType = if (selectedType == type) null else type }) { Text(type) }
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))
        val filtered = if (selectedType == null) favorites else favorites.filter { it.contentType == selectedType }
        LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
            items(filtered, key = { it.contentId }) { fav -> MovieCard(title = fav.title, poster = fav.poster ?: "", year = 0, onClick = { onItemClick(fav) }) }
        }
    }
}
