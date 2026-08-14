package com.platinum.ott.presentation.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import com.platinum.ott.presentation.components.MovieCard

// Раньше поиск существовал только снаружи приложения (системный поиск TV
// через MovieSearchProvider) — внутри самого приложения зайти в поиск
// текстом было вообще нельзя, incoming initialQuery — для случая, когда
// пользователь ввёл запрос в системном поиске TV и нажал "Найти" целиком
// (ACTION_SEARCH), а не выбрал готовую подсказку.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(onBackPressed: () -> Unit, onMovieClick: (String) -> Unit, initialQuery: String = "", viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = ZenithDimens.tvOverscanPadding, top = ZenithDimens.tvOverscanPadding, end = ZenithDimens.tvOverscanPadding, bottom = ZenithDimens.paddingL)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackPressed) { Text("← Назад") }
            Spacer(Modifier.width(ZenithDimens.paddingM))
            BasicTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                textStyle = TextStyle(Color.White, 20.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).background(Color.White.copy(0.08f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(ZenithDimens.paddingM, ZenithDimens.paddingSM),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Название фильма или сериала…", style = TextStyle(Color.White.copy(0.3f), 20.sp))
                    inner()
                }
            )
        }
        Spacer(Modifier.height(ZenithDimens.paddingL))
        when (val state = uiState) {
            is SearchUiState.Idle -> Text("Начните вводить название (минимум 2 символа)", color = Color.Gray)
            is SearchUiState.Loading -> Box(Modifier.fillMaxWidth().padding(top = ZenithDimens.paddingXL), Alignment.TopCenter) { CircularProgressIndicator() }
            is SearchUiState.Error -> Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
            is SearchUiState.Success -> {
                if (state.results.isEmpty()) {
                    Text("Ничего не нашлось по запросу «$query»", color = Color.Gray)
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM), verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                        items(state.results, key = { it.id }) { movie -> MovieCard(movie = movie, onClick = { onMovieClick(movie.id) }) }
                    }
                }
            }
        }
    }
}
