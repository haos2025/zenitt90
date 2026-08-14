package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.platinum.ott.presentation.components.MovieCard
import com.platinum.ott.presentation.screens.series.SeriesListUiState
import com.platinum.ott.presentation.screens.series.SeriesListViewModel
import com.platinum.ott.domain.model.Movie

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSeriesListScreen(navController: NavHostController, viewModel: SeriesListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Сериалы") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is SeriesListUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = ZenithDimens.paddingXL))
                is SeriesListUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(ZenithDimens.paddingM))
                is SeriesListUiState.Success -> {
                    if (state.series.isEmpty()) {
                        Text("Сериалов не найдено. Раздел заполняется из Xtream и M3U-названий вида S01E02.", color = Color.Gray, modifier = Modifier.padding(ZenithDimens.paddingM))
                    } else {
                        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(ZenithDimens.paddingSM), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                            items(state.series, key = { it.seriesId }) { s ->
                                MovieCard(
                                    movie = Movie(id = s.seriesId, year = 0, title = s.title, poster = s.poster, genre = s.genre),
                                    onClick = { navController.navigate("series/${s.seriesId}") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
