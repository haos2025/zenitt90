package com.platinum.ott.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Последние отчёты о синхронизации (не секретные данные — обычные SharedPreferences, JSON). */
class SyncReportStore(context: Context) {
    private val prefs = context.getSharedPreferences("zenith_sync_reports", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<List<SyncReport>>() {}.type

    @Synchronized
    fun add(report: SyncReport) {
        val updated = (listOf(report) + load()).take(MAX_REPORTS)
        prefs.edit().putString(KEY, gson.toJson(updated)).apply()
    }

    @Synchronized
    fun load(): List<SyncReport> = try {
        gson.fromJson<List<SyncReport>>(prefs.getString(KEY, null) ?: "[]", type) ?: emptyList()
    } catch (e: Exception) { emptyList() }

    private companion object {
        const val KEY = "reports"
        const val MAX_REPORTS = 20
    }
}
