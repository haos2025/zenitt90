package com.platinum.ott.core.platform

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Определяет, запущено ли приложение на Android TV */
fun isTV(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

enum class WindowSize { Compact, Medium, Expanded }

@Composable
fun rememberWindowSize(): WindowSize {
    val config = LocalConfiguration.current
    return remember(config) {
        val w = config.screenWidthDp
        when { w < 600 -> WindowSize.Compact; w < 840 -> WindowSize.Medium; else -> WindowSize.Expanded }
    }
}

/** Адаптивные размеры для обеих платформ */
object ZenithDimens {
    val cardWidth: Dp @Composable get() = when (rememberWindowSize()) {
        WindowSize.Compact -> 140.dp; WindowSize.Medium -> 160.dp; WindowSize.Expanded -> 180.dp
    }
    val cardHeight: Dp @Composable get() = cardWidth * 1.5f
    val paddingS: Dp = 8.dp; val paddingM: Dp = 16.dp; val paddingL: Dp = 24.dp
    val gridColumns: Int @Composable get() = when (rememberWindowSize()) {
        WindowSize.Compact -> 3; WindowSize.Medium -> 4; WindowSize.Expanded -> 6
    }
}
