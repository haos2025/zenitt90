package com.platinum.ott.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import com.platinum.ott.ui.theme.ZenithFocusContainerActive

/** Тумблер для ТВ с хорошо видимым фокусом: подсвеченная «таблетка» с рамкой вокруг переключателя. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(if (focused) ZenithFocusContainerActive else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .padding(6.dp)
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
