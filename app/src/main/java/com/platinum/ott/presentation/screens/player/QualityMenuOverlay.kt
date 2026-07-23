package com.platinum.ott.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.*
import com.platinum.ott.domain.model.StreamVariant

/**
 * Оверлей выбора качества потока.
 * Появляется по нажатию кнопки Menu на пульте ДУ.
 * D-pad навигация через LazyColumn.
 *
 * Расположен в правой части экрана чтобы не перекрывать видео полностью.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QualityMenuOverlay(
    variants: List<StreamVariant>,
    currentVariant: StreamVariant,
    onSelectVariant: (StreamVariant) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Полупрозрачный фон при нажатии вне меню
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        // Панель меню
        Column(
            modifier = Modifier
                .width(240.dp)
                .background(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                )
                .padding(vertical = 24.dp)
        ) {
            Text(
                text     = "Качество",
                style    = MaterialTheme.typography.titleLarge,
                color    = Color.White,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding      = PaddingValues(horizontal = 12.dp)
            ) {
                items(
                    items = variants,
                    key   = { it.url }
                ) { variant ->
                    val isSelected = variant.url == currentVariant.url

                    Surface(
                        onClick = { onSelectVariant(variant) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor        = if (isSelected)
                                Color(0xFF6C63FF).copy(alpha = 0.3f)
                            else
                                Color.Transparent,
                            focusedContainerColor = Color(0xFF6C63FF).copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text  = variant.quality,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) Color(0xFF6C63FF) else Color.White
                                )
                                // Раньше здесь показывалось только качество —
                                // пользователь не видел, пришёл ли вариант с
                                // backend или от установленного плагина
                                // (Задача 2, гибридная гонка при воспроизведении).
                                Text(
                                    text  = variant.source,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text  = "✓",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF6C63FF)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка закрытия
            Surface(
                onClick  = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor        = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White.copy(alpha = 0.15f)
                )
            ) {
                Text(
                    text     = "Закрыть",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
