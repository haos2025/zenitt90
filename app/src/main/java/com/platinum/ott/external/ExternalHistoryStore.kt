package com.platinum.ott.external

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.security.MessageDigest

/**
 * Одна запись внешнего плеера: ссылка, заголовки и подписи. По ней запись истории («exth_…») можно открыть
 * снова — в самой истории (Room) ссылки нет, только id, название и позиция.
 */
data class ExternalStoredItem(
    val id: String,
    val url: String,
    val title: String,
    val label: String = "",
    val headers: Map<String, String> = emptyMap(),
    val subtitleUrl: String? = null,
    val startPositionMs: Long = 0L,
)

/** Список серий, с которым запускали плеер: [cardTitle] — название фильма/сериала, [items] — файлы по порядку. */
data class ExternalStoredPlaylist(
    val key: String,
    val cardTitle: String,
    val items: List<ExternalStoredItem>,
)

/**
 * Память внешнего плеера: какие ссылки и списки серий открывали (для «Продолжить» из истории и для переходов
 * между сериями после перезапуска). Хранится в одном JSON-файле, держим не больше [MAX_PLAYLISTS] последних списков.
 * Токены и адреса TorrServe в базу истории не попадают: там только id, а ссылка лежит здесь, на этом устройстве
 * (и не синхронизируется — id с префиксом [ID_PREFIX] из синхронизации исключены).
 */
class ExternalHistoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "external_history.json")
    private val gson = Gson()
    private val lock = Any()

    private fun load(): LinkedHashMap<String, ExternalStoredPlaylist> = try {
        if (!file.exists()) LinkedHashMap()
        else {
            val type = object : TypeToken<LinkedHashMap<String, ExternalStoredPlaylist>>() {}.type
            gson.fromJson<LinkedHashMap<String, ExternalStoredPlaylist>>(file.readText(), type) ?: LinkedHashMap()
        }
    } catch (_: Exception) {
        LinkedHashMap()
    }

    private fun save(map: LinkedHashMap<String, ExternalStoredPlaylist>) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(gson.toJson(map))
            if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        } catch (_: Exception) { /* история не критична */ }
    }

    /** Запоминает список (самый свежий — последним). */
    fun remember(playlist: ExternalStoredPlaylist) = synchronized(lock) {
        val map = load()
        map.remove(playlist.key)
        map[playlist.key] = playlist
        while (map.size > MAX_PLAYLISTS) map.remove(map.keys.first())
        save(map)
    }

    /** Список, в котором есть запись [itemId]; самый свежий, если таких несколько. */
    fun findByItem(itemId: String): ExternalStoredPlaylist? = synchronized(lock) {
        load().values.toList().asReversed().firstOrNull { pl -> pl.items.any { it.id == itemId } }
    }

    companion object {
        const val ID_PREFIX = "exth_"
        const val SERIES_PREFIX = "exth_s_"
        private const val MAX_PLAYLISTS = 40

        fun isExternalId(id: String?): Boolean = id != null && id.startsWith(ID_PREFIX)

        /**
         * Стабильный id файла: не зависит от адреса сервера и одноразовых токенов, поэтому тот же файл при следующем
         * запуске получает тот же id. Для TorrServe это путь + `link` + `index`; для остальных — хост и путь без query.
         */
        fun itemId(url: String): String {
            val uri = try { android.net.Uri.parse(url) } catch (_: Exception) { null }
            val basis = if (uri == null) url else {
                val path = uri.path.orEmpty()
                val link = uri.getQueryParameter("link")
                val index = uri.getQueryParameter("index")
                if (link != null) "ts|${path}|$link|${index.orEmpty()}"
                else "${uri.host.orEmpty()}|$path"
            }
            return ID_PREFIX + sha(basis).take(16)
        }

        /** Ключ списка: из id файлов, в порядке списка. */
        fun playlistKey(itemIds: List<String>): String = sha(itemIds.joinToString(",")).take(16)

        private fun sha(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
