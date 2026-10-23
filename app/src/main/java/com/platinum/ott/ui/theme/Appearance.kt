package com.platinum.ott.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Настройки «Внешний вид» (план 11). Касаются только каталога и интерфейса — плеер не затрагивают.

/** Акцентный цвет: 6 вариантов. [id] хранится в SharedPreferences — не менять выпущенные значения. */
enum class AccentColor(val id: String, val title: String, val color: Color) {
    CYAN("cyan", "Голубой", Color(0xFF4FD1FF)),
    GREEN("green", "Зелёный", Color(0xFF5FD38D)),
    ORANGE("orange", "Оранжевый", Color(0xFFFFA94D)),
    PINK("pink", "Розовый", Color(0xFFFF7AA8)),
    VIOLET("violet", "Фиолетовый", Color(0xFFB39DFF)),
    YELLOW("yellow", "Жёлтый", Color(0xFFFFD54F));

    /** Светлый вариант для значений настроек (раньше жёстко ZenithPrimaryMuted). */
    val muted: Color get() = androidx.compose.ui.graphics.lerp(color, Color.White, 0.45f)

    companion object {
        fun fromId(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: CYAN
    }
}

/** Как вписывать картинку в карточку: по форме (как раньше), всегда заполнять или всегда вписывать целиком. */
enum class ImageScaleMode(val id: String, val title: String) {
    AUTO("auto", "Авто"),
    FILL("fill", "Заполнять"),
    FIT("fit", "Вписывать");

    companion object {
        fun fromId(id: String?): ImageScaleMode = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Размер элементов каталога: 4 шага, шаг 1 — как было до появления настройки. */
val SIZE_STEP_TITLES = listOf("Мелкий", "Обычный", "Крупный", "Очень крупный")
val SIZE_STEP_MULTIPLIERS = listOf(0.85f, 1.0f, 1.15f, 1.3f)
const val DEFAULT_SIZE_STEP = 1

data class AppearanceSettings(
    val sizeStep: Int = DEFAULT_SIZE_STEP,
    val accent: AccentColor = AccentColor.CYAN,
    val imageScale: ImageScaleMode = ImageScaleMode.AUTO
) {
    val sizeMultiplier: Float get() = SIZE_STEP_MULTIPLIERS[sizeStep.coerceIn(0, SIZE_STEP_MULTIPLIERS.lastIndex)]
}

val LocalAppearance = staticCompositionLocalOf { AppearanceSettings() }
