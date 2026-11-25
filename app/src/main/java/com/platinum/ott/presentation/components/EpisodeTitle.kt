package com.platinum.ott.presentation.components

import com.platinum.ott.domain.model.Movie

private val LEADING_EPISODE_WORD = Regex("""^\s*(?:серия|эпизод|episode|ep\.?|series|е)\s*\d{1,4}\s*[.:)\-–—]*\s*""", RegexOption.IGNORE_CASE)
private val SXXEXX = Regex("""\bS\d{1,2}\s*[·.\-_ ]?\s*E\d{1,4}\b""", RegexOption.IGNORE_CASE)
private val NXNN = Regex("""\b\d{1,2}x\d{1,4}\b""", RegexOption.IGNORE_CASE)
private val EMPTY_BRACKETS = Regex("""\(\s*\)|\[\s*\]""")
private val SEPARATORS = " -–—:·.|/,_"

/**
 * Название серии для списков: только собственное название, без названия сериала и без номера
 * («Игра престолов S01E05 — Волк и лев» → «Волк и лев»). Номер серии уже выводится отдельной
 * колонкой. Если собственного названия нет — «Серия N» (или исходная строка, когда номера тоже нет).
 */
fun episodeDisplayTitle(ep: Movie): String = episodeDisplayTitle(ep.title, ep.seriesTitle, ep.episodeNumber)

fun episodeDisplayTitle(rawTitle: String, seriesTitle: String?, episodeNumber: Int?): String {
    var t = rawTitle.trim()
    val series = seriesTitle?.trim().orEmpty()
    t = t.replace(SXXEXX, " ").replace(NXNN, " ").replace(EMPTY_BRACKETS, " ")
    t = t.trim { it in SEPARATORS }
    // Название сериала убираем только с краёв строки: в середине это может быть часть названия серии.
    if (series.isNotEmpty()) {
        if (t.startsWith(series, ignoreCase = true)) t = t.substring(series.length).trim { it in SEPARATORS }
        if (t.endsWith(series, ignoreCase = true)) t = t.substring(0, t.length - series.length).trim { it in SEPARATORS }
    }
    t = t.replace(LEADING_EPISODE_WORD, "")
    if (episodeNumber != null) {
        // «5. Название» / «5) Название», когда число совпадает с номером серии
        val lead = Regex("""^\s*$episodeNumber\s*[.):\-–—]+\s*""")
        t = t.replace(lead, "")
    }
    t = t.trim { it in SEPARATORS }.replace(Regex("""\s{2,}"""), " ")
    return when {
        t.isNotEmpty() && !t.equals(series, ignoreCase = true) -> t
        episodeNumber != null -> "Серия $episodeNumber"
        else -> rawTitle.trim()
    }
}
