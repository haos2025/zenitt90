package com.platinum.ott.sync.local

import com.google.gson.Gson
import com.platinum.ott.data.remote.dto.LocalSyncPayload
import com.platinum.ott.data.remote.dto.LocalSyncSourceDto

/*
 * Локальная синхронизация, протокол v2 (устройства в одной Wi-Fi сети, без сервера).
 *
 * Связывание (один раз):
 *   1. Устройство B нажимает «Показать код» (8 букв, 5 минут, 5 попыток, одноразовый).
 *   2. Устройство A находит B (NSD) или вводит адрес вручную и вводит код. POST /zl/v2/pair/init:
 *      открытые ключи ECDH + HMAC(код, ...) — сам код по сети не идёт.
 *   3. Оба считают общий секрет и КОНТРОЛЬНУЮ СТРОКУ (4 цифры) и показывают её. Пользователь сверяет и
 *      подтверждает на обоих устройствах (POST /zl/v2/pair/state, тела зашифрованы). Подмена в сети даёт разные строки.
 *   4. После двойного подтверждения у обоих сохраняется общий секрет устройства (в зашифрованных настройках).
 *
 * Передача (для связанных устройств, код не нужен). Отправитель S → получатель R:
 *   1. /zl/v2/offer — «манифест»: сколько чего, без самих данных. R показывает диалог.
 *   2. /zl/v2/offer/status — S ждёт решения. R выбирает категории и (отдельно) разрешает ли пароли Xtream.
 *   3. /zl/v2/push — S отправляет только разрешённое; R применяет и возвращает отчёт с числом принятых записей
 *      и контрольной суммой по каждой категории; S сверяет со своими значениями.
 * Тело каждого запроса и ответа шифруется AES-256-GCM, ключ выводится из общего секрета и случайного nonce запроса;
 * время запроса и nonce защищают от повторной отправки.
 */
object LocalProto {
    const val VERSION = 2
    const val DEFAULT_PORT = 37221
    const val SERVICE_TYPE = "_zenith._tcp."
    const val PATH_PAIR_INIT = "/zl/v2/pair/init"
    const val PATH_PAIR_STATE = "/zl/v2/pair/state"
    /** Без секретов: показывает ли устройство сейчас код связывания. Попытки ввода кода не расходует. */
    const val PATH_PAIR_PROBE = "/zl/v2/pair/probe"
    const val PATH_OFFER = "/zl/v2/offer"
    const val PATH_OFFER_STATUS = "/zl/v2/offer/status"
    const val PATH_PUSH = "/zl/v2/push"
    const val PATH_INPUT_INFO = "/zl/v2/input/info"
    const val PATH_INPUT = "/zl/v2/input"
    const val PATH_PREFIX = "/zl/v2/"

    const val CLOCK_SKEW_MS = 10 * 60 * 1000L
    const val PAIR_SESSION_TTL_MS = 5 * 60 * 1000L
    const val OFFER_TTL_MS = 3 * 60 * 1000L
    const val NONCE_TTL_MS = 20 * 60 * 1000L
    const val MAX_BODY_BYTES = 24 * 1024 * 1024
    const val INPUT_TTL_MS = 10 * 60 * 1000L
    const val MAX_INPUT_CHARS = 4000

    // Режимы «ввод с телефона» (заменяют прежние QR-каналы)
    const val INPUT_SUBTITLE = "subtitle"
    const val INPUT_SEARCH = "search"
    const val INPUT_PLUGIN = "plugin"
    const val INPUT_M3U = "m3u"

    const val CAT_FAVORITES = "favorites"
    const val CAT_HISTORY = "history"
    const val CAT_SOURCES = "sources"
    const val CAT_PLUGINS = "plugins"
    const val CAT_SETTINGS = "settings"
    const val CAT_HIDDEN = "hidden"
    val CATEGORIES = listOf(CAT_FAVORITES, CAT_HISTORY, CAT_SOURCES, CAT_PLUGINS, CAT_SETTINGS, CAT_HIDDEN)

