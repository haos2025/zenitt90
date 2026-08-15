package com.platinum.ott.core.companion

import fi.iki.elonen.NanoHTTPD

// Лёгкий локальный сервер для приёма данных с телефона (ROADMAP.md п.6,
// PROMPT_PHONE_COMPANION.md) — живёт ровно пока открыт экран, которому
// нужен ввод с телефона (см. PlayerScreen.kt: старт/остановка через
// DisposableEffect, привязано к жизни конкретного экрана, не ко всему
// приложению — поэтому не заведено в SessionGraph как синглтон).
//
// v1 — один эндпоинт под первое применение промта (внешние субтитры).
// Поиск текстом — вторая задача по тому же каналу, отдельным шагом позже
// (см. промт: "выбрать одно... второе — по аналогии отдельным шагом").
//
// NanoHTTPD выбран вместо ktor-server-cio по прямому указанию промта — не
// тащить тяжёлый фреймворк ради пары эндпоинтов (см. app/build.gradle.kts).
class CompanionHttpServer(
    port: Int = 0, // 0 — просим ОС выбрать свободный порт сама, читаем его через listeningPort после старта
    private val onSubtitleUrl: (String) -> Unit
) : NanoHTTPD(port) {

    fun startServer(): Int {
        start(SOCKET_READY_TIMEOUT_MILLIS, false)
        return listeningPort
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri != "/subtitle") {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        return try {
            val files = HashMap<String, String>()
            // NanoHTTPD кладёт сырое тело POST-запроса в files["postData"]
            // для любого не-multipart Content-Type (в т.ч. text/plain).
            // Тело — просто URL строкой: JSON ради одного поля избыточен
            // (см. комментарий в PhoneQrScanScreen.kt — сторона телефона
            // шлёт именно так).
            session.parseBody(files)
            val url = files["postData"]?.trim().orEmpty()
            if (url.isBlank()) {
                newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Empty url")
            } else {
                onSubtitleUrl(url)
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Error")
        }
    }
}
