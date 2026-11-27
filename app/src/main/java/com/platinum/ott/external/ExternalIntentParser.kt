@file:Suppress("DEPRECATION")

package com.platinum.ott.external

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.platinum.ott.presentation.screens.player.DirectPlayItem
import com.platinum.ott.presentation.screens.player.DirectPlayRequest

/**
 * Разбор интента «открыть в плеере» от других приложений (план 05_EXTERNAL_PLAYER_PLAN).
 * Принимает стандартный ACTION_VIEW и extras в «диалектах» MX Player и VLC, а также список серий:
 *  - MX Player: `video_list` (Uri[]) + `video_list.name` (String[]) — так Lampa отдаёт плейлист обычным внешним плеерам;
 *  - Just+ Player: Bundle `playlist` (docs/PLAYLIST_API.md Just+: `items`, `start_index`, `headers`, `title`…).
 * Всё, что пришло снаружи, считается недоверенным: схемы, длины и символы проверяются,
 * неизвестные extras игнорируются.
 */
object ExternalIntentParser {
    private const val MAX_URL_LENGTH = 4096
    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_HEADERS = 20
    private const val MAX_HEADER_VALUE = 2048
    private const val MAX_PLAYLIST = 500
    private val HEADER_NAME = Regex("^[A-Za-z0-9-]{1,64}$")

    const val DIALECT_VIEW = "view"
    const val DIALECT_MX = "mx"
    const val DIALECT_JUSTPLUS = "justplus"

    /** null — интент не подходит (не ссылка, неподдерживаемая схема и т.п.). */
    fun parse(intent: Intent?): DirectPlayRequest? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val extras = intent.extras
        fun raw(key: String): Any? = extras?.get(key)

        val playlistBundle = raw("playlist") as? Bundle
        if (playlistBundle != null) parseJustPlus(playlistBundle)?.let { return it }

        val uri = intent.data ?: return null
        val url = checkedUrl(uri.toString()) ?: return null

        val title = (raw("title") as? String)
            ?: (raw(Intent.EXTRA_TITLE) as? String)
            ?: (raw("filename") as? String)
        val cleanTitle = cleanTitle(title) ?: titleFromUrl(uri)

        // MX Player: position — Int в мс. VLC: position — Long, from_start=true — с начала.
        // null — вызывающее приложение позицию не передало: тогда берём свою историю.
        val fromStart = raw("from_start") as? Boolean ?: false
        val position: Long? = if (fromStart) 0L else (raw("position") as? Number)?.toLong()?.coerceAtLeast(0L)

        val headers = parseHeaders(raw("headers"))
        val subtitle = parseSubtitle(raw("subs"), raw("subtitles_location"))

        // Список серий MX: video_list (Uri[]) + video_list.name (String[]).
        // Приложения кладут список по-разному: Uri[] (как требует MX), ArrayList<Uri> или ArrayList<String>.
        // Кроме MX-ключей пробуем и другие распространённые: «playlist»/«urls»/«uris» (списком, не Bundle),
        // EXTRA_STREAM и несколько ссылок в ClipData; имена — «video_list.name», «titles», «names».
        fun clipUris(): List<String?> {
            val cd = intent.clipData ?: return emptyList()
            return (0 until cd.itemCount).map { cd.getItemAt(it).uri?.toString() }
        }
        val mxUris = listOf(
            asStringList(raw("video_list")), asStringList(raw("playlist")), asStringList(raw("urls")),
            asStringList(raw("uris")), asStringList(raw(Intent.EXTRA_STREAM)), clipUris(),
        ).firstOrNull { it.size > 1 }.orEmpty()
        val mxNames = listOf(
            asStringList(raw("video_list.name")), asStringList(raw("titles")), asStringList(raw("names")),
        ).firstOrNull { it.isNotEmpty() }.orEmpty()
        var playlist: List<DirectPlayItem> = emptyList()
        var startIndex = 0
        if (mxUris.size > 1 && mxUris.size <= MAX_PLAYLIST) {
            val items = mxUris.mapIndexedNotNull { i, u ->
                val checked = u?.let { checkedUrl(it) } ?: return@mapIndexedNotNull null
                DirectPlayItem(
                    url = checked,
                    title = cleanTitle,
                    label = cleanTitle(mxNames.getOrNull(i)) ?: titleFromUrl(Uri.parse(checked)),
                    headers = headers,
                )
            }
            val idx = items.indexOfFirst { it.url == url }
            if (idx >= 0) { playlist = items; startIndex = idx }
        }

