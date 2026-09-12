package com.platinum.ott.core.player

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * ROADMAP.md — "Веб-архив (`ia_`) по-прежнему не воспроизводится —
 * ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, похоже на недоступность
 * archive.org именно с сети тестировщика (метаданные/поиск при этом
 * работают — значит блокировка не тотальная)". Рабочая гипотеза: заблокирован/
 * недоступен не archive.org целиком, а конкретный ФИЗИЧЕСКИЙ узел
 * (ia8xxxxx.us.archive.org), на который запрос попал через редирект
 * archive.org/download/... — такие узлы у archive.org исторически славятся
 * точечной перегрузкой/недоступностью независимо от домена archive.org в
 * целом (метаданные и поиск идут через отдельную инфраструктуру, не через
 * эти же узлы, поэтому они и продолжают работать).
 *
 * archive.org официально хранит каждый файл на 1-2 серверах (поля d1/d2
 * публичного, не требующего авторизации /metadata/{identifier} API) именно
 * на случай, если один из них недоступен. Если воспроизведение уже упавшего
 * URL похоже на archive.org, пробуем второй сервер НАПРЯМУЮ — минуя
 * редиректящий /download/, который мог бы заново отправить на тот же
 * неисправный узел.
 *
 * Полностью клиентская мера — ничего не меняет на бэкенде Zenith и не
 * требует его правки; независима от увеличения таймаутов/перехода на
 * OkHttpDataSource в PlayerViewModel.kt — та мера помогает, если проблема
 * была "слишком короткий таймаут", эта — если проблема "этот конкретный
 * узел действительно недоступен из этой сети".
 */
object ArchiveOrgMirrorResolver {
    // Отдельный, короткоживущий клиент — не тот, что настроен на долгое
    // потоковое чтение видео (см. PlayerViewModel.mediaHttpClient): это
    // всего один маленький JSON-запрос, длинный таймаут тут не нужен и
    // только тянул бы решение "мирный узел недоступен" на лишние секунды.
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Совпадает с обеими формами archive.org-путей к файлу:
    //   /download/{identifier}/{file...}      — через apex-редирект
    //   /{N}/items/{identifier}/{file...}      — уже на конкретном узле
    // (archive.org отдаёт то ту, то другую форму в зависимости от того,
    // прошёл ли запрос через редирект или сразу попал на узел).
    private val pathRegex = Regex("""/(?:download|\d+/items)/([^/]+)/(.+)$""")

    data class AlternateResult(val url: String, val server: String)

    /**
     * excludeHosts — узлы, уже провалившиеся в текущей попытке
     * воспроизведения этого фильма (обычно как минимум хост из failedUrl —
     * вызывающая сторона добавляет его сама ДО вызова, см. PlayerViewModel),
     * чтобы не предложить его же снова, если он совпадает с d1/d2/server.
     * Возвращает null при любой невозможности разобрать/запросить/найти
     * альтернативу — вызывающая сторона просто идёт по обычному пути
     * (финальный повтор того же URL, затем ошибка), а не падает.
     */
    fun resolveAlternate(failedUrl: String, excludeHosts: Set<String>): AlternateResult? {
        val uri = runCatching { URI(failedUrl) }.getOrNull() ?: return null
        val path = uri.path ?: return null
        val match = pathRegex.find(path) ?: return null
        val identifier = match.groupValues[1]
        val filePath = match.groupValues[2]
        if (identifier.isBlank() || filePath.isBlank()) return null

        val body = runCatching {
            client.newCall(Request.Builder().url("https://archive.org/metadata/$identifier").build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull() ?: return null

        val json = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val dir = json.get("dir")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val candidateServers = listOfNotNull(
            json.get("d1")?.takeIf { it.isJsonPrimitive }?.asString,
            json.get("d2")?.takeIf { it.isJsonPrimitive }?.asString,
            json.get("server")?.takeIf { it.isJsonPrimitive }?.asString
        ).distinct().filterNot { server -> server.isBlank() || server.equals(uri.host, ignoreCase = true) || server in excludeHosts }

        val server = candidateServers.firstOrNull() ?: return null
        return AlternateResult(url = "https://$server$dir/$filePath", server = server)
    }
}
