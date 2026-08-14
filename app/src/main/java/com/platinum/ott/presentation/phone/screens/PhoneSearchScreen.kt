package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import com.platinum.ott.presentation.screens.search.SearchUiState
import com.platinum.ott.presentation.screens.search.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSearchScreen(navController: NavHostController, initialQuery: String = "", viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery) }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Название фильма или сериала…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") } }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (val state = uiState) {
                is SearchUiState.Idle -> Text("Начните вводить название (минимум 2 символа)", color = Color.Gray, modifier = Modifier.padding(ZenithDimens.paddingM))
                is SearchUiState.Loading -> Box(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXL), Alignment.TopCenter) { CircularProgressIndicator() }
                is SearchUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(ZenithDimens.paddingM))
                is SearchUiState.Success -> {
                    if (state.results.isEmpty()) {
                        Text("Ничего не нашлось по запросу «$query»", color = Color.Gray, modifier = Modifier.padding(ZenithDimens.paddingM))
                    } else {
                        LazyVerticalGrid(columns = GridCells.Adaptive(140.dp), contentPadding = PaddingValues(ZenithDimens.paddingSM), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                            items(state.results, key = { it.id }) { movie -> MovieCard(movie = movie, onClick = { navController.navigate("detail/${movie.id}") }) }
                        }
                    }
                }
            }
        }
    }
}
