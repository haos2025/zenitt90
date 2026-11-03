package com.platinum.ott.core.subtitles

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Кэш уже распознанных автосубтитров на диске: при повторном открытии (или продолжении просмотра)
 * готовые реплики показываются сразу, а запросы в облако идут только для ещё не покрытых кусков.
 *
 * Ключ — название + год + язык + длительность (с округлением до 10 с): у внешних плееров ссылка
 * подписанная и меняется от сеанса к сеансу, поэтому по ней ключ строить нельзя.
 * Файлы маленькие (десятки КБ); хранится не более [MAX_FILES] последних.
 */
class SubtitleCueCache(private val dir: File) {

    class Entry(val cues: List<SubtitleCue>, val covered: List<LongRange>)

    /** null — ключ не построить (нет названия или длительности), кэш не используется. */
    fun key(title: String, year: Int, durationMs: Long, language: String): String? {
        val name = title.trim().lowercase()
        if (name.isEmpty() || durationMs <= 0) return null
        val raw = "v1|$name|$year|$language|${durationMs / 10_000}"
        return MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun load(key: String): Entry? = runCatching {
        val file = File(dir, "$key.json")
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        val cues = root.getJSONArray("cues").let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SubtitleCue(o.getLong("s"), o.getLong("e"), o.getString("t"))
            }
        }
        val covered = root.getJSONArray("covered").let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONArray(i)
                o.getLong(0) until o.getLong(1)
            }
        }
        file.setLastModified(System.currentTimeMillis())
        Entry(cues, covered)
    }.onFailure { Log.w(TAG, "cue cache load failed", it) }.getOrNull()

    fun save(key: String, entry: Entry) {
        runCatching {
            dir.mkdirs()
            val root = JSONObject()
            root.put("cues", JSONArray().apply {
                entry.cues.forEach { c ->
                    put(JSONObject().put("s", c.startMs).put("e", c.endMs).put("t", c.text))
                }
            })
            root.put("covered", JSONArray().apply {
                entry.covered.forEach { r -> put(JSONArray().put(r.first).put(r.last + 1)) }
            })
            val tmp = File(dir, "$key.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(File(dir, "$key.json"))) tmp.delete()
            trim()
        }.onFailure { Log.w(TAG, "cue cache save failed", it) }
    }

    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    private companion object {
        const val TAG = "ZenithSubs"
        const val MAX_FILES = 40
    }
}
