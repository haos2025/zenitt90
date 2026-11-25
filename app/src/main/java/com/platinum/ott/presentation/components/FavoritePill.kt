package com.platinum.ott.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.platinum.ott.ui.theme.ZenithOnSurface

private const val FILL_MS = 320

/**
 * Пилюля «В избранное» ↔ «В избранном» для пульта ТВ: без сердечка, при добавлении плавно
 * заливается акцентом, текст меняется кросс-фейдом. Фокус — белая рамка, без изменения размера.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvFavoritePill(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val idle = Color.White.copy(alpha = 0.10f)
    val container by animateColorAsState(if (isFavorite) accent else idle, tween(FILL_MS), label = "favFill")
    val focused by animateColorAsState(if (isFavorite) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.20f), tween(FILL_MS), label = "favFillFocus")
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = ZenithOnSurface,
            focusedContainerColor = focused,
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.70f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (isFavorite) accent else Color.White.copy(alpha = 0.25f))),
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Crossfade(targetState = isFavorite, animationSpec = tween(FILL_MS), label = "favText") { fav ->
                Text(if (fav) "В избранном" else "В избранное", style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface)
            }
        }
    }
}

/** То же для телефона (Material3): пилюля с плавной заливкой акцентом. */
@Composable
fun PhoneFavoritePill(isFavorite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val container by animateColorAsState(if (isFavorite) scheme.primary else scheme.surfaceVariant, tween(FILL_MS), label = "favFill")
    val content by animateColorAsState(if (isFavorite) scheme.onPrimary else scheme.onSurface, tween(FILL_MS), label = "favContent")
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Crossfade(targetState = isFavorite, animationSpec = tween(FILL_MS), label = "favText") { fav ->
                androidx.compose.material3.Text(if (fav) "В избранном" else "В избранное", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
        }
    }
}
