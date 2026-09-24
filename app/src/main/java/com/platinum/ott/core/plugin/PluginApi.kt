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
        // ФИКС (аудит): followRedirects/followSslRedirects были включены —
        // PluginUrlValidator проверяет только ПЕРВЫЙ url; сервер, прошедший
        // проверку, мог ответить 3xx на http://127.0.0.1/... или адрес из
        // локальной сети, и OkHttp сам, без единой проверки, шёл по этому
        // редиректу. Теперь редиректы отключены на уровне клиента и
        // обрабатываются вручную в executeValidatingRedirects(), с повторной
        // проверкой PluginUrlValidator на КАЖДЫЙ переход.
        internal val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_PLUGIN_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }

    private val gson = Gson()

    /** Валидация URL — вынесена в PluginUrlValidator (раньше была продублирована здесь и в PluginRepository.kt) */
    private fun isValidUrl(url: String): Boolean = PluginUrlValidator.isValid(url)

    /**
     * Выполняет запрос и вручную идёт по редиректам (максимум
     * MAX_PLUGIN_REDIRECTS), заново проверяя PluginUrlValidator.isValid()
     * на КАЖДЫЙ Location — иначе именно эта проверка на первом хопе не
     * защищает от того, куда сервер решит перенаправить запрос дальше.
     * 301/302/303 на не-GET понижают метод до GET (стандартное поведение
     * браузеров/OkHttp по умолчанию); 307/308 сохраняют метод и тело.
     */
    internal fun executeValidatingRedirects(initial: Request): okhttp3.Response {
        var request = initial
        repeat(MAX_PLUGIN_REDIRECTS) {
            val response = sharedClient.newCall(request).execute()
            if (response.code !in REDIRECT_CODES) return response
            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) return response
            val nextUrl = response.request.url.resolve(location)?.toString() ?: return response
            if (!isValidUrl(nextUrl)) {
                // Не идём по невалидному редиректу — возвращаем то, что есть,
                // вызывающая сторона (httpGet/httpPost/httpHead) увидит
                // пустой/неуспешный результат, не адрес из приватной сети.
                return response
            }
            request = if (response.code == 307 || response.code == 308) {
                request.newBuilder().url(nextUrl).build()
            } else {
                request.newBuilder().url(nextUrl).get().build()
            }
        }
        return sharedClient.newCall(request).execute()
    }

    /** HTTP GET запрос (для парсеров) */
    suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        if (!isValidUrl(url)) return@withContext ""
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        executeValidatingRedirects(builder.build()).use { it.body?.string() ?: "" }
    }

    /** HTTP POST запрос */
    suspend fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        if (!isValidUrl(url)) return@withContext ""
        val reqBody = body.toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder().url(url).post(reqBody)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        executeValidatingRedirects(builder.build()).use { it.body?.string() ?: "" }
    }

    /** HTTP HEAD запрос (проверка доступности) */
    suspend fun httpHead(url: String): Int = withContext(Dispatchers.IO) {
        if (!isValidUrl(url)) return@withContext 0
        val req = Request.Builder().url(url).head().build()
        executeValidatingRedirects(req).use { it.code }
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
