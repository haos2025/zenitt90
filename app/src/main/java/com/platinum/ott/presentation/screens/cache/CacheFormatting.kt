package com.platinum.ott.presentation.screens.cache

import kotlin.math.roundToInt

/** "0 Б" / "128 КБ" / "12.4 МБ" — используется и TV, и телефон-версией экрана. */
fun formatCacheBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 Б"
    bytes < 1024L -> "$bytes Б"
    bytes < 1024L * 1024L -> "${bytes / 1024L} КБ"
    else -> {
        val mb = bytes / (1024.0 * 1024.0)
        val rounded = (mb * 10).roundToInt() / 10.0
        "$rounded МБ"
    }
}
