package com.platinum.ott.core.plugin

import android.content.Context
import com.google.gson.Gson
import com.platinum.ott.domain.model.Movie
import com.platinum.ott.domain.model.StreamVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * API-мост для JS-плагинов (Lampa-стиль).
 * Каждый плагин получает ссылку на этот объект через глобальный `Zenith`.
 * Singleton через companion object — OkHttpClient создаётся один раз.
 */
class PluginApi(private val context: Context) {
    companion object {
        internal val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()

    /** Валидация URL — только http/https, без внутренних адресов */
    private fun isValidUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            // Block private/internal IPs
            if (host == "localhost" || host == "127.0.0.1" || host == "::1") return false
            if (host.startsWith("10.") || host.startsWith("192.168.")) return false
            if (host.startsWith("172.")) {
                val second = host.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                if (second in 16..31) return false
            }
            if (host.endsWith(".local") || host.endsWith(".internal")) return false
            return true
        } catch (_: Exception) { return false }
    }

    /** HTTP GET запрос (для парсеров) */
    suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        if (!isValidUrl(url)) return@withContext ""
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        sharedClient.newCall(builder.build()).execute().use { it.body?.string() ?: "" }
    }

    /** HTTP POST запрос */
    suspend fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        if (!isValidUrl(url)) return@withContext ""
        val reqBody = body.toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder().url(url).post(reqBody)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        sharedClient.newCall(builder.build()).execute().use { it.body?.string() ?: "" }
    }

    /** HTTP HEAD запрос (проверка доступности) */
    suspend fun httpHead(url: String): Int = withContext(Dispatchers.IO) {
        if (!isValidUrl(url)) return@withContext 0
        val req = Request.Builder().url(url).head().build()
        sharedClient.newCall(req).execute().use { it.code }
    }

    /** Сохранить значение в хранилище плагина */
    fun storageSet(pluginId: String, key: String, value: String) {
        context.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    }

    /** Получить значение из хранилища плагина */
    fun storageGet(pluginId: String, key: String, default: String = ""): String {
        return context.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE)
            .getString(key, default) ?: default
    }

    /** Удалить ключ из хранилища плагина */
    fun storageRemove(pluginId: String, key: String) {
        context.getSharedPreferences("plugin_$pluginId", Context.MODE_PRIVATE)
            .edit().remove(key).apply()
    }

    /** Уведомить пользователя */
    fun notify(title: String, message: String) {
        com.platinum.ott.worker.NotificationHelper.showNewContent(context, "$title: $message")
    }

    /** Логирование из плагина */
    fun log(pluginId: String, message: String) {
        android.util.Log.d("Plugin/$pluginId", message)
    }

    /** Сформировать объект Movie из JSON (для каталогов/парсеров) */
    fun parseMovie(json: String): Movie? {
        return try { gson.fromJson(json, Movie::class.java) } catch (_: Exception) { null }
    }

    /** Сформировать список StreamVariant из JSON */
    fun parseStreamVariants(json: String): List<StreamVariant> {
        return try {
            val arr = gson.fromJson(json, Array<StreamVariantJson>::class.java)
            arr.map { StreamVariant(it.quality ?: "auto", it.url ?: "") }
        } catch (_: Exception) { emptyList() }
    }

    /** Парсинг произвольного JSON в Map */
    fun parseJsonMap(json: String): Map<String, Any?> {
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(json, Map::class.java) as Map<String, Any?>
        } catch (_: Exception) { emptyMap() }
    }

    private data class StreamVariantJson(val quality: String?, val url: String?)
}
