package com.platinum.ott.core.companion

import com.platinum.ott.sync.local.LocalProto
import com.platinum.ott.sync.local.LocalSyncEngine
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * HTTP-сервер приёма для локальной синхронизации v2. Сам ничего не решает: отдаёт тело запроса движку
 * (LocalSyncEngine) и возвращает его ответ. Тела зашифрованы на уровне протокола (см. LocalProto).
 * Работает только пока открыт раздел «Мои устройства».
 */
class LocalSyncHttpServer(port: Int, private val engine: LocalSyncEngine) : NanoHTTPD(port) {

    private class Status(private val code: Int) : Response.IStatus {
        override fun getRequestStatus(): Int = code
        override fun getDescription(): String = "$code"
    }

    fun startServer(): Int {
        // 5000 мс — как и в остальных серверах проекта (SOCKET_READ_TIMEOUT в этой версии NanoHTTPD не публичен).
        start(5000, false)
        return listeningPort
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || !session.uri.startsWith(LocalProto.PATH_PREFIX)) {
            return reply(404, "{\"error\":\"Not found\",\"code\":\"not_found\"}")
        }
        val length = session.headers["content-length"]?.toLongOrNull() ?: -1L
        if (length > LocalProto.MAX_BODY_BYTES) return reply(413, "{\"error\":\"Слишком большой запрос\",\"code\":\"too_large\"}")
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"]?.trim().orEmpty()
            if (body.isEmpty()) return reply(400, "{\"error\":\"Пустой запрос\",\"code\":\"bad_request\"}")
            val r = runBlocking(Dispatchers.IO) { engine.handle(session.uri, body) }
            reply(r.status, r.body)
        } catch (e: Exception) {
            reply(500, "{\"error\":\"Внутренняя ошибка\",\"code\":\"internal\"}")
        }
    }

    private fun reply(status: Int, body: String): Response =
        newFixedLengthResponse(Status(status), "application/json; charset=utf-8", body)
}
