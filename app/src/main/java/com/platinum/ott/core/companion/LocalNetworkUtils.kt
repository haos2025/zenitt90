package com.platinum.ott.core.companion

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

// Раньше локальный IP TV-устройства нигде в проекте не вычислялся — телефон-
// компаньон (ROADMAP.md п.6, PROMPT_PHONE_COMPANION.md) должен показать его
// в QR. WifiManager.getConnectionInfo() требует ACCESS_WIFI_STATE (которого
// нет в AndroidManifest.xml) и ничего не даёт, если TV подключён по Ethernet-
// адаптеру, а не Wi-Fi (у части TV-приставок это обычный сценарий). Перебор
// NetworkInterface работает для обоих случаев и не требует нового permission
// — INTERNET/ACCESS_NETWORK_STATE уже есть в манифесте.
object LocalNetworkUtils {
    fun getLocalIpAddress(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { !it.isLoopback && it.isUp }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()
            ?.hostAddress
    } catch (_: Exception) {
        null
    }
}
