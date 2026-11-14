package com.platinum.ott.sync.local

import com.google.gson.JsonParseException
import com.platinum.ott.data.remote.dto.LocalSyncPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/** Ошибка протокола с понятным пользователю текстом. */
class LocalSyncException(message: String, val code: String = "") : Exception(message)

/** Сеанс связывания на стороне того, кто вводит код. */
class ClientPairSession internal constructor(
    val endpoint: Endpoint,
    val sid: String,
    val peerId: String,
    val peerName: String,
    val peerType: String,
    /** Контрольная строка — показать пользователю и сверить с экраном второго устройства. */
    val sas: String,
    internal val key: ByteArray,
    internal val secret: ByteArray
)

enum class PairStep { WAITING, DONE, REJECTED }

enum class SendStage { OFFERING, WAITING_CONFIRM, SENDING }

/** Сверка по категории: сколько отправили, сколько подтвердил получатель, совпала ли контрольная сумма. */
data class CategoryCheck(val category: String, val sent: Int, val confirmed: Int, val checksumOk: Boolean, val result: CategoryResult) {
    val ok get() = sent == confirmed && checksumOk
}

data class SendResult(val peerName: String, val checks: List<CategoryCheck>, val report: ApplyReport) {
    val allConfirmed get() = checks.all { it.ok }
}

/**
 * Сторона ОТПРАВИТЕЛЯ локального протокола v2. Не зависит от Android: сеть абстрагирована через Transport.
 */