    val gson: Gson = Gson()
}

// ---------------------------------------------------------------- сообщения

data class PairInit(
    val v: Int = 0, val deviceId: String = "", val name: String = "", val type: String = "",
    val pub: String = "", val proof: String = ""
)

data class PairInitReply(
    val v: Int = 0, val deviceId: String = "", val name: String = "", val type: String = "",
    val pub: String = "", val sessionId: String = ""
)

/** accept == null — просто опрос состояния. */
data class PairStateReq(val sid: String = "", val accept: Boolean? = null)

/** state: waiting | done | rejected */
data class PairStateReply(val state: String = "waiting", val peerAccepted: Boolean = false)

data class Envelope(val d: String = "", val n: String = "", val t: Long = 0, val c: String = "")

data class ErrorBody(val error: String = "", val code: String = "")

data class Manifest(
    val favorites: Int = 0, val history: Int = 0, val sources: Int = 0, val plugins: Int = 0,
    val hidden: Int = 0, val settings: Boolean = false,
    val sourceNames: List<String> = emptyList(), val pluginNames: List<String> = emptyList(),
    val sourcesWithCredentials: Int = 0
) {
    /** «Лёгкий» набор — только несекретные избранное и история. Такой можно принимать без диалога (по настройке). */
    fun isLightOnly() = sources == 0 && plugins == 0 && hidden == 0 && !settings
    fun isEmpty() = favorites == 0 && history == 0 && sources == 0 && plugins == 0 && hidden == 0 && !settings

    companion object {
        fun of(p: LocalSyncPayload): Manifest = Manifest(
            favorites = p.favorites.size, history = p.watchHistory.size,
            sources = p.sources.size, plugins = p.plugins.size,
            hidden = p.hiddenItems?.size ?: 0, settings = p.settings != null,
            sourceNames = p.sources.map { it.label }.take(20),
            pluginNames = p.plugins.map { it.name }.take(20),
            sourcesWithCredentials = p.sources.count { SourceCredentials.hasCredentials(it) }
        )
    }
}

/** Запрос на ввод текста с другого устройства (ссылка на субтитры, поисковый запрос и т. п.). */
data class InputInfoReq(val v: Int = 0)
data class InputInfoReply(val pending: Boolean = false, val requestId: String = "", val mode: String = "", val prompt: String = "")
data class InputReq(val requestId: String = "", val text: String = "")
data class InputReply(val ok: Boolean = false, val error: String? = null)

/** Что получатель разрешил принять. passwords — ОТДЕЛЬНОЕ разрешение на логины/пароли источников. */
data class AcceptSet(
    val favorites: Boolean = false, val history: Boolean = false, val sources: Boolean = false,
    val plugins: Boolean = false, val settings: Boolean = false, val hidden: Boolean = false,
    val passwords: Boolean = false
) {
    companion object {
        val LIGHT = AcceptSet(favorites = true, history = true)
    }
}

data class OfferReq(val transferId: String = "", val manifest: Manifest = Manifest())
data class OfferStatusReq(val transferId: String = "")

/** state: pending | accepted | declined | expired | done */
data class OfferReply(val state: String = "pending", val accept: AcceptSet? = null)

data class PushReq(val transferId: String = "", val payload: LocalSyncPayload = LocalSyncPayload())

data class CategoryResult(
    val received: Int = 0, val added: Int = 0, val updated: Int = 0, val skipped: Int = 0,
    val errors: Int = 0, val idsChecksum: String = ""
)

data class ApplyReport(
    val categories: Map<String, CategoryResult> = emptyMap(),
    /** Пояснения получателя (например «источник добавлен выключенным: нужны данные входа»). */
    val notes: List<String> = emptyList(),
    /** Ошибки с причиной (какой плагин не установился и почему). */
    val errors: List<String> = emptyList()
)

data class PushReply(val ok: Boolean = false, val report: ApplyReport? = null, val error: String? = null)

// ---------------------------------------------------------------- устройства и интерфейсы

