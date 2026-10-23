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

/** Форма карточки: высокий постер, широкая 16:9 или строка списка (миниатюра + текст). */
enum class CardStyle(val id: String, val title: String) {
    POSTER("poster", "Постер"),
    WIDE("wide", "Широкая"),
    LIST("list", "Список");

    companion object {
        fun fromId(id: String?): CardStyle? = entries.firstOrNull { it.id == id }
    }
}

/** Разделы, для которых форма карточки настраивается отдельно. [defaultStyle] — как было до настройки. */
enum class CardSection(val id: String, val title: String, val defaultStyle: CardStyle, val phoneDefaultStyle: CardStyle = defaultStyle) {
    MOVIES("movies", "Фильмы и сериалы", CardStyle.POSTER),
    FAVORITES("favorites", "Избранное", CardStyle.POSTER),
    HISTORY("history", "История", CardStyle.LIST),
    // Каналы: на ТВ раньше были плитки (POSTER), на телефоне — строки (LIST).
    CHANNELS("channels", "Каналы", CardStyle.POSTER, CardStyle.LIST)
}

/** Подпись стиля с учётом раздела: для каналов «Постер» — это плитка. */
fun CardStyle.titleFor(section: CardSection): String =
    if (section == CardSection.CHANNELS) when (this) {
        CardStyle.POSTER -> "Плитка"
        CardStyle.WIDE -> "Широкая плитка"
        CardStyle.LIST -> "Список"
    } else title

/** ТВ: как раскладывать карточки в избранном, истории и списке сериалов (для стиля «Список» не действует). */
enum class TvLayout(val id: String, val title: String) {
    GRID("grid", "Сетка"),
    CAROUSEL("carousel", "Лента");

    companion object {
        fun fromId(id: String?): TvLayout = entries.firstOrNull { it.id == id } ?: GRID
    }
}

data class AppearanceSettings(
    val sizeStep: Int = DEFAULT_SIZE_STEP,
    val accent: AccentColor = AccentColor.CYAN,
    val imageScale: ImageScaleMode = ImageScaleMode.AUTO,
    val cardStyles: Map<CardSection, CardStyle> = emptyMap(),
    val tvLayout: TvLayout = TvLayout.GRID
) {
    fun styleOf(section: CardSection, isTv: Boolean = true): CardStyle =
        cardStyles[section] ?: if (isTv) section.defaultStyle else section.phoneDefaultStyle

    val sizeMultiplier: Float get() = SIZE_STEP_MULTIPLIERS[sizeStep.coerceIn(0, SIZE_STEP_MULTIPLIERS.lastIndex)]
}

val LocalAppearance = staticCompositionLocalOf { AppearanceSettings() }
