package com.platinum.ott.core.plugin

/**
 * Раньше эта проверка была буквально продублирована в PluginApi.kt и
 * PluginRepository.kt — один в один, включая комментарии. Проблема не в
 * дублировании самом по себе, а в том, что это SSRF-защита (блокирует
 * запросы плагинов на localhost/приватные сети): если её расширят или
 * поправят в одном месте, второе тихо останется со старым, более слабым
 * списком блокировок — расхождение, которое не всплывёт ни в компиляции,
 * ни в тестах, только на реальной атаке.
 */
object PluginUrlValidator {
    fun isValid(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        try {
            val uri = java.net.URI(url)
            val host = uri.host?.lowercase() ?: return false
            if (host == "localhost" || host == "127.0.0.1" || host == "::1") return false
            if (host.startsWith("10.") || host.startsWith("192.168.")) return false
            if (host.startsWith("172.")) {
                val second = host.split(".").getOrNull(1)?.toIntOrNull() ?: 0
                if (second in 16..31) return false
            }
            if (host.endsWith(".local") || host.endsWith(".internal")) return false
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
