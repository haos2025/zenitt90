package com.platinum.ott.core.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Не даёт экрану гаснуть и не пускает заставку ТВ, пока открыт экран плеера: флаг окна активности
 * (одного `keepScreenOn` у PlayerView на части приставок недостаточно). При выходе флаг снимается.
 */
@Composable
fun KeepScreenOnEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        var c: Context? = context
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val window = (c as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
