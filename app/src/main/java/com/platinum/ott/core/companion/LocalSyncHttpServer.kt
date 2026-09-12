package com.platinum.ott.core.companion

import com.google.gson.Gson
import com.platinum.ott.data.remote.dto.LocalSyncPayload
import com.platinum.ott.data.remote.dto.LocalSyncRequestDto
import com.platinum.ott.data.remote.dto.LocalSyncResponseDto
import fi.iki.elonen.NanoHTTPD

/**
 * PROMPT_LOCAL_SYNC_V1.md, раздел "Формат передачи" — решение принято в
 * пользу ОТДЕЛЬНОГО сервера, не расширения CompanionHttpServer.kt. Причина
 * ровно та, что и сам промт называет более безопасной: CompanionHttpServer
 * жёстко рассчитан на один путь и одно строковое значение как есть
 * (files["postData"] без парсинга, без проверки кода) — это стабильно
 * работает для QR-каналов (субтитры/поиск/URL-плагина/URL-плейлиста) и
 * трогать его ради JSON+кода не нужно. Дублирование NanoHTTPD-обвязки в
 * двух маленьких классах — сознательно принятая цена этой изоляции.
 *
 * Один HTTP-запрос делает push И pull разом: тело содержит код и payload
 * телефона, ответ — уже смёрженный (после applyPayload) снапшот TV. Так
 * кнопка "Синхронизировать" на телефоне закрывает обмен в одну сторону и
 * обратно без второго round-trip и без отдельного протокола поверх этого же
 * порта.
 */
class LocalSyncHttpServer(
    port: Int = 0,
    // TV сам решает, действителен ли присланный код (сверяет с активным
    // LocalSyncCode и его TTL) — сервер не хранит код повторно, только
    // делегирует проверку наружу.
    private val isCodeValid: (String) -> Boolean,
    // Вызывается синхронно в потоке NanoHTTPD (см. runBlocking в
    // LocalSyncRepository — тот же приём, что и SessionGraph.initAuth()).
    // Должен применить присланный payload локально и вернуть свежий
    // локальный снапшот (уже после применения).
    private val onPayloadReceived: (LocalSyncPayload) -> LocalSyncPayload
) : NanoHTTPD(port) {

    private val gson = Gson()

    companion object {
        const val ENDPOINT_PATH = "/local_sync"
    }

    fun startServer(): Int {
        // Литеральные 5000мс — то же значение и то же обоснование, что и в
        // CompanionHttpServer.kt (SOCKET_READY_TIMEOUT_MILLIS не существует
        // в NanoHTTPD 2.3.1, использованной в этом проекте).
        start(5000, false)
        return listeningPort
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri != ENDPOINT_PATH) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"]?.trim().orEmpty()
            if (body.isBlank()) return jsonError(Response.Status.BAD_REQUEST, "Пустое тело запроса")

            val request = try {
                gson.fromJson(body, LocalSyncRequestDto::class.java)
            } catch (e: Exception) {
                return jsonError(Response.Status.BAD_REQUEST, "Некорректный JSON")
            }

            if (!isCodeValid(request.code)) {
                return jsonError(Response.Status.UNAUTHORIZED, "Код истёк или неверен")
            }

            val merged = onPayloadReceived(request.payload)
            newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(LocalSyncResponseDto(payload = merged)))
        } catch (e: Exception) {
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Внутренняя ошибка")
        }
    }

    private fun jsonError(status: Response.Status, message: String): Response =
        newFixedLengthResponse(status, "application/json", gson.toJson(LocalSyncResponseDto(error = message)))
}