class Endpoint(val host: String, val port: Int = LocalProto.DEFAULT_PORT)

data class PairedDevice(
    val id: String = "",
    val name: String = "",
    val type: String = "phone",
    /** Общий секрет (base64). Хранится только в зашифрованных настройках. */
    val secret: String = "",
    val pairedAt: Long = 0,
    val lastTransferAt: Long = 0,
    val lastTransferOk: Boolean? = null,
    val lastTransferNote: String? = null,
    /** Принимать избранное и историю от этого устройства без диалога. */
    val autoAcceptLight: Boolean = false
)

interface LocalIdentity {
    val deviceId: String
    val deviceName: String
    val deviceType: String
}

interface PairedStore {
    fun all(): List<PairedDevice>
    fun get(id: String): PairedDevice?
    fun put(device: PairedDevice)
    fun remove(id: String)
}

class TransportResponse(val status: Int, val body: String)

interface Transport {
    /** Бросает IOException при сетевой ошибке. HTTP-статусы возвращаются как есть. */
    suspend fun post(endpoint: Endpoint, path: String, body: String): TransportResponse
}

class HttpReply(val status: Int, val body: String)

/** События движка получателя для интерфейса. Могут приходить из любого потока. */
interface ReceiverEvents {
    fun onCodeExpired(reason: String)
    fun onPairingRequest(sessionId: String, peerName: String, peerType: String, sas: String)
    fun onPairingFinished(sessionId: String, ok: Boolean, device: PairedDevice?, reason: String?)
    /** Пришло предложение передачи. Если оно принято автоматически, диалог не нужен (autoAccepted = true). */
    fun onOffer(transferId: String, device: PairedDevice, manifest: Manifest, autoAccepted: Boolean)
    fun onOfferCancelled(transferId: String)
    fun onTransferFinished(transferId: String, device: PairedDevice, report: ApplyReport?, error: String?)
    /** Связанное устройство прислало текст по запросу beginInput(). */
    fun onInput(requestId: String, device: PairedDevice, mode: String, text: String)
}

/** Всё то же плюс применение принятых данных к базе приложения. */
interface ReceiverHost : ReceiverEvents {
    /** Применить принятые данные. Payload уже отфильтрован по accept. */
    suspend fun applyIncoming(device: PairedDevice, accept: AcceptSet, payload: LocalSyncPayload): ApplyReport
}

// ---------------------------------------------------------------- вспомогательное

object SourceCredentials {
    private val CRED_IN_URL = Regex("[?&](username|user|password|pass|pwd|token|key|auth|apikey)=", RegexOption.IGNORE_CASE)

    fun hasCredentials(s: LocalSyncSourceDto): Boolean =
        !s.password.isNullOrBlank() || !s.username.isNullOrBlank() ||
            (s.url != null && CRED_IN_URL.containsMatchIn(s.url))

    /** Копия источника без данных входа: выключена и помечена «нужны данные входа». */
    fun strip(s: LocalSyncSourceDto): LocalSyncSourceDto {
        if (!hasCredentials(s)) return s
        return s.copy(
            username = null, password = null,
            url = s.url?.let { if (CRED_IN_URL.containsMatchIn(it)) it.substringBefore('?') else it },
            enabled = false, needsCredentials = true
        )
    }
}

class CatStat(val count: Int, val checksum: String)

/** Число записей и контрольная сумма идентификаторов по категориям: по ним отправитель сверяет, что дошло всё. */
object PayloadStats {
    private fun stat(ids: List<String>): CatStat {
        val sorted = ids.sorted()
        return CatStat(sorted.size, LocalCrypto.hex(LocalCrypto.sha256(sorted.joinToString("\n").toByteArray(Charsets.UTF_8))))
    }

