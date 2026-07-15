package com.platinum.ott.presentation.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import com.platinum.ott.presentation.components.MovieCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HistoryScreen(onBackPressed: () -> Unit, onMovieClick: (String) -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val history by viewModel.history.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(24.dp)) {
        Text("История просмотров", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.contentId }) { entry ->
                val progress = if (entry.durationMs > 0) entry.positionMs.toFloat() / entry.durationMs else 0f
                Surface(onClick = { onMovieClick(entry.contentId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        MovieCard(title = entry.title, poster = entry.poster ?: "", year = 0, onClick = { onMovieClick(entry.contentId) })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(if (entry.completed) "Просмотрено" else "Прогресс: ${(progress * 100).toInt()}%", color = Color.Gray)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
