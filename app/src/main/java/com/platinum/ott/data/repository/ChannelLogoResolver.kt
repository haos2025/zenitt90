package com.platinum.ott.data.repository

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStreamReader

/**
 * Автоподбор логотипов для каналов, у которых в плейлисте нет tvg-logo.
 *
 * Источник — открытая база iptv-org (channels.json + logos.json). Названия каналов приводятся к
 * «звуковому ключу» [ChannelNameMatcher.coreKey] (кириллица ↔ латиница, без HD/FHD/«канал»/TV),
 * по нему и ищется логотип: «Первый канал HD» = «Pervyj Kanal». Совпадают по основному названию и
 * по альтернативным (alt_names) — у российских каналов там есть русские написания.
 *
 * Индекс скачивается один раз (~десятки МБ JSON читаются потоково, в памяти остаётся только
 * компактная карта «ключ → ссылка») и кэшируется в файле на [MAX_AGE_MS]. Сеть недоступна —
 * берётся старый кэш; кэша нет — пробуем снова не чаще раза в [RETRY_AFTER_MS].
 */
class ChannelLogoResolver(
    private val appContext: Context,
    private val client: OkHttpClient
) {
    private val cacheFile: File by lazy { File(appContext.cacheDir, "iptv_org_logo_index.tsv") }
    private val mutex = Mutex()
    @Volatile private var memory: Map<String, String>? = null
    @Volatile private var lastFailAt = 0L

    /** Готовый индекс «ключ названия → ссылка на логотип» или null, если получить не удалось. */
    suspend fun index(): Map<String, String>? = withContext(Dispatchers.IO) {
        memory?.let { return@withContext it }
        mutex.withLock {
            memory?.let { return@withLock it }
            val fresh = cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < MAX_AGE_MS
            if (fresh) readCache()?.let { memory = it; return@withLock it }
            val recentlyFailed = System.currentTimeMillis() - lastFailAt < RETRY_AFTER_MS
            if (!recentlyFailed) {
                val built: Map<String, String>? = try {
                    build()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                }
                if (built != null && built.isNotEmpty()) {
                    writeCache(built)
                    memory = built
                    return@withLock built
                }
                lastFailAt = System.currentTimeMillis()
            }
            // Загрузка не удалась — лучше устаревший кэш, чем ничего.
            readCache()?.also { memory = it }
        }
    }

    /** Ссылка на логотип для названия канала или null. */
    fun find(index: Map<String, String>, channelName: String): String? {
        for (k in keysOf(channelName)) index[k]?.let { return it }
        return null
    }

    // ---------------------------------------------------------------- ключи

    private fun keysOf(name: String): List<String> {
        val out = LinkedHashSet<String>()
        val clean = name.trim()
        fun add(s: String) {
            val k = ChannelNameMatcher.coreKey(s)
            if (k.length >= MIN_KEY) out.add(k)
        }
        add(clean)
        // «Россия 1 (+2)», «Матч! [Москва]» — без хвоста в скобках.
        val noBrackets = clean.replace(Regex("[\\(\\[].*?[\\)\\]]"), " ").trim()
        if (noBrackets.isNotEmpty() && noBrackets != clean) add(noBrackets)
        // «РЕН ТВ - Москва», «Кино | HD» — только часть до разделителя.
        val head = noBrackets.split(Regex("\\s[-–—|/:]\\s")).firstOrNull()?.trim().orEmpty()
        if (head.isNotEmpty() && head != noBrackets) add(head)
        return out.toList()
    }

    // ---------------------------------------------------------------- сборка индекса

    private class Logo(val url: String, val score: Int)

    private fun build(): Map<String, String>? {
        // 1) лучший логотип каждого канала
        val best = HashMap<String, Logo>(32_000)
        val okLogos = download(LOGOS_URL) { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                var channel: String? = null; var url: String? = null; var format: String? = null
                var inUse = false; var width = 0; var feed: String? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "channel" -> channel = reader.strOrNull()
                        "feed" -> feed = reader.strOrNull()
                        "url" -> url = reader.strOrNull()
                        "format" -> format = reader.strOrNull()
                        "in_use" -> inUse = reader.boolOrFalse()
                        "width" -> width = reader.intOrZero()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (channel == null || url == null || !url.startsWith("http")) continue
                val fmtScore = when (format?.uppercase()) {
                    "PNG" -> 30; "WEBP" -> 30; "JPEG" -> 20; "SVG" -> 10
                    else -> continue // GIF/AVIF/APNG и неизвестное не берём
                }
                // Логотип основного канала (без привязки к региональному фиду) — предпочтительнее;
                // действующий (in_use) — предпочтительнее; потом более крупный, но не гигантский.
                val score = fmtScore + (if (inUse) 100 else 0) + (if (feed == null) 40 else 0) +
                    (if (width in 120..1200) 5 else 0)
                val prev = best[channel]
                if (prev == null || score > prev.score) best[channel] = Logo(url, score)
            }
            reader.endArray()
        }
        if (!okLogos || best.isEmpty()) return null

        // 2) названия каналов → ключи; при совпадении ключей приоритет у стран СНГ
        val index = HashMap<String, String>(best.size * 2)
        val keyRank = HashMap<String, Int>(best.size * 2)
        val okChannels = download(CHANNELS_URL) { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                var id: String? = null; var name: String? = null; var country: String? = null
                var nsfw = false
                val alts = ArrayList<String>(2)
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "id" -> id = reader.strOrNull()
                        "name" -> name = reader.strOrNull()
                        "country" -> country = reader.strOrNull()
                        "is_nsfw" -> nsfw = reader.boolOrFalse()
                        "alt_names" -> {
                            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) reader.strOrNull()?.let { alts.add(it) }
                                reader.endArray()
                            } else reader.skipValue()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                val logo = best[id ?: continue] ?: continue
                if (nsfw) continue
                val rank = country?.let { COUNTRY_RANK[it] } ?: 0
                for (n in listOfNotNull(name) + alts) {
                    val k = ChannelNameMatcher.coreKey(n)
                    if (k.length < MIN_KEY) continue
                    val prev = keyRank[k]
                    if (prev == null || rank > prev) { keyRank[k] = rank; index[k] = logo.url }
                }
            }
            reader.endArray()
        }
        if (!okChannels) return null
        return index
    }

    private fun download(url: String, parse: (JsonReader) -> Unit): Boolean {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newBuilder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(3, java.util.concurrent.TimeUnit.MINUTES)
            .build().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body ?: return false
                JsonReader(InputStreamReader(body.byteStream(), Charsets.UTF_8).buffered(64 * 1024)).use { parse(it) }
            }
        return true
    }

    private fun JsonReader.strOrNull(): String? =
        if (peek() == JsonToken.NULL) { nextNull(); null } else if (peek() == JsonToken.STRING) nextString() else { skipValue(); null }

    private fun JsonReader.boolOrFalse(): Boolean =
        if (peek() == JsonToken.BOOLEAN) nextBoolean() else { skipValue(); false }

    private fun JsonReader.intOrZero(): Int =
        if (peek() == JsonToken.NUMBER) nextInt() else { skipValue(); 0 }

    // ---------------------------------------------------------------- кэш в файле (key \t url)

    private fun writeCache(map: Map<String, String>) {
        try {
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.bufferedWriter(Charsets.UTF_8).use { w ->
                for ((k, v) in map) { w.write(k); w.write("\t"); w.write(v); w.write("\n") }
            }
            if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); tmp.renameTo(cacheFile) }
        } catch (_: Exception) { /* кэш — только оптимизация */ }
    }

    private fun readCache(): Map<String, String>? {
        if (!cacheFile.exists()) return null
        return try {
            val m = HashMap<String, String>(60_000)
            cacheFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val t = line.indexOf('\t')
                    if (t > 0) m[line.substring(0, t)] = line.substring(t + 1)
                }
            }
            m.ifEmpty { null }
        } catch (_: Exception) { null }
    }

    private companion object {
        const val LOGOS_URL = "https://iptv-org.github.io/api/logos.json"
        const val CHANNELS_URL = "https://iptv-org.github.io/api/channels.json"
        const val MAX_AGE_MS = 14L * 24 * 3_600_000
        const val RETRY_AFTER_MS = 6L * 3_600_000
        const val MIN_KEY = 3
        // Если одно и то же название есть у каналов разных стран — берём «ближайшую» страну.
        val COUNTRY_RANK: Map<String, Int> = mapOf(
            "RU" to 9, "KZ" to 8, "BY" to 7, "UA" to 6, "UZ" to 5, "KG" to 4, "AM" to 3, "AZ" to 3, "GE" to 3, "MD" to 3
        )
    }
}
