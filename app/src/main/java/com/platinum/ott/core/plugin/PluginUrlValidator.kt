package com.platinum.ott.core.plugin

import java.net.InetAddress
import java.net.URI

/**
 * Раньше эта проверка была буквально продублирована в PluginApi.kt и
 * PluginRepository.kt — один в один, включая комментарии. Проблема не в
 * дублировании самом по себе, а в том, что это SSRF-защита (блокирует
 * запросы плагинов на localhost/приватные сети): если её расширят или
 * поправят в одном месте, второе тихо останется со старым, более слабым
 * списком блокировок — расхождение, которое не всплывёт ни в компиляции,
 * ни в тестах, только на реальной атаке.
 *
 * ДОБАВЛЕНО: раньше проверялся только текст хоста (префиксы "10.",
 * "192.168." и т.д.) — домен, который РЕЗОЛВИТСЯ в приватный IP (DNS
 * rebinding: сначала домен указывает на публичный адрес для проверки,
 * потом переключается на 127.0.0.1/внутреннюю сеть), проходил проверку
 * беспрепятственно, потому что сам текст хоста не выглядел подозрительно.
 * Теперь резолвится реальный IP (может быть несколько адресов на один
 * домен — проверяются ВСЕ) и проверяется уже он, через `InetAddress`
 * (`isLoopbackAddress`/`isSiteLocalAddress`/`isLinkLocalAddress` — надёжнее
 * ручного разбора байтов). Текстовые проверки хоста оставлены как быстрый
 * первый фильтр перед сетевым походом за DNS, не заменены полностью.
 *
 * Вызывается только из мест, уже обёрнутых в withContext(Dispatchers.IO)
 * (PluginApi.kt, PluginRepository.kt) — блокирующий DNS-запрос здесь безопасен.
 */
object PluginUrlValidator {
    fun isValid(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false

            if (!isHostTextSafe(host)) return false

            val addresses = InetAddress.getAllByName(host)
            if (addresses.isEmpty()) return false
            addresses.none { isPrivateOrLocalAddress(it) }
        } catch (_: Exception) {
            // Ошибка резолвинга DNS, некорректный URL и т.д. — безопаснее
            // считать URL недопустимым, чем пропустить непроверенный адрес.
            false
        }
    }

    private fun isHostTextSafe(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return false
        if (host.startsWith("10.") || host.startsWith("192.168.")) return false
        if (host.startsWith("172.")) {
            val second = host.split(".").getOrNull(1)?.toIntOrNull() ?: 0
            if (second in 16..31) return false
        }
        if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) return false
        return true
    }

    private fun isPrivateOrLocalAddress(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress) {
            return true
        }
        val raw = addr.address
        if (raw.size == 4) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            if (b0 == 0) return true // 0.0.0.0/8
            if (b0 == 100 && b1 in 64..127) return true // CGNAT 100.64.0.0/10
        }
        return false
    }
}
