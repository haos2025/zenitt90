package com.platinum.ott.data.repository

/**
 * Сравнение названий каналов, одинаково работающее для кириллицы и латиницы:
 * «СТС» = «STS», «Первый канал» = «Pervyj Kanal», «Матч ТВ» = «Match TV», «Россия 1 HD» = «Rossiya 1».
 *
 * Оба названия приводятся к «звуковому ключу»: кириллица транслитерируется, похожие по звучанию латинские
 * буквы сводятся к одной (y/j/и/й/ы → i, ts/ц → одна буква, ch/ч, sh/ш/щ и т. д.), повторы схлопываются.
 * Служебные слова (HD, FHD, 4K, канал, channel, TV/ТВ …) для «похожести» отбрасываются, а в обычном поиске —
 * нет (поиск «тв» должен находить «Рен ТВ»). Чистая логика без Android — проверяется на JVM.
 */
object ChannelNameMatcher {

    // Служебные слова; сравниваются уже в «звуковом» виде (см. [noiseKeys]), поэтому русские и английские
    // варианты («канал» и «channel») обрабатываются одинаково.
    private val noiseWords = listOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "h264", "tv", "тв", "канал", "kanal", "channel", "live", "orig", "original"
    )
    private val noiseKeys: Set<String> by lazy { noiseWords.map { phonetic(it) }.toSet() }

    private val cyr: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "iu", 'я' to "ia",
        // украинские/белорусские буквы встречаются в названиях каналов
        'і' to "i", 'ї' to "i", 'є' to "e", 'ґ' to "g", 'ў' to "u"
    )

    /** Транслитерация + сведение похожих по звучанию букв. Пробелов и знаков в результате нет. */
    private fun phonetic(token: String): String {
        val sb = StringBuilder()
        for (ch in token.lowercase()) sb.append(cyr[ch] ?: ch.toString())
        var s = sb.toString()
        // Многобуквенные сочетания — в одиночные «служебные» символы (верхний регистр, чтобы не пересечься с буквами).
        s = s.replace("kh", "h").replace("ph", "f").replace("ck", "k")
            .replace("ts", "C").replace("ch", "Q").replace("sh", "S").replace("zh", "Z")
        s = s.replace('y', 'i').replace('j', 'i').replace('w', 'v').replace('q', 'k')
        s = s.replace("x", "ks")
        // c перед e/i/y читается как «с», иначе как «к»
        s = s.replace(Regex("c(?=[ei])"), "s").replace('c', 'k')
        // повторы: ss → s, ii → i
        return s.replace(Regex("(.)\\1+"), "$1")
    }

    private fun tokens(name: String): List<String> =
        name.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotEmpty() }

    /** Ключ для обычного поиска: все слова, без отбрасывания служебных. */
    fun fullKey(name: String): String = tokens(name).joinToString("") { phonetic(it) }

    /** Ключ для сравнения похожести: без служебных слов (если после этого ничего не осталось — полный ключ). */
    fun coreKey(name: String): String {
        val keys = tokens(name).map { phonetic(it) }
        val core = keys.filter { it !in noiseKeys }
        return (if (core.isEmpty()) keys else core).joinToString("")
    }

    /** Цифры названия («Россия 24» → «24»): разные номера — разные каналы. */
    private fun digits(name: String): String = name.filter { it.isDigit() }

    /** Поиск по подстроке с учётом транслитерации: «sts» находит «СТС», «матч» находит «Match TV». */
    fun matchesQuery(name: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (name.contains(q, ignoreCase = true)) return true
        val qk = fullKey(q)
        return qk.isNotEmpty() && fullKey(name).contains(qk)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Похожесть названий 0..1 (1 — совпадают по звучанию). */
    fun similarity(a: String, b: String): Double {
        val ka = coreKey(a)
        val kb = coreKey(b)
        if (ka.isEmpty() || kb.isEmpty()) return 0.0
        var score = if (ka == kb) 1.0 else {
            val lev = 1.0 - levenshtein(ka, kb).toDouble() / maxOf(ka.length, kb.length)
            val shorter = if (ka.length <= kb.length) ka else kb
            val longer = if (ka.length <= kb.length) kb else ka
            // Одно название — часть другого («СТС» и «СТС Love»): считаем похожими, но не как точное совпадение.
            val contain = if (shorter.length >= 3 && longer.contains(shorter)) 0.8 else 0.0
            maxOf(lev, contain)
        }
        val da = digits(a)
        val db = digits(b)
        if (da != db) score *= if (da.isNotEmpty() && db.isNotEmpty()) 0.5 else 0.85
        return score
    }

    /** Кандидат с оценкой похожести. */
    data class Scored<T>(val item: T, val score: Double)

    /**
     * Кандидаты, похожие на [reference] (по убыванию похожести), не ниже [minScore]. Если задан [query] —
     * сначала отбираются подходящие под поиск, а похожесть считается к тексту запроса.
     */
    fun <T> rank(
        reference: String,
        candidates: List<T>,
        nameOf: (T) -> String,
        query: String = "",
        minScore: Double = 0.55,
        limit: Int = 40
    ): List<Scored<T>> {
        val q = query.trim()
        return if (q.isEmpty()) {
            candidates.map { Scored(it, similarity(reference, nameOf(it))) }
                .filter { it.score >= minScore }
        } else {
            candidates.filter { matchesQuery(nameOf(it), q) }
                .map { Scored(it, maxOf(similarity(q, nameOf(it)), 0.01)) }
        }.sortedWith(compareByDescending<Scored<T>> { it.score }.thenBy { nameOf(it.item).lowercase() }).take(limit)
    }
}