        return DirectPlayRequest(
            url = url,
            title = cleanTitle,
            headers = headers,
            startPositionMs = position,
            subtitleUrl = subtitle,
            playlist = playlist,
            startIndex = startIndex,
            dialect = if (playlist.isNotEmpty() || raw("return_result") != null || raw("video_list") != null) DIALECT_MX else DIALECT_VIEW,
        )
    }

    private fun asStringList(v: Any?): List<String?> = when (v) {
        is Array<*> -> v.map { it?.toString() }
        is Collection<*> -> v.map { it?.toString() }
        else -> emptyList()
    }

    /** Короткая «справка» о том, что прислало приложение: только ключи и размеры, без значений (там бывают токены). */
    fun describe(intent: Intent?): String {
        if (intent == null) return "интент пуст"
        val sb = StringBuilder()
        intent.data?.let { u ->
            sb.append(u.scheme).append("://").append(u.host ?: "").append(if (u.port > 0) ":${u.port}" else "")
                .append(u.path.orEmpty().take(20)).append("; ")
        }
        val keys = intent.extras?.keySet().orEmpty().sorted()
        sb.append("extras: ")
        if (keys.isEmpty()) sb.append("нет")
        keys.joinTo(sb, ", ") { k ->
            when (val v = intent.extras?.get(k)) {
                is Array<*> -> "$k[${v.size}]"
                is Collection<*> -> "$k[${v.size}]"
                is Bundle -> "$k{${v.keySet().size}}"
                else -> k
            }
        }
        return sb.toString().take(380)
    }

    // ---- Just+ Player: Bundle "playlist" ----

    private fun parseJustPlus(pl: Bundle): DirectPlayRequest? {
        val rawItems = pl.get("items")
        val bundles: List<Bundle> = when (rawItems) {
            is Array<*> -> rawItems.filterIsInstance<Bundle>()
            is List<*> -> rawItems.filterIsInstance<Bundle>()
            else -> emptyList()
        }.take(MAX_PLAYLIST)
        if (bundles.isEmpty()) return null

        val cardTitle = cleanTitle(pl.getString("title"))
        val sharedHeaders = parseHeaders(headersValue(pl))
        val items = ArrayList<DirectPlayItem>()
        val indexMap = IntArray(bundles.size) { -1 } // индекс в исходном списке → индекс в items
        bundles.forEachIndexed { i, b ->
            val url = itemUrl(b)?.let { checkedUrl(it) } ?: return@forEachIndexed
            val itemTitle = cleanTitle(b.getString("title"))
            val episodeTitle = cleanTitle(b.getString("episode_title"))
            val name = cardTitle ?: itemTitle ?: episodeTitle ?: titleFromUrl(Uri.parse(url))
            val season = intOrNull(b, "season")?.takeIf { it > 0 }
            val episode = intOrNull(b, "episode")?.takeIf { it > 0 }
            indexMap[i] = items.size
            items += DirectPlayItem(
                url = url,
                title = itemTitle ?: name,
                label = episodeTitle ?: itemTitle ?: name,
                headers = sharedHeaders + parseHeaders(headersValue(b)),
                subtitleUrl = firstSubtitle(b),
                startPositionMs = millis(b, "position"),
                season = season,
                episode = episode,
            )
        }
        if (items.isEmpty()) return null
        val wantedIndex = (pl.get("start_index") as? Number)?.toInt() ?: 0
        val start = indexMap.getOrNull(wantedIndex)?.takeIf { it >= 0 } ?: 0
        val first = items[start]
        return DirectPlayRequest(
            url = first.url,
            title = cardTitle ?: first.title,
            headers = first.headers,
            startPositionMs = first.startPositionMs,
            subtitleUrl = first.subtitleUrl,
            playlist = if (items.size > 1) items else emptyList(),
            startIndex = start,
            dialect = DIALECT_JUSTPLUS,
        )
    }

    private fun itemUrl(b: Bundle): String? {
        b.getString("uri")?.takeIf { it.isNotBlank() }?.let { return it }
        // qualities: первое помеченное selected, иначе самое высокое по числу в названии.
        qualityUrl(listOfBundles(b, "qualities"))?.let { return it }
        val voices = listOfBundles(b, "voices")
        val voice = voices.firstOrNull { it.getBoolean("selected") } ?: voices.firstOrNull()
        voice?.getString("uri")?.takeIf { it.isNotBlank() }?.let { return it }
        return voice?.let { qualityUrl(listOfBundles(it, "qualities")) }
    }

    private fun qualityUrl(list: List<Bundle>): String? {
        val valid = list.filter { !it.getString("uri").isNullOrBlank() }
        if (valid.isEmpty()) return null
        val selected = valid.firstOrNull { it.getBoolean("selected") }
        val best = selected ?: valid.maxByOrNull { Regex("\\d+").find(it.getString("label").orEmpty())?.value?.toIntOrNull() ?: 0 }
        return best?.getString("uri")
    }

    private fun firstSubtitle(b: Bundle): String? {
        val subs = listOfBundles(b, "subtitles").ifEmpty {
            listOfBundles(b, "voices").firstOrNull { it.getBoolean("selected") }?.let { listOfBundles(it, "subtitles") }.orEmpty()
        }
        return subs.sortedByDescending { it.getBoolean("selected") }
            .mapNotNull { it.getString("uri") }
            .firstOrNull { it.length <= MAX_URL_LENGTH && (it.startsWith("http://", true) || it.startsWith("https://", true)) }
    }

    private fun listOfBundles(b: Bundle, key: String): List<Bundle> {
        val v = b.get(key)
        return when (v) {
            is Array<*> -> v.filterIsInstance<Bundle>()
            is List<*> -> v.filterIsInstance<Bundle>()
            else -> emptyList()
        }
    }

    private fun headersValue(b: Bundle): Any? {
        val v = b.get("headers")
        return when (v) {
            is Array<*> -> v
            is List<*> -> v.toTypedArray()
            else -> null
        }
    }

    private fun intOrNull(b: Bundle, key: String): Int? {
        return (b.get(key) as? Number)?.toInt()
    }

    /** `<name>_ms` важнее `<name>_sec`; null — ключа нет. */
    private fun millis(b: Bundle, name: String): Long? {
        val ms = (b.get("${name}_ms") as? Number)?.toLong()
        val sec = (b.get("${name}_sec") as? Number)?.toDouble()
        return (ms ?: sec?.let { (it * 1000).toLong() })?.coerceAtLeast(0L)
    }

    // ---- общее ----

    private fun checkedUrl(raw: String): String? {
        val uri = try { Uri.parse(raw) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (raw.length > MAX_URL_LENGTH || uri.host.isNullOrBlank()) return null
        return raw
    }

    private fun cleanTitle(title: String?): String? =
        title?.replace(Regex("[\\r\\n]+"), " ")?.trim()?.take(MAX_TITLE_LENGTH)?.takeIf { it.isNotBlank() }

    private fun titleFromUrl(uri: Uri): String {
        val last = uri.lastPathSegment?.let { Uri.decode(it) }.orEmpty()
        val name = last.substringBeforeLast('.', last).replace('_', ' ').replace('.', ' ').trim()
        return name.take(MAX_TITLE_LENGTH).ifBlank { "Видео" }
    }

    // MX Player: "headers" — String[] из пар «имя, значение». Just+: пары либо строки «Имя: значение» (можно вперемешку).
    private fun parseHeaders(value: Any?): Map<String, String> {
        val items = (value as? Array<*>)?.map { it?.toString() } ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        fun put(name: String?, v: String?) {
            val n = name?.trim()
            val x = v?.trim()
            if (n != null && x != null && result.size < MAX_HEADERS && HEADER_NAME.matches(n) &&
                x.length <= MAX_HEADER_VALUE && x.none { it == '\r' || it == '\n' || it == '\u0000' }
            ) result[n] = x
        }
        var i = 0
        while (i < items.size) {
            val first = items[i]
            if (first != null && first.contains(':') && !HEADER_NAME.matches(first.trim())) {
                // строка «Имя: значение»
                put(first.substringBefore(':'), first.substringAfter(':'))
                i += 1
            } else {
                put(first, items.getOrNull(i + 1))
                i += 2
            }
        }
        return result
    }

    // MX Player: "subs" — Uri[]; VLC: "subtitles_location" — строка. Берём первый подходящий.
    private fun parseSubtitle(subs: Any?, vlcLocation: Any?): String? {
        val candidates = buildList<String?> {
            (subs as? Array<*>)?.forEach { add(it?.toString()) }
            (vlcLocation as? String)?.let { add(it) }
        }
        return candidates.firstOrNull { c ->
            c != null && c.length <= MAX_URL_LENGTH &&
                (c.startsWith("http://", true) || c.startsWith("https://", true))
        }
    }
}
