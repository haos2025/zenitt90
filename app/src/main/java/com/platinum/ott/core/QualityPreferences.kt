package com.platinum.ott.core

import android.content.Context
import android.content.SharedPreferences

class QualityPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zenith_quality", Context.MODE_PRIVATE)
    fun getSelectedQuality(): String? = prefs.getString("selected_quality", null)
    fun setSelectedQuality(q: String) = prefs.edit().putString("selected_quality", q).apply()
    fun getMaxQualityOnMobile(): String = prefs.getString("max_quality_mobile", "720p") ?: "720p"
    fun setMaxQualityOnMobile(q: String) = prefs.edit().putString("max_quality_mobile", q).apply()
}
