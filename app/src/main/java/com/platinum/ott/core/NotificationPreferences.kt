package com.platinum.ott.core

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

// "Новые серии" / "Тихий режим" раньше были захардкоженными строками
// ("Вкл", "23:00-08:00") — не читались и не писались нигде. "Новый контент"
// сюда намеренно не включён: в бэкенде нет поля "добавлено в каталог"
// (MovieDto ничего похожего не отдаёт), реализовать честно нечем — оставлен
// в SettingsScreen как "Скоро", по тому же принципу, что уже применён к
// "Язык".
class NotificationPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zenith_notifications", Context.MODE_PRIVATE)

    fun isNewEpisodesEnabled(): Boolean = prefs.getBoolean("new_episodes_enabled", true)
    fun setNewEpisodesEnabled(enabled: Boolean) = prefs.edit().putBoolean("new_episodes_enabled", enabled).apply()

    fun isQuietHoursEnabled(): Boolean = prefs.getBoolean("quiet_hours_enabled", true)
    fun setQuietHoursEnabled(enabled: Boolean) = prefs.edit().putBoolean("quiet_hours_enabled", enabled).apply()

    fun getQuietStartHour(): Int = prefs.getInt("quiet_start_hour", 23)
    fun getQuietEndHour(): Int = prefs.getInt("quiet_end_hour", 8)
    fun setQuietHours(startHour: Int, endHour: Int) = prefs.edit().putInt("quiet_start_hour", startHour).putInt("quiet_end_hour", endHour).apply()

    // Диапазон может переходить через полночь (23:00-08:00) — обычное
    // сравнение start<=now<=end тут не работает.
    fun isQuietNow(): Boolean {
        if (!isQuietHoursEnabled()) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = getQuietStartHour(); val end = getQuietEndHour()
        return if (start <= end) hour in start until end else hour >= start || hour < end
    }
}
