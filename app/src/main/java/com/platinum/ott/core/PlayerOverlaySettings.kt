package com.platinum.ott.core

/**
 * Настройки оверлея плеера (общие для TV и телефона).
 *
 * hideDelayMs: null — пользователь ещё не выбирал, экран подставляет свой
 * дефолт (TV — 0, "не скрывать", как всегда было; телефон — 3000, как было
 * захардкожено). 0 — автоскрытие выключено.
 */
data class PlayerOverlaySettings(
    val hideDelayMs: Int? = null,
    val showProgressStrip: Boolean = true,
    val progressStripDp: Int = DEFAULT_PROGRESS_STRIP_DP,
    val keepControlsWhenPaused: Boolean = true,
    val showBuffered: Boolean = true
)

const val DEFAULT_PROGRESS_STRIP_DP = 3

/** Варианты задержки автоскрытия, мс. 0 = выключено. */
val CONTROLS_HIDE_DELAY_OPTIONS_MS = listOf(0, 1000, 2000, 3000, 4000, 5000, 7000, 10000)

/** Варианты толщины полоски прогресса, dp. */
val PROGRESS_STRIP_DP_OPTIONS = listOf(2, 3, 4, 6)

fun formatControlsHideDelay(ms: Int): String = if (ms <= 0) "Выкл" else "${ms / 1000} с"

/** Следующее значение по кругу (для TV-строки "нажми — следующий вариант"). */
fun <T> List<T>.nextAfter(current: T): T {
    val i = indexOf(current)
    return this[(if (i < 0) 0 else i + 1) % size]
}
