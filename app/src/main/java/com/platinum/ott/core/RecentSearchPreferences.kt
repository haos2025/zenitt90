package com.platinum.ott.core

import android.content.Context
import android.content.SharedPreferences

// До этого пустое поле поиска (SearchUiState.Idle) показывало только
// статичную подсказку — реального использования истории запросов не
// было вообще. По аналогии с SubtitlePreferences/QualityPreferences —
// простое SharedPreferences-хранилище, отдельная Room-таблица ради
// списка из 10 строк не нужна.
//
// Хранится как одна строка: записи разделены символом-разделителем
// (U+001F, "information separator one"), которого не бывает в обычном
// пользовательском тексте — JSONArray/список ради 10 коротких строк был
// бы избыточен. Порядок в строке — от новых к старым, это же порядок
// показа.
class RecentSearchPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zenith_recent_search", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_QUERIES = "queries"
        private const val DELIMITER = "\u001F"
        private const val MAX_ENTRIES = 10
    }

    fun getRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_QUERIES, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    // Повтор существующего запроса — поднимается наверх списка, не
    // дублируется (сравнение без учёта регистра, чтобы "Матрица" и
    // "матрица" не жили как два разных пункта истории).
    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = getRecentSearches().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val capped = current.take(MAX_ENTRIES)
        prefs.edit().putString(KEY_QUERIES, capped.joinToString(DELIMITER)).apply()
    }

    fun removeRecentSearch(query: String) {
        val current = getRecentSearches().toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        prefs.edit().putString(KEY_QUERIES, current.joinToString(DELIMITER)).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_QUERIES).apply()
    }
}
