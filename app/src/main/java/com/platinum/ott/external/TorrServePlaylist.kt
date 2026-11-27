package com.platinum.ott.external

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/**
 * Список серий прямо с TorrServe — на случай, когда приложение-источник (Lampa) передало только ОДНУ ссылку
 * без списка. Ссылка вида `http://host:8090/stream/<имя>?link=<hash>&index=<N>&play` однозначно говорит,
 * какой торрент играет; плейлист всех файлов TorrServe отдаёт сам (`/stream?link=…&m3u`, запасной путь —
 * `/playlist?hash=…`). Нужны только запросы к тому же серверу, с которого идёт видео.
 */
object TorrServePlaylist {
    data class StreamRef(val base: String, val link: String, val index: Int)
    data class Entry(val index: Int?, val url: String, val name: String, val season: Int?, val episode: Int?)

    private val VIDEO_EXT = setOf("mkv", "mp4", "avi", "m4v", "mov", "wmv", "webm", "ts", "m2ts", "mpg", "mpeg", "flv")
    private val SXXEYY = Regex("""[Ss](\d{1,2})[ ._-]?[Ee](\d{1,3})""")
    private val NXNN = Regex("""(?<!\d)(\d{1,2})x(\d{2,3})(?!\d)""")
    private const val MAX_ENTRIES = 500
    private const val MAX_BYTES = 2_000_000

    /** null — это не ссылка TorrServe на файл торрента. */
    fun parseStreamUrl(url: String): StreamRef? {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (u.scheme != "http" && u.scheme != "https") return null
        val path = u.path ?: return null
        if (!path.startsWith("/stream")) return null
        val link = u.getQueryParameter("link")?.takeIf { it.isNotBlank() } ?: return null
        val index = u.getQueryParameter("index")?.toIntOrNull() ?: return null
        val authority = u.encodedAuthority ?: return null
        return StreamRef("${u.scheme}://$authority", link, index)
    }

    /** null — сервер недоступен или ответил не плейлистом; пустой список — в торренте меньше двух видеофайлов не нашлось. */
    fun fetch(ref: StreamRef, headers: Map<String, String>): List<Entry>? {
        val candidates = buildList {
            add("${ref.base}/stream?link=${Uri.encode(ref.link)}&m3u")
            if (ref.link.length == 40 && ref.link.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                add("${ref.base}/playlist?hash=${ref.link}")
            }
        }
        for (u in candidates) {
            val body = download(u, headers) ?: continue
            if (!body.trimStart().startsWith("#EXTM3U")) continue
            return parseM3u(body, ref.base)
        }
        return null
    }

    internal fun parseM3u(body: String, base: String): List<Entry> {
        val out = ArrayList<Entry>()
        var title: String? = null
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                title = line.substringAfter(',', "").trim().ifEmpty { null }
                continue
            }
            if (line.startsWith("#")) continue
            val url = if (line.startsWith("/")) base + line else line
            val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: continue
            if (parsed.scheme != "http" && parsed.scheme != "https") continue
            val fileName = (parsed.lastPathSegment ?: "").let { Uri.decode(it) }
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val name = (title ?: fileName).substringBeforeLast('.', title ?: fileName).trim().ifEmpty { fileName }
            title = null
            if (ext !in VIDEO_EXT) continue
            val se = SXXEYY.find(name) ?: SXXEYY.find(fileName) ?: NXNN.find(name)
            out += Entry(
                index = parsed.getQueryParameter("index")?.toIntOrNull(),
                url = url,
                name = name,
                season = se?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 },
                episode = se?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it > 0 },
            )
            if (out.size >= MAX_ENTRIES) break
        }
        return out
    }

    private fun download(url: String, headers: Map<String, String>): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4_000
        c.readTimeout = 8_000
        headers.forEach { (k, v) -> runCatching { c.setRequestProperty(k, v) } }
        try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.use { it.readNBytesCapped(MAX_BYTES) }
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun java.io.InputStream.readNBytesCapped(limit: Int): String {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (buf.size() < limit) {
            val n = read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
        }
        return buf.toString(Charsets.UTF_8.name())
    }
}
