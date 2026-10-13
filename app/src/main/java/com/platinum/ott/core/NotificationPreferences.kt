package com.platinum.ott.core

import android.content.Context
import android.content.SharedPreferences

// "Новые серии" раньше были захардкоженной строкой — не читалась и не писалась нигде. "Новый контент"
// сюда намеренно не включён: в бэкенде нет поля "добавлено в каталог"
// (MovieDto ничего похожего не отдаёт), реализовать честно нечем — оставлен
// в SettingsScreen как "Скоро", по тому же принципу, что уже применён к
// "Язык".
class NotificationPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zenith_notifications", Context.MODE_PRIVATE)

    fun isNewEpisodesEnabled(): Boolean = prefs.getBoolean("new_episodes_enabled", true)
    fun setNewEpisodesEnabled(enabled: Boolean) = prefs.edit().putBoolean("new_episodes_enabled", enabled).apply()
}
