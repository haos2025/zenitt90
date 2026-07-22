package com.platinum.ott.core

import android.content.Context
import android.content.SharedPreferences

class QualityPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zenith_quality", Context.MODE_PRIVATE)
    fun getSelectedQuality(): String? = prefs.getString("selected_quality", null)
    fun setSelectedQuality(q: String) = prefs.edit().putString("selected_quality", q).apply()
    // Раньше не было способа вернуться к "без явного выбора" (Авто) —
    // только setSelectedQuality(). PlayerViewModel.loadMovie() уже трактует
    // null как "нет предпочтения" (падает на variants.first()), просто
    // ничего не умело записать обратно null.
    fun clearSelectedQuality() = prefs.edit().remove("selected_quality").apply()
    fun getMaxQualityOnMobile(): String = prefs.getString("max_quality_mobile", "720p") ?: "720p"
    fun setMaxQualityOnMobile(q: String) = prefs.edit().putString("max_quality_mobile", q).apply()
}