class LocalSyncClient(
    private val identity: LocalIdentity,
    private val store: PairedStore,
    private val transport: Transport,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = LocalProto.gson

    // ---------------------------------------------------------------- связывание

    /** Шаг 1: ввод кода. После успеха нужно показать sas и спросить пользователя. */
    suspend fun pairInit(endpoint: Endpoint, codeInput: String): Result<ClientPairSession> = guarded {
        val code = CodeAlphabet.normalize(codeInput)
            ?: throw LocalSyncException("Код состоит из 8 букв, например KFMQ-TNDT", "bad_code")
        val kp = LocalCrypto.newKeyPair()
        val pubB64 = LocalCrypto.b64(kp.publicEncoded)
        val proof = LocalCrypto.hmac(code.toByteArray(Charsets.UTF_8), "init|${identity.deviceId}|$pubB64".toByteArray(Charsets.UTF_8))
        val req = PairInit(LocalProto.VERSION, identity.deviceId, identity.deviceName, identity.deviceType, pubB64, LocalCrypto.b64(proof))
        val resp = transport.post(endpoint, LocalProto.PATH_PAIR_INIT, gson.toJson(req))
        if (resp.status != 200) throw errorOf(resp)
        val reply = gson.fromJson(resp.body, PairInitReply::class.java) ?: throw LocalSyncException("Пустой ответ устройства")
        if (reply.sessionId.isBlank() || reply.pub.isBlank()) throw LocalSyncException("Некорректный ответ устройства")
        val peerPub = LocalCrypto.unb64(reply.pub)
        val shared = LocalCrypto.agree(kp.private, peerPub)
        val salt = code.toByteArray(Charsets.UTF_8)
        ClientPairSession(
            endpoint, reply.sessionId, reply.deviceId, reply.name.ifBlank { "Устройство" }, reply.type,
            LocalCrypto.sas(shared, kp.publicEncoded, peerPub),
            LocalCrypto.hkdf(shared, salt, "zenith-local-v2|pair|${reply.sessionId}", 32),
            LocalCrypto.hkdf(shared, salt, "zenith-local-v2|secret|${reply.sessionId}", 32)
        )
    }

    /**
     * Шаг 2: отправить своё решение (accept = true/false) или просто спросить состояние (accept = null).
     * Когда оба подтвердили, устройство сохраняется в списке связанных.
     */
    suspend fun pairStep(s: ClientPairSession, accept: Boolean?): Result<PairStep> = guarded {
        val body = gson.toJson(Seal.pair(s.key, s.sid, "req", gson.toJson(PairStateReq(s.sid, accept))))
        val resp = transport.post(s.endpoint, LocalProto.PATH_PAIR_STATE, body)
        if (resp.status != 200) throw errorOf(resp)
        val env = gson.fromJson(resp.body, Envelope::class.java) ?: throw LocalSyncException("Пустой ответ устройства")
        val reply = gson.fromJson(Seal.openPair(s.key, s.sid, "resp", env), PairStateReply::class.java)
        when (reply.state) {
            "done" -> {
                store.put(PairedDevice(s.peerId, s.peerName, s.peerType, LocalCrypto.b64(s.secret), clock()))
                PairStep.DONE
            }
            "rejected" -> PairStep.REJECTED
            else -> PairStep.WAITING
        }
    }

    /** Ждёт, пока второе устройство подтвердит. Возвращает DONE / REJECTED, при превышении времени — ошибку. */
    suspend fun pairAwait(s: ClientPairSession, timeoutMs: Long = 3 * 60_000L, pollMs: Long = 1500): Result<PairStep> {
        val until = clock() + timeoutMs
        while (clock() < until) {
            val r = pairStep(s, null)
            val step = r.getOrElse { return Result.failure(it) }
            if (step != PairStep.WAITING) return Result.success(step)
            delay(pollMs)
        }
        return Result.failure(LocalSyncException("Второе устройство не подтвердило вовремя"))
    }

    // ---------------------------------------------------------------- передача

    /**
     * Отправить данные связанному устройству: манифест → ожидание решения получателя → отправка разрешённого
     * → сверка подтверждения. buildPayload вызывается ТОЛЬКО после решения получателя (с тем, что он разрешил).
     */
    suspend fun send(
        endpoint: Endpoint,
        device: PairedDevice,
        manifest: Manifest,
        buildPayload: suspend (AcceptSet) -> LocalSyncPayload,
        progress: (SendStage) -> Unit = {},
        waitMs: Long = 3 * 60_000L,
        pollMs: Long = 1500
    ): Result<SendResult> = guarded {
        val secret = LocalCrypto.unb64(device.secret)
        val transferId = LocalCrypto.hex(LocalCrypto.randomBytes(12))
        progress(SendStage.OFFERING)
        var reply = call(endpoint, secret, LocalProto.PATH_OFFER, gson.toJson(OfferReq(transferId, manifest)), OfferReply::class.java)

        progress(SendStage.WAITING_CONFIRM)
        val until = clock() + waitMs
        while (reply.state == "pending") {
            if (clock() > until) throw LocalSyncException("Получатель не ответил вовремя")
            delay(pollMs)
            reply = call(endpoint, secret, LocalProto.PATH_OFFER_STATUS, gson.toJson(OfferStatusReq(transferId)), OfferReply::class.java)
        }
        when (reply.state) {
            "accepted" -> Unit
            "declined" -> throw LocalSyncException("«${device.name}» отклонил передачу", "declined")
            "expired" -> throw LocalSyncException("Время ожидания ответа вышло", "expired")
            else -> throw LocalSyncException("Неожиданный ответ получателя: ${reply.state}")
        }
        val accept = reply.accept ?: throw LocalSyncException("Получатель не указал, что принимать")

        progress(SendStage.SENDING)
        val payload = PayloadFilter.apply(buildPayload(accept), accept)
        val sentStats = PayloadStats.of(payload)
        val push = call(endpoint, secret, LocalProto.PATH_PUSH, gson.toJson(PushReq(transferId, payload)), PushReply::class.java)
        if (!push.ok) throw LocalSyncException(push.error ?: "Получатель не смог применить данные")
        val report = push.report ?: throw LocalSyncException("Получатель не прислал подтверждение")

        val checks = LocalProto.CATEGORIES.map { c ->
            val sent = sentStats.getValue(c)
            val r = report.categories[c] ?: CategoryResult()
            CategoryCheck(c, sent.count, r.received, r.idsChecksum == sent.checksum, r)
        }
        store.get(device.id)?.let {
            store.put(it.copy(lastTransferAt = clock(), lastTransferOk = checks.all { c -> c.ok } && report.errors.isEmpty()))
        }
        SendResult(device.name, checks, report)
    }

    // ---------------------------------------------------------------- ввод на другом устройстве

    /** Спрашивает у связанного устройства, ждёт ли оно ввод (и какой). */
    suspend fun inputInfo(endpoint: Endpoint, device: PairedDevice): Result<InputInfoReply> = guarded {
        call(endpoint, LocalCrypto.unb64(device.secret), LocalProto.PATH_INPUT_INFO, gson.toJson(InputInfoReq()), InputInfoReply::class.java)
    }

    /** Отправляет текст на запрос requestId (см. inputInfo). */
    suspend fun sendInput(endpoint: Endpoint, device: PairedDevice, requestId: String, text: String): Result<Unit> = guarded {
        val r = call(endpoint, LocalCrypto.unb64(device.secret), LocalProto.PATH_INPUT, gson.toJson(InputReq(requestId, text)), InputReply::class.java)
        if (!r.ok) throw LocalSyncException(r.error ?: "Устройство не приняло текст")
    }

    // ---------------------------------------------------------------- внутреннее

    private suspend fun <T> call(endpoint: Endpoint, secret: ByteArray, path: String, plain: String, type: Class<T>): T {
        val now = clock()
        val env = Seal.request(secret, identity.deviceId, path, plain, now)
        val resp = transport.post(endpoint, path, gson.toJson(env))
        if (resp.status != 200) throw errorOf(resp)
        val renv = gson.fromJson(resp.body, Envelope::class.java) ?: throw LocalSyncException("Пустой ответ устройства")
        val text = try {
            Seal.openResponse(secret, env, path, renv)
        } catch (e: LocalCryptoException) {
            throw LocalSyncException("Ответ не прошёл проверку. Свяжите устройства заново.", "bad_auth")
        }
        return gson.fromJson(text, type) ?: throw LocalSyncException("Пустой ответ устройства")
    }

    private fun errorOf(r: TransportResponse): LocalSyncException {
        val e = try { gson.fromJson(r.body, ErrorBody::class.java) } catch (_: Exception) { null }
        val msg = e?.error?.takeIf { it.isNotBlank() } ?: "Устройство ответило ошибкой (${r.status})"
        return LocalSyncException(msg, e?.code.orEmpty())
    }

    private inline fun <T> guarded(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: LocalSyncException) {
        Result.failure(e)
    } catch (e: LocalCryptoException) {
        Result.failure(LocalSyncException(e.message ?: "Ошибка шифрования"))
    } catch (e: JsonParseException) {
        Result.failure(LocalSyncException("Некорректный ответ устройства"))
    } catch (e: IOException) {
        Result.failure(LocalSyncException("Нет связи с устройством. Проверьте, что оно включено, в той же сети и на нём открыт раздел «Мои устройства»."))
    } catch (e: Exception) {
        Result.failure(LocalSyncException(e.message ?: e.javaClass.simpleName))
    }
}
