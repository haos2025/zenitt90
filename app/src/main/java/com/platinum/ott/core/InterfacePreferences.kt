package com.platinum.ott.core

import android.content.Context
import com.platinum.ott.ui.theme.AccentColor
import com.platinum.ott.ui.theme.AppearanceSettings
import com.platinum.ott.ui.theme.CardSection
import com.platinum.ott.ui.theme.CardStyle
import com.platinum.ott.ui.theme.TvLayout
import com.platinum.ott.ui.theme.DEFAULT_SIZE_STEP
import com.platinum.ott.ui.theme.ImageScaleMode
import com.platinum.ott.ui.theme.SIZE_STEP_MULTIPLIERS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Раньше ZenithTheme брал только isSystemInDarkTheme() по умолчанию —
 * пользовательского выбора темы не существовало вообще, "Тема: Тёмная" в
 * настройках было захардкоженной строкой, ни к чему не привязанной.
 * Дефолт true (тёмная) — соответствует тому, как приложение выглядело
 * всегда, вне зависимости от системной темы, до этого изменения.
 */
class InterfacePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("interface_prefs", Context.MODE_PRIVATE)
    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", true)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()

    // --- Внешний вид (план 11) ---
    // Синглтон: MainActivity и SettingsViewModel видят один и тот же поток, тема меняется сразу.
    private val _appearance = MutableStateFlow(readAppearance())
    val appearance: StateFlow<AppearanceSettings> = _appearance

    private fun readAppearance() = AppearanceSettings(
        sizeStep = prefs.getInt("appearance_size_step", DEFAULT_SIZE_STEP).coerceIn(0, SIZE_STEP_MULTIPLIERS.lastIndex),
        accent = AccentColor.fromId(prefs.getString("appearance_accent", null)),
        imageScale = ImageScaleMode.fromId(prefs.getString("appearance_image_scale", null)),
        cardStyles = CardSection.entries.mapNotNull { sec ->
            CardStyle.fromId(prefs.getString("appearance_card_${sec.id}", null))?.let { sec to it }
        }.toMap(),
        tvLayout = TvLayout.fromId(prefs.getString("appearance_tv_layout", null))
    )

    fun setSizeStep(step: Int) { prefs.edit().putInt("appearance_size_step", step).apply(); _appearance.value = readAppearance() }
    fun setAccent(accent: AccentColor) { prefs.edit().putString("appearance_accent", accent.id).apply(); _appearance.value = readAppearance() }
    fun setImageScale(mode: ImageScaleMode) { prefs.edit().putString("appearance_image_scale", mode.id).apply(); _appearance.value = readAppearance() }

    fun setCardStyle(section: CardSection, style: CardStyle) { prefs.edit().putString("appearance_card_${section.id}", style.id).apply(); _appearance.value = readAppearance() }
    fun setTvLayout(layout: TvLayout) { prefs.edit().putString("appearance_tv_layout", layout.id).apply(); _appearance.value = readAppearance() }

    /** Сбрасывает только «Внешний вид»; настройки плеера и прочее не трогает. */
    fun resetAppearance() {
        prefs.edit().apply {
            remove("appearance_size_step"); remove("appearance_accent"); remove("appearance_image_scale"); remove("appearance_tv_layout")
            CardSection.entries.forEach { remove("appearance_card_${it.id}") }
        }.apply()
        _appearance.value = readAppearance()
    }

    // --- Оверлей плеера (см. PlayerOverlaySettings) ---

    /** null — не выбрано; старый булев переключатель (true) мигрирует в 4 с. */
    var controlsHideDelayMs: Int?
        get() = when {
            prefs.contains("player_controls_hide_delay_ms") -> prefs.getInt("player_controls_hide_delay_ms", 0)
            prefs.getBoolean("auto_hide_player_controls", false) -> 4000
            else -> null
        }
        set(value) = prefs.edit().apply {
            if (value == null) remove("player_controls_hide_delay_ms") else putInt("player_controls_hide_delay_ms", value)
        }.apply()

    var showProgressStrip: Boolean
        get() = prefs.getBoolean("player_progress_strip", false)
        set(value) = prefs.edit().putBoolean("player_progress_strip", value).apply()

    var progressStripDp: Int
        get() = prefs.getInt("player_progress_strip_dp", DEFAULT_PROGRESS_STRIP_DP)
        set(value) = prefs.edit().putInt("player_progress_strip_dp", value).apply()

    var keepControlsWhenPaused: Boolean
        get() = prefs.getBoolean("player_keep_controls_paused", true)
        set(value) = prefs.edit().putBoolean("player_keep_controls_paused", value).apply()

    var showBuffered: Boolean
        get() = prefs.getBoolean("player_show_buffered", true)
        set(value) = prefs.edit().putBoolean("player_show_buffered", value).apply()

    var liveArrowMode: LiveArrowMode
        get() = runCatching { LiveArrowMode.valueOf(prefs.getString("player_live_arrow_mode", null) ?: "") }
            .getOrDefault(LiveArrowMode.CLASSIC)
        set(value) = prefs.edit().putString("player_live_arrow_mode", value.name).apply()

    var backClosesAll: Boolean
        get() = prefs.getBoolean("player_back_closes_all", true)
        set(value) = prefs.edit().putBoolean("player_back_closes_all", value).apply()

    fun playerOverlaySettings() = PlayerOverlaySettings(
        controlsHideDelayMs, showProgressStrip, progressStripDp, keepControlsWhenPaused, showBuffered,
        liveArrowMode, backClosesAll
    )
}
