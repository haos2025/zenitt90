package com.platinum.ott.core

import android.content.Context
import android.content.SharedPreferences

// Раньше таймаут запроса был захардкожен внутри RetrofitFactory
// (connectTimeout(15с)/readTimeout(30с)) — экран настроек показывал "15 сек"
// статичной строкой, никак не влиявшей на реальный OkHttpClient.
class NetworkPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zenith_network", Context.MODE_PRIVATE)
    fun getTimeoutSeconds(): Int = prefs.getInt("timeout_seconds", 15)
    fun setTimeoutSeconds(seconds: Int) = prefs.edit().putInt("timeout_seconds", seconds).apply()
}
