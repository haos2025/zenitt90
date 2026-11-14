package com.platinum.ott.sync.local

import java.security.SecureRandom

/**
 * Короткий код связывания по правилам RFC 8628: алфавит без гласных и похожих символов, 8 знаков,
 * показывается как XXXX-XXXX, регистр и дефис при вводе не важны. Тот же алфавит у серверного режима.
 */
object CodeAlphabet {
    const val ALPHABET = "BCDFGHJKLMNPQRSTVWXZ"
    const val LENGTH = 8
    private val rnd = SecureRandom()

    fun generate(): String = buildString { repeat(LENGTH) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }

    fun format(code: String): String = code.chunked(4).joinToString("-")

    /** Нормализует ввод пользователя; null — если это не может быть кодом (не та длина или чужие символы). */
    fun normalize(input: String): String? {
        val c = input.uppercase().filter { it.isLetter() }
        if (c.length != LENGTH || c.any { it !in ALPHABET }) return null
        return c
    }
}

/**
 * Код, показанный на этом устройстве. Одноразовый, живёт 5 минут; после 5 неверных попыток гаснет.
 */
class ActiveCode(val code: String, val expiresAtMs: Long) {
    var failed = 0
    fun isAlive(nowMs: Long) = nowMs < expiresAtMs && failed < MAX_FAILED
    fun secondsLeft(nowMs: Long) = ((expiresAtMs - nowMs) / 1000L).toInt().coerceAtLeast(0)

    companion object {
        const val TTL_MS = 5 * 60 * 1000L
        const val MAX_FAILED = 5
    }
}
