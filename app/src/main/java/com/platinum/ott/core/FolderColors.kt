package com.platinum.ott.core

import androidx.compose.ui.graphics.Color

/**
 * Готовые цвета папок избранного (вместо свободного выбора цвета — на пульте
 * ТВ неудобно, и цвета гарантированно читаются на тёмной теме). Первый —
 * прежний дефолт FolderEntity.color, поэтому у уже существующих папок цвет
 * совпадает с первым пунктом палитры.
 */
val FOLDER_COLORS = listOf(
    "#6C63FF", // фиолетовый (дефолт)
    "#4FD1FF", // голубой
    "#34D399", // зелёный
    "#F59E0B", // оранжевый
    "#EF4444", // красный
    "#EC4899", // розовый
    "#FACC15", // жёлтый
    "#9CA3AF"  // серый
)

fun parseFolderColor(hex: String): Color =
    try { Color(android.graphics.Color.parseColor(hex)) } catch (_: IllegalArgumentException) { Color(0xFF6C63FF) }

fun nextFolderColor(current: String): String {
    val i = FOLDER_COLORS.indexOfFirst { it.equals(current, ignoreCase = true) }
    return FOLDER_COLORS[(if (i < 0) 0 else i + 1) % FOLDER_COLORS.size]
}
