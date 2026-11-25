package com.platinum.ott.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import com.platinum.ott.ui.theme.ZenithOnSurface

/**
 * Логотип канала с запасным видом: пока грузится или если не загрузился (404, 403, не тот формат,
 * нет сети) — инициалы названия вместо пустого места. Раньше при сбое оставался пустой квадрат,
 * и казалось, что «логотипы не появляются».
 */
@Composable
fun ChannelLogoImage(
    model: Any?,
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        loading = { LogoInitials(name) },
        error = { LogoInitials(name) }
    )
}

@Composable
private fun LogoInitials(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(2).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = ZenithOnSurface.copy(alpha = 0.4f),
            maxLines = 1
        )
    }
}
