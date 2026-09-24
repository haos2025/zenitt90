package com.platinum.ott.core.platform

import androidx.compose.ui.focus.FocusRequester

/**
 * ФИКС (аудит): паттерн `LaunchedEffect(key) { someFocusRequester.requestFocus() }`
 * встречался как минимум в двух местах (DetailScreen.kt — фокус на кнопку
 * "Смотреть", PlaybackMenuOverlay.kt — фокус на активную вкладку) — если
 * recomposition, назначающая `Modifier.focusRequester(...)` нужному узлу,
 * ещё не успела отработать к моменту вызова `requestFocus()` в том же
 * кадре, Compose бросает `IllegalStateException("FocusRequester is not
 * initialized")`. Редко, но реально — оба места чинились независимо друг
 * от друга одним и тем же способом. Один общий безопасный вызов вместо
 * повторения try/catch в каждом новом месте, где понадобится начальный
 * фокус.
 */
fun FocusRequester.safeRequestFocus() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
        // Целевой узел ещё не прикреплён к дереву в этом кадре — фокус
        // просто не переставится сейчас; следующая recomposition с тем же
        // key в LaunchedEffect не наступит, но D-pad всё равно продолжит
        // работать от того фокуса, что уже есть, ничего не виснет.
    }
}