    fun of(p: LocalSyncPayload): Map<String, CatStat> = linkedMapOf(
        LocalProto.CAT_FAVORITES to stat(p.favorites.map { it.contentId }),
        LocalProto.CAT_HISTORY to stat(p.watchHistory.map { it.contentId }),
        LocalProto.CAT_SOURCES to stat(p.sources.map { it.id }),
        LocalProto.CAT_PLUGINS to stat(p.plugins.map { it.id }),
        LocalProto.CAT_SETTINGS to stat(if (p.settings != null) listOf("settings") else emptyList()),
        LocalProto.CAT_HIDDEN to stat(p.hiddenItems.orEmpty().map { it.kind + "|" + it.key })
    )
}

/** Оставляет только разрешённое. Применяется и отправителем (минимум данных по сети), и получателем (не доверяет отправителю). */
object PayloadFilter {
    fun apply(p: LocalSyncPayload, a: AcceptSet): LocalSyncPayload = LocalSyncPayload(
        favorites = if (a.favorites) p.favorites else emptyList(),
        watchHistory = if (a.history) p.watchHistory else emptyList(),
        settings = if (a.settings) p.settings else null,
        plugins = if (a.plugins) p.plugins else emptyList(),
        sources = if (a.sources) (if (a.passwords) p.sources else p.sources.map { SourceCredentials.strip(it) }) else emptyList(),
        hiddenItems = if (a.hidden) p.hiddenItems else null,
        deviceTimestamp = p.deviceTimestamp
    )
}

/** Запечатывание сообщений: ключ и AAD привязаны к пути, устройству и времени запроса. */
internal object Seal {
    private fun aadReq(path: String, deviceId: String, t: Long) = "$path|$deviceId|$t".toByteArray(Charsets.UTF_8)
    private fun aadResp(path: String, deviceId: String, t: Long) = "$path|resp|$deviceId|$t".toByteArray(Charsets.UTF_8)
    private fun keyFor(secret: ByteArray, nonce: ByteArray, label: String) =
        LocalCrypto.hkdf(secret, nonce, "zenith-local-v2|$label", 32)

    fun request(secret: ByteArray, deviceId: String, path: String, plain: String, nowMs: Long): Envelope {
        val nonce = LocalCrypto.randomBytes(16)
        val ct = LocalCrypto.encrypt(keyFor(secret, nonce, "req"), plain.toByteArray(Charsets.UTF_8), aadReq(path, deviceId, nowMs))
        return Envelope(deviceId, LocalCrypto.b64(nonce), nowMs, LocalCrypto.b64(ct))
    }

    fun openRequest(secret: ByteArray, path: String, e: Envelope): String {
        val nonce = LocalCrypto.unb64(e.n)
        val pt = LocalCrypto.decrypt(keyFor(secret, nonce, "req"), LocalCrypto.unb64(e.c), aadReq(path, e.d, e.t))
        return String(pt, Charsets.UTF_8)
    }

    fun response(secret: ByteArray, req: Envelope, path: String, plain: String): Envelope {
        val ct = LocalCrypto.encrypt(keyFor(secret, LocalCrypto.unb64(req.n), "resp"), plain.toByteArray(Charsets.UTF_8), aadResp(path, req.d, req.t))
        return Envelope(c = LocalCrypto.b64(ct))
    }

    fun openResponse(secret: ByteArray, req: Envelope, path: String, e: Envelope): String {
        val pt = LocalCrypto.decrypt(keyFor(secret, LocalCrypto.unb64(req.n), "resp"), LocalCrypto.unb64(e.c), aadResp(path, req.d, req.t))
        return String(pt, Charsets.UTF_8)
    }

    // Связывание: общий ключ сессии K (из ECDH и кода), направление входит в AAD.
    fun pair(key: ByteArray, sid: String, dir: String, plain: String): Envelope =
        Envelope(d = sid, c = LocalCrypto.b64(LocalCrypto.encrypt(key, plain.toByteArray(Charsets.UTF_8), "pair|$sid|$dir".toByteArray(Charsets.UTF_8))))

    fun openPair(key: ByteArray, sid: String, dir: String, e: Envelope): String =
        String(LocalCrypto.decrypt(key, LocalCrypto.unb64(e.c), "pair|$sid|$dir".toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
}
