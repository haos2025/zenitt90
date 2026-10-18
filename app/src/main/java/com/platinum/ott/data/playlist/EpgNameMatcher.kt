package com.platinum.ott.data.playlist

/**
 * Сопоставление каналов по НАЗВАНИЮ (для каналов без tvg-id или с tvg-id, которого нет в XMLTV).
 * Только точное совпадение после нормализации — сознательно без «похожих» названий:
 * неверная программа хуже, чем её отсутствие.
 *
 * Нормализация: нижний регистр, ё→е, скобки без «+N» вырезаются («(HD)», «[Orig]»),
 * знаки препинания → пробелы, граница буква/цифра разделяется («ТВ3» = «ТВ-3» = «ТВ 3»),
 * слова качества (hd, fhd, uhd, sd, 4k, hevc, orig) отбрасываются, «+2» (сдвиг часового
 * пояса) сохраняется — «Первый +2» это другой канал. Порядок слов не важен.
 */
object EpgNameMatcher {
    private val quality = setOf("hd", "fhd", "uhd", "sd", "4k", "hevc", "orig", "original", "hq", "lq")
    private val filler = setOf("канал", "channel")
    private val bracket = Regex("[\\(\\[][^\\)\\]]*[\\)\\]]")
    private val shift = Regex("\\+\\s?\\d+")
    private val boundary = Regex("(?<=\\p{L})(?=\\d)|(?<=\\d)(?=\\p{L})")

    private fun tokens(raw: String): List<String> {
        var s = raw.lowercase().replace('ё', 'е')
        s = bracket.replace(s) { m -> if (shift.containsMatchIn(m.value)) " " + shift.find(m.value)!!.value.replace(" ", "") + " " else " " }
        s = s.replace(Regex("[^\\p{L}\\p{N}+]+"), " ")
        s = boundary.replace(s, " ")
        return s.split(' ').filter { it.isNotBlank() && it !in quality }
    }

    /** Ключи для поиска: полный и (если осмысленный) без слов «канал»/«channel». */
    fun keys(raw: String): List<String> {
        val t = tokens(raw)
        if (t.isEmpty()) return emptyList()
        val full = t.sorted().joinToString(" ")
        val shortT = t.filter { it !in filler }
        val short = shortT.sorted().joinToString(" ")
        // Короткий ключ из одних цифр/1–2 символов («Канал 3» → «3») слишком неоднозначен.
        val ok = shortT.isNotEmpty() && short != full && short.length >= 3 && short.any { it.isLetter() }
        return if (ok) listOf(full, short) else listOf(full)
    }
}
