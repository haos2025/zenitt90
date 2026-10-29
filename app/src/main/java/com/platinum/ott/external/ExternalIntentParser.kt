package com.platinum.ott.external

import android.content.Intent
import android.net.Uri
import com.platinum.ott.presentation.screens.player.DirectPlayRequest

/**
 * Разбор интента «открыть в плеере» от других приложений (план 05_EXTERNAL_PLAYER_PLAN).
 * Принимает стандартный ACTION_VIEW и extras в «диалектах» MX Player и VLC.
 * Всё, что пришло снаружи, считается недоверенным: схемы, длины и символы проверяются,
 * неизвестные extras игнорируются.
 */
object ExternalIntentParser {
    private const val MAX_URL_LENGTH = 4096
    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_HEADERS = 20
    private const val MAX_HEADER_VALUE = 2048
    private val HEADER_NAME = Regex("^[A-Za-z0-9-]{1,64}$")

    /** null — интент не подходит (не ссылка, неподдерживаемая схема и т.п.). */
    fun parse(intent: Intent?): DirectPlayRequest? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val url = uri.toString()
        if (url.length > MAX_URL_LENGTH || uri.host.isNullOrBlank()) return null

        val extras = intent.extras
        @Suppress("DEPRECATION")
        fun raw(key: String): Any? = extras?.get(key)

        val title = (raw("title") as? String)
            ?: (raw(Intent.EXTRA_TITLE) as? String)
            ?: (raw("filename") as? String)
        val cleanTitle = title?.replace(Regex("[\\r\\n]+"), " ")?.trim()?.take(MAX_TITLE_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: titleFromUrl(uri)

        // MX Player: position — Int в мс. VLC: position — Long, from_start=true — с начала.
        val fromStart = raw("from_start") as? Boolean ?: false
        val position = if (fromStart) 0L else ((raw("position") as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)

        return DirectPlayRequest(
            url = url,
            title = cleanTitle,
            headers = parseHeaders(raw("headers")),
            startPositionMs = position,
            subtitleUrl = parseSubtitle(raw("subs"), raw("subtitles_location")),
        )
    }

    private fun titleFromUrl(uri: Uri): String {
        val last = uri.lastPathSegment?.let { Uri.decode(it) }.orEmpty()
        val name = last.substringBeforeLast('.', last).replace('_', ' ').replace('.', ' ').trim()
        return name.take(MAX_TITLE_LENGTH).ifBlank { "Видео" }
    }

    // MX Player: "headers" — String[] из пар «имя, значение».
    private fun parseHeaders(value: Any?): Map<String, String> {
        val items = (value as? Array<*>)?.map { it?.toString() } ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        var i = 0
        while (i + 1 < items.size && result.size < MAX_HEADERS) {
            val name = items[i]?.trim()
            val v = items[i + 1]?.trim()
            if (name != null && v != null && HEADER_NAME.matches(name) &&
                v.length <= MAX_HEADER_VALUE && v.none { it == '\r' || it == '\n' || it == '\u0000' }
            ) result[name] = v
            i += 2
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
