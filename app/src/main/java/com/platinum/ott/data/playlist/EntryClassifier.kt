package com.platinum.ott.data.playlist

import org.json.JSONObject

/** Чем является запись M3U: живым каналом или фильмом/сериалом (VOD). */
enum class EntryKind { CHANNEL, VOD }

/**
 * Разведение записей M3U на каналы и фильмы/сериалы (план 07, раздел 4).
 * Чистая функция без Android-зависимостей (кроме org.json в разборе переопределений —
 * см. parseOverrides; сам классификатор от него не зависит), поэтому легко проверяется
 * unit-тестами.
 *
 * Порядок решения для каждой записи:
 * 1. Переопределение группы (groupOverrides источника) — явное действие пользователя.
 * 2. Принудительный режим источника: mode "live" → канал, "vod" → фильм
 *    (старые источники не меняют поведение, пока пользователь сам не выберет «Авто»).
 * 3. Сильные признаки. Если есть и «канальные», и «фильмовые» — это конфликт, идём дальше.
 * 4. Слабые признаки (длительность -1/0, tvg-id) — только если сильных нет.
 * 5. Большинство уже решённых записей той же group-title.
 * 6. Иначе — фильм (ошибочный фильм в каталоге заметен меньше, чем «канал», который не играет).
 */
object EntryClassifier {
    const val MODE_AUTO = "auto"
    const val MODE_LIVE = "live"
    const val MODE_VOD = "vod"

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "m4v")
    private val EPISODE_REGEX = Regex("S\\d{1,2}E\\d{1,3}", RegexOption.IGNORE_CASE)

    /** Значения переопределения группы в groupOverrides. */
    const val OVERRIDE_LIVE = "live"
    const val OVERRIDE_VOD = "vod"

    /**
     * @param entries записи плейлиста
     * @param mode "auto" | "live" | "vod" (PlaylistSourceEntity.contentKind)
     * @param groupOverrides group-title → "live"/"vod"
     * @return список той же длины и порядка
     */
    fun classifyAll(
        entries: List<ParsedEntry>,
        mode: String,
        groupOverrides: Map<String, String> = emptyMap()
    ): List<EntryKind> {
        val result = arrayOfNulls<EntryKind>(entries.size)

        // Проходы 1–4: всё, что решается по самой записи.
        entries.forEachIndexed { i, entry ->
            result[i] = decideByEntry(entry, mode, groupOverrides)
        }

        // Проход 5: большинство по группе среди уже решённых записей.
        if (mode == MODE_AUTO) {
            val channelsByGroup = HashMap<String, Int>()
            val vodByGroup = HashMap<String, Int>()
            entries.forEachIndexed { i, entry ->
                val key = groupKey(entry)
                when (result[i]) {
                    EntryKind.CHANNEL -> channelsByGroup[key] = (channelsByGroup[key] ?: 0) + 1
                    EntryKind.VOD -> vodByGroup[key] = (vodByGroup[key] ?: 0) + 1
                    null -> {}
                }
            }
            entries.forEachIndexed { i, entry ->
                if (result[i] == null) {
                    val key = groupKey(entry)
                    val c = channelsByGroup[key] ?: 0
                    val v = vodByGroup[key] ?: 0
                    result[i] = when {
                        c > v -> EntryKind.CHANNEL
                        v > c -> EntryKind.VOD
                        else -> EntryKind.VOD // проход 6: безопасное значение
                    }
                }
            }
        }
        return result.map { it ?: EntryKind.VOD }
    }

    private fun groupKey(entry: ParsedEntry): String = entry.rawGroup?.trim()?.lowercase().orEmpty()

    /** null = по самой записи решить нельзя (отдаём проходу «большинство по группе»). */
    private fun decideByEntry(entry: ParsedEntry, mode: String, groupOverrides: Map<String, String>): EntryKind? {
        // 1. Переопределение группы.
        entry.rawGroup?.let { group ->
            val o = groupOverrides[group] ?: groupOverrides.entries.firstOrNull { it.key.equals(group, ignoreCase = true) }?.value
            when (o) {
                OVERRIDE_LIVE -> return EntryKind.CHANNEL
                OVERRIDE_VOD -> return EntryKind.VOD
            }
        }
        // 2. Принудительный режим источника.
        when (mode) {
            MODE_LIVE -> return EntryKind.CHANNEL
            MODE_VOD -> return EntryKind.VOD
        }
        // 3. Сильные признаки.
        val url = entry.entity.streamUrl.lowercase()
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('/', "").substringAfterLast('.', "")
        val duration = entry.durationSec

        var strongVod = false
        var strongChannel = false
        if (ext in VIDEO_EXTENSIONS) strongVod = true
        if ("/movie/" in url || "/series/" in url || "/vod/" in url) strongVod = true
        if (EPISODE_REGEX.containsMatchIn(entry.entity.title)) strongVod = true
        if (duration != null && duration > 0) strongVod = true

        if ("/live/" in url) strongChannel = true
        if (entry.entity.channelNumber != null) strongChannel = true
        if (entry.entity.catchupDays != null || entry.entity.catchupTemplate != null) strongChannel = true

        if (strongVod && !strongChannel) return EntryKind.VOD
        if (strongChannel && !strongVod) return EntryKind.CHANNEL
        if (strongVod && strongChannel) return null // конфликт — решит группа

        // 4. Слабые признаки.
        if (duration != null && duration <= 0) return EntryKind.CHANNEL
        if (!entry.entity.tvgId.isNullOrBlank()) return EntryKind.CHANNEL
        return null
    }

    // ---------- Хранение переопределений (JSON-словарь в PlaylistSourceEntity.groupOverrides) ----------

    fun parseOverrides(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val out = LinkedHashMap<String, String>()
            obj.keys().forEach { k -> out[k] = obj.optString(k) }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun overridesToJson(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
