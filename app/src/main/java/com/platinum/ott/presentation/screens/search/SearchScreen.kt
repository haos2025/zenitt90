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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun SearchScreen(onBackPressed: () -> Unit, onMovieClick: (String) -> Unit, initialQuery: String = "", viewModel: SearchViewModel = viewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(start = 56.dp, top = 56.dp, end = 56.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBackPressed) { Text("← Назад") }
            Spacer(Modifier.width(16.dp))
            BasicTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                textStyle = TextStyle(Color.White, 20.sp),
                cursorBrush = SolidColor(Color(0xFF6C63FF)),
                modifier = Modifier.weight(1f).background(Color.White.copy(0.08f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(16.dp, 12.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Название фильма или сериала…", style = TextStyle(Color.White.copy(0.3f), 20.sp))
                    inner()
                }
            )
        }
        Spacer(Modifier.height(24.dp))
        when (val state = uiState) {
            is SearchUiState.Idle -> Text("Начните вводить название (минимум 2 символа)", color = Color.Gray)
            is SearchUiState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.TopCenter) { CircularProgressIndicator() }
            is SearchUiState.Error -> Text("⚠ ${state.message}", color = Color(0xFFFF6B6B))
            is SearchUiState.Success -> {
                if (state.results.isEmpty()) {
                    Text("Ничего не нашлось по запросу «$query»", color = Color.Gray)
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(5), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(state.results, key = { it.id }) { movie -> MovieCard(movie = movie, onClick = { onMovieClick(movie.id) }) }
                    }
                }
            }
        }
    }
}
