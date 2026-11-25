package com.platinum.ott.core.platform

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Защита окон, которые открываются ДОЛГИМ нажатием OK на пульте.
 *
 * Пока пользователь держит кнопку, окно уже появилось и забрало фокус; когда кнопку отпускают, событие
 * KeyUp приходит в уже открытое окно и срабатывает как обычное нажатие на первую кнопку («В избранное»,
 * «Скрыть категорию», «Выше»…). Здесь это отпускание (и автоповторы удержания) гасятся. Если кнопку
 * отпустили ещё до появления окна, первое настоящее нажатие (KeyDown без повтора) «взводит» защиту и
 * проходит как обычно.
 */
fun Modifier.swallowOpeningKeyRelease(): Modifier = composed {
    val armed = remember { BooleanArray(1) }
    onPreviewKeyEvent { e ->
        val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        if (!isOk) {
            false
        } else when (e.type) {
            KeyEventType.KeyDown ->
                if (e.nativeKeyEvent.repeatCount == 0) { armed[0] = true; false } else !armed[0]
            KeyEventType.KeyUp ->
                if (!armed[0]) { armed[0] = true; true } else false
            else -> false
        }
    }
}
