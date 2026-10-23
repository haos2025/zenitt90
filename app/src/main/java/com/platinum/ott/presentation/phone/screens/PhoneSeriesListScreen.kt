package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
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
import com.platinum.ott.presentation.components.CardCollection
import com.platinum.ott.presentation.components.PosterListRow
import com.platinum.ott.ui.theme.CardSection
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
    // ФИКС (аудит): тот же паттерн, что в TV-версии (SeriesListScreen.kt).
    LaunchedEffect(Unit) { viewModel.load() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Сериалы") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is SeriesListUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = ZenithDimens.paddingXL))
                is SeriesListUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(ZenithDimens.paddingM))
                is SeriesListUiState.Success -> {
                    if (state.series.isEmpty()) {
                        Text("Сериалов не найдено. Раздел заполняется из Xtream и M3U-названий вида S01E02.", color = ZenithOnSurfaceVariant, modifier = Modifier.padding(ZenithDimens.paddingM))
                    } else {
                        CardCollection(
                            items = state.series,
                            key = { it.seriesId },
                            section = CardSection.MOVIES,
                            isTv = false,
                            contentPadding = PaddingValues(ZenithDimens.paddingSM),
                            horizontalGap = ZenithDimens.paddingSM,
                            listItem = { s, _ ->
                                PosterListRow(title = s.title, poster = s.poster, subtitle = "${s.episodeCount} эп.", onClick = { navController.navigate("series/${s.seriesId}") })
                            },
                            cardItem = { s, _, wide ->
                                MovieCard(title = s.title, poster = s.poster, year = 0, onClick = { navController.navigate("series/${s.seriesId}") }, wide = wide)
                            }
                        )
                    }
                }
            }
        }
    }
}
