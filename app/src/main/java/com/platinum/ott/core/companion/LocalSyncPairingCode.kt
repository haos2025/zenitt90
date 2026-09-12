package com.platinum.ott.core.companion

import kotlin.random.Random

/**
 * Код пары для ЛОКАЛЬНОЙ синхронизации (PROMPT_LOCAL_SYNC_V1.md) — не то
 * же самое, что sync/SyncRepository.kt::PairingCode: тот код выдаёт и
 * проверяет бэкенд (контракт API — 6 цифр/10 минут, см. SyncPairingViewModel
 * "код живёт 10 минут на backend"), этот генерируется и живёт целиком на
 * TV, без сети вообще. Промт прямо разрешил не совпадать со значением TTL
 * бэкенда ("переиспользовать ту же идею числа, не обязательно то же
 * значение") — 6 цифр оставлены те же (чтобы не путать пользователя двумя
 * разными форматами кода в одном приложении), TTL короче: оба устройства
 * уже физически рядом друг с другом, обмен занимает секунды, а не как при
 * сопряжении через бэкенд, где второе устройство может быть где угодно.
 */
data class LocalSyncCode(val code: String, val expiresAtMs: Long) {
    fun isValidNow(): Boolean = System.currentTimeMillis() < expiresAtMs
    fun secondsLeft(): Int = ((expiresAtMs - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
}

object LocalSyncPairingCode {
    const val TTL_SECONDS = 300 // 5 минут

    fun generate(): LocalSyncCode {
        val code = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
        return LocalSyncCode(code, System.currentTimeMillis() + TTL_SECONDS * 1000L)
    }
}
