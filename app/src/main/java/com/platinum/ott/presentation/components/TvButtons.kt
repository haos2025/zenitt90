package com.platinum.ott.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.safeRequestFocus
import com.platinum.ott.ui.theme.ZenithError
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithShapeLarge
import com.platinum.ott.ui.theme.ZenithSurface

/**
 * Кнопка-«пилюля» для пульта ТВ. Стандартные кнопки tv-material при фокусе заливаются почти белым
 * цветом (и при нажатии тоже), текст на них темнеет: выглядит резко и читается хуже. Здесь фокус —
 * мягкая заливка акцентом и цветная рамка, без белого. danger — красный акцент (для удаления).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPillButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentDescription: String? = label,
    danger: Boolean = false
) {
    val accent = if (danger) ZenithError else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = ZenithOnSurface,
            focusedContainerColor = accent.copy(alpha = 0.24f),
            focusedContentColor = ZenithOnSurface,
            pressedContainerColor = accent.copy(alpha = 0.38f),
            pressedContentColor = ZenithOnSurface
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, accent))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            Modifier.padding(horizontal = if (label != null) 16.dp else 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription, tint = ZenithOnSurface, modifier = Modifier.size(20.dp))
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = ZenithOnSurface)
            }
        }
    }
}

/**
 * Подтверждение опасного действия для пульта ТВ. Фокус при открытии стоит на «Отмена»,
 * чтобы случайное нажатие OK ничего не удаляло. «Назад» на пульте тоже отменяет.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .clip(ZenithShapeLarge)
                .background(ZenithSurface)
                .border(1.dp, ZenithOnSurface.copy(alpha = 0.10f), ZenithShapeLarge)
                .padding(ZenithDimens.paddingL)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = ZenithOnSurface)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = ZenithOnSurfaceVariant)
            Spacer(Modifier.height(ZenithDimens.paddingL))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TvPillButton(Icons.Default.Close, onDismiss, Modifier.focusRequester(cancelFocus), label = "Отмена")
                Spacer(Modifier.width(ZenithDimens.paddingSM))
                TvPillButton(Icons.Default.Delete, onConfirm, label = confirmLabel, danger = true)
            }
        }
    }
    LaunchedEffect(Unit) {
        // Диалог создаёт содержимое не мгновенно: ждём пару кадров, потом ставим фокус.
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        cancelFocus.safeRequestFocus()
    }
}
