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
}
