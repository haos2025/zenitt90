package com.platinum.ott.core.subtitles

import android.content.Context

/**
 * Запоминает между запусками, насколько быстро извлекается звук из конкретного источника
 * (отношение «время извлечения окна / длина окна», см. extractRatio в SubtitleOrchestrator).
 * Благодаря этому медленный источник распознаётся уже в первом окне, а не после «обучения»
 * в каждой сессии. Ключ — sourceTag: берём только публичные хосты и локальную сеть; для
 * loop/nohost (Lampa и т.п. по 127.0.0.1) за одним адресом могут стоять разные источники,
 * профиль для них был бы недостоверным.
 */
class SourceProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("zenith_source_profiles", Context.MODE_PRIVATE)

    private fun usable(tag: String) = tag.contains("_pub_") || tag.endsWith("_lan")

    /** Сохранённая скорость источника, если она свежая (не старше недели), иначе null. */
    fun ratioFor(tag: String): Double? {
        if (!usable(tag)) return null
        return runCatching {
            val parts = prefs.getString(tag, null)?.split('|') ?: return null
            val ratio = parts[0].toDouble()
            val updated = parts[1].toLong()
            if (System.currentTimeMillis() - updated > MAX_AGE_MS) null else ratio
        }.getOrNull()
    }

    fun record(tag: String, ratio: Double) {
        if (!usable(tag) || ratio < 0) return
        runCatching {
            val editor = prefs.edit().putString(tag, "$ratio|${System.currentTimeMillis()}")
            val all = prefs.all
            if (all.size > MAX_ENTRIES) {
                // выкидываем самые старые записи
                all.entries
                    .sortedBy { (it.value as? String)?.substringAfter('|')?.toLongOrNull() ?: 0L }
                    .take(all.size - MAX_ENTRIES)
                    .forEach { editor.remove(it.key) }
            }
            editor.apply()
        }
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
        const val MAX_ENTRIES = 30
    }
}
