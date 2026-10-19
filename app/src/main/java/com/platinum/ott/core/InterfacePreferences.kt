package com.platinum.ott.core

import android.content.Context

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
            .getOrDefault(LiveArrowMode.LEFT_RIGHT_ZAP)
        set(value) = prefs.edit().putString("player_live_arrow_mode", value.name).apply()

    var backClosesAll: Boolean
        get() = prefs.getBoolean("player_back_closes_all", true)
        set(value) = prefs.edit().putBoolean("player_back_closes_all", value).apply()

    fun playerOverlaySettings() = PlayerOverlaySettings(
        controlsHideDelayMs, showProgressStrip, progressStripDp, keepControlsWhenPaused, showBuffered,
        liveArrowMode, backClosesAll
    )
}
