package com.platinum.ott.core

/**
 * Передаёт короткую диагностику из Application в логику субтитров без ручного копирования.
 * Сейчас: причина прошлого убийства процесса. Оркестратор добавляет её в meta первого
 * запроса следующей сессии (поле ex), и она видна в логах Render.
 * Формат: <reason>_i<importance>_r<rss МБ>_a<минут назад>, например 3_i400_r180_a95.
 */
object DiagnosticsHub {
    @Volatile var pendingExit: String? = null

    /** Отдаёт отчёт один раз. */
    @Synchronized
    fun takeExit(): String? {
        val v = pendingExit
        pendingExit = null
        return v
    }
}
