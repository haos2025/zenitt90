package com.platinum.ott.core.companion

import fi.iki.elonen.NanoHTTPD

// Лёгкий локальный сервер для приёма данных с телефона (ROADMAP.md п.6,
// PROMPT_PHONE_COMPANION.md) — живёт ровно пока открыт экран, которому
// нужен ввод с телефона (см. PlayerScreen.kt/SearchScreen.kt: старт/
// остановка через DisposableEffect, привязано к жизни конкретного
// экрана, не ко всему приложению — поэтому не заведено в SessionGraph
// как синглтон).
//
// v2 — раньше endpointPath был захардкожен в "/subtitle" (первое
// применение промта). Второе применение (поиск текстом) заводит второй
// эндпоинт "/search" — вместо копирования всего класса ради одной
// строки, endpointPath стал параметром конструктора: TV поднимает свой
// экземпляр сервера на нужном экране с нужным путём и обработчиком,
// сам класс не знает и не должен знать про субтитры/поиск конкретно.
//
// NanoHTTPD выбран вместо ktor-server-cio по прямому указанию промта — не
// тащить тяжёлый фреймворк ради пары эндпоинтов (см. app/build.gradle.kts).
class CompanionHttpServer(
    private val endpointPath: String,
    port: Int = 0, // 0 — просим ОС выбрать свободный порт сама, читаем его через listeningPort после старта
    private val onValueReceived: (String) -> Unit
) : NanoHTTPD(port) {

    fun startServer(): Int {
        start(SOCKET_READY_TIMEOUT_MILLIS, false)
        return listeningPort
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri != endpointPath) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        return try {
            val files = HashMap<String, String>()
            // NanoHTTPD кладёт сырое тело POST-запроса в files["postData"]
            // для любого не-multipart Content-Type (в т.ч. text/plain).
            // Тело — просто строка (URL субтитров или текст поиска): JSON
            // ради одного поля избыточен (см. комментарий в
            // PhoneQrScanScreen.kt — сторона телефона шлёт именно так).
            session.parseBody(files)
            val value = files["postData"]?.trim().orEmpty()
            if (value.isBlank()) {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Empty value")
            } else {
                onValueReceived(value)
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Error")
        }
    }
}
