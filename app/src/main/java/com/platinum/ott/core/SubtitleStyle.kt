package com.platinum.ott.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Оформление субтитров «как в YouTube»: размер, цвет и прозрачность текста, фон, контур/тень,
 * положение по высоте и сдвиг по времени. Одни и те же настройки действуют и на субтитры из
 * распознавания речи (рисуем сами), и на обычные дорожки ExoPlayer (через SubtitleView).
 * Сдвиг по времени работает только для распознанных субтитров.
 */
data class SubtitleStyle(
    val sizePercent: Int = 100,
    val textColor: Int = 0xFFFFFF,
    val textOpacity: Int = 100,
    val bgColor: Int = 0x000000,
    val bgOpacity: Int = 75,
    val edge: Int = EDGE_NONE,
    /** Отступ от нижнего края в процентах высоты экрана. */
    val bottomPercent: Int = 10,
    val offsetMs: Int = 0
) {
    companion object {
        const val EDGE_NONE = 0
        const val EDGE_SHADOW = 1
        const val EDGE_OUTLINE = 2
    }
}

/** Один пункт настроек: заголовок, текущее значение текстом и переход к следующему значению по кругу. */
class SubtitleSetting(
    val title: String,
    val valueLabel: (SubtitleStyle) -> String,
    val next: (SubtitleStyle) -> SubtitleStyle
)

private val COLORS = listOf(
    "Белый" to 0xFFFFFF, "Жёлтый" to 0xFFFF00, "Зелёный" to 0x00FF00, "Голубой" to 0x00FFFF,
    "Синий" to 0x0000FF, "Розовый" to 0xFF00FF, "Красный" to 0xFF0000, "Чёрный" to 0x000000
)
private val SIZES = listOf(50, 75, 100, 125, 150, 200, 300)
private val TEXT_OPACITY = listOf(25, 50, 75, 100)
private val BG_OPACITY = listOf(0, 25, 50, 75, 100)
private val EDGES = listOf(
    SubtitleStyle.EDGE_NONE to "Нет", SubtitleStyle.EDGE_SHADOW to "Тень", SubtitleStyle.EDGE_OUTLINE to "Контур"
)
private val BOTTOMS = listOf(3 to "Низко", 10 to "Обычно", 20 to "Выше", 30 to "Ещё выше")
private val OFFSETS = listOf(-2000, -1000, -500, -250, 0, 250, 500, 1000, 2000)

private fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1) % size]
private fun colorName(rgb: Int) = COLORS.firstOrNull { it.second == rgb }?.first ?: "Другой"

val SUBTITLE_SETTINGS: List<SubtitleSetting> = listOf(
    SubtitleSetting("Размер текста", { "${it.sizePercent}%" }, { it.copy(sizePercent = SIZES.after(it.sizePercent)) }),
    SubtitleSetting("Цвет текста", { colorName(it.textColor) }, { s -> s.copy(textColor = COLORS.map { it.second }.after(s.textColor)) }),
    SubtitleSetting("Прозрачность текста", { "${it.textOpacity}%" }, { it.copy(textOpacity = TEXT_OPACITY.after(it.textOpacity)) }),
    SubtitleSetting("Цвет фона", { colorName(it.bgColor) }, { s -> s.copy(bgColor = COLORS.map { it.second }.after(s.bgColor)) }),
    SubtitleSetting("Прозрачность фона", { "${it.bgOpacity}%" }, { it.copy(bgOpacity = BG_OPACITY.after(it.bgOpacity)) }),
    SubtitleSetting("Края текста", { s -> EDGES.firstOrNull { it.first == s.edge }?.second ?: "Нет" }, { s -> s.copy(edge = EDGES.map { it.first }.after(s.edge)) }),
    SubtitleSetting("Положение", { s -> BOTTOMS.firstOrNull { it.first == s.bottomPercent }?.second ?: "${s.bottomPercent}%" }, { s -> s.copy(bottomPercent = BOTTOMS.map { it.first }.after(s.bottomPercent)) }),
    SubtitleSetting("Сдвиг по времени", { s -> if (s.offsetMs == 0) "Нет" else "%+.2f с".format(s.offsetMs / 1000.0) }, { it.copy(offsetMs = OFFSETS.after(it.offsetMs)) })
)

/** Общее хранилище: оверлей на экране плеера и меню настроек видят одно и то же значение сразу. */
object SubtitleStyleStore {
    private val _style = MutableStateFlow(SubtitleStyle())
    val style: StateFlow<SubtitleStyle> = _style.asStateFlow()
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("zenith_subtitles", Context.MODE_PRIVATE)

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = prefs(context)
            val d = SubtitleStyle()
            _style.value = SubtitleStyle(
                sizePercent = p.getInt("style_size", d.sizePercent),
                textColor = p.getInt("style_text_color", d.textColor),
                textOpacity = p.getInt("style_text_opacity", d.textOpacity),
                bgColor = p.getInt("style_bg_color", d.bgColor),
                bgOpacity = p.getInt("style_bg_opacity", d.bgOpacity),
                edge = p.getInt("style_edge", d.edge),
                bottomPercent = p.getInt("style_bottom", d.bottomPercent),
                offsetMs = p.getInt("style_offset_ms", d.offsetMs)
            )
        } catch (_: Exception) { /* останутся значения по умолчанию */ }
    }

    fun update(context: Context, change: (SubtitleStyle) -> SubtitleStyle) {
        ensureLoaded(context)
        val s = change(_style.value)
        _style.value = s
        try {
            prefs(context).edit()
                .putInt("style_size", s.sizePercent).putInt("style_text_color", s.textColor)
                .putInt("style_text_opacity", s.textOpacity).putInt("style_bg_color", s.bgColor)
                .putInt("style_bg_opacity", s.bgOpacity).putInt("style_edge", s.edge)
                .putInt("style_bottom", s.bottomPercent).putInt("style_offset_ms", s.offsetMs)
                .apply()
        } catch (_: Exception) { }
    }

    fun reset(context: Context) = update(context) { SubtitleStyle() }
}
