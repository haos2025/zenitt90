package com.platinum.ott.data.playlist

import com.platinum.ott.data.local.entity.PlaylistMovieEntity

/**
 * Парсер M3U-плейлиста в список PlaylistMovieEntity. Формат — построчный
 * текстовый стандарт: строка "#EXTINF:-1 tvg-logo="..." group-title="...",Название"
 * затем следующей непустой строкой — сама ссылка на поток.
 *
 * id строится как "m3u_" + порядковый индекс — стабилен, пока провайдер не
 * переупорядочит плейлист; если переупорядочит — избранное/история,
 * привязанные к старому id, перестанут находить совпадение. Осознанный
 * компромисс ради простоты первой версии, не хэш от URL.
 */
object M3uPlaylistParser {
    private val YEAR_REGEX = Regex("\\((\\d{4})\\)")
    private val ATTR_REGEX = Regex("(tvg-logo|group-title)=\"([^\"]*)\"")

    fun parse(raw: String): List<PlaylistMovieEntity> {
        val lines = raw.lines()
        val result = mutableListOf<PlaylistMovieEntity>()
        var index = 0
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF")) {
                val attrs = ATTR_REGEX.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                val title = line.substringAfterLast(",", "").trim().ifBlank { "Без названия" }
                val year = YEAR_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                // Следующая непустая, не-# строка — это URL потока
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) j++
                val url = if (j < lines.size) lines[j].trim() else null

                if (!url.isNullOrBlank()) {
                    result.add(
                        PlaylistMovieEntity(
                            id = "m3u_$index",
                            title = title,
                            year = year,
                            poster = attrs["tvg-logo"],
                            genre = attrs["group-title"]?.ifBlank { null } ?: "Мой плейлист",
                            streamUrl = url
                        )
                    )
                    index++
                }
                i = j + 1
            } else {
                i++
            }
        }
        return result
    }
}
