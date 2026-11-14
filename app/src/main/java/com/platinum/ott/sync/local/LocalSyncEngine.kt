package com.platinum.ott.sync.local

import com.google.gson.JsonParseException
import com.platinum.ott.data.remote.dto.LocalSyncPayload
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/**
 * Сторона ПОЛУЧАТЕЛЯ локального протокола v2 (см. LocalProto): принимает запросы от HTTP-сервера,
 * проверяет, расшифровывает и передаёт решения интерфейсу через ReceiverHost. Не зависит от Android,
 * поэтому проверяется обычными тестами на JVM.
 */
class LocalSyncEngine(
    private val identity: LocalIdentity,
    private val store: PairedStore,
    private val host: ReceiverHost,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val gson = LocalProto.gson
    private val lock = Any()
    private var activeCode: ActiveCode? = null
    private val sessions = HashMap<String, PairSession>()
    private val offers = HashMap<String, IncomingOffer>()
    private val seenNonces = LinkedHashMap<String, Long>()

    private class PairSession(
        val sid: String, val peerId: String, val peerName: String, val peerType: String,
        val key: ByteArray, val secret: ByteArray, val createdAt: Long
    ) {
        var selfDecision: Boolean? = null
        var peerDecision: Boolean? = null
        var reported = false
    }

    private class IncomingOffer(val id: String, val deviceId: String, val manifest: Manifest, val createdAt: Long) {
        var state = "pending"
        var accept: AcceptSet? = null
        var pushed = false
    }

    // ---------------------------------------------------------------- управление из интерфейса

    /** Показать новый код связывания (прежний гаснет). */
    fun showCode(): ActiveCode {
        val code = ActiveCode(CodeAlphabet.generate(), clock() + ActiveCode.TTL_MS)
        synchronized(lock) { activeCode = code }
        return code
    }

    fun hideCode() = synchronized(lock) { activeCode = null }

    fun currentCode(): ActiveCode? = synchronized(lock) { activeCode?.takeIf { it.isAlive(clock()) } }

    /** Решение пользователя этого устройства: контрольные строки совпали / не совпали. */
    fun decidePairing(sessionId: String, accept: Boolean) {
        val s = synchronized(lock) { sessions[sessionId]?.also { if (it.selfDecision == null) it.selfDecision = accept } } ?: return
        finishPairing(s)
    }

    /** Решение пользователя по входящему предложению. accept == null — отклонить. */
    fun decideOffer(transferId: String, accept: AcceptSet?, alwaysLight: Boolean) {
        val deviceId: String
        synchronized(lock) {
            val o = offers[transferId] ?: return
            if (o.state != "pending") return
            val nothing = accept == null || (!accept.favorites && !accept.history && !accept.sources &&
                !accept.plugins && !accept.settings && !accept.hidden)
            if (nothing) {
                o.state = "declined"
            } else {
                o.state = "accepted"
                o.accept = accept
            }
            deviceId = o.deviceId
        }
        if (alwaysLight && accept != null) {
            store.get(deviceId)?.let { store.put(it.copy(autoAcceptLight = true)) }
        }
    }

    /** Остановка приёма: гасит код, сессии и ожидающие предложения. */
    fun shutdown() {
        val cancelled = ArrayList<String>()
        synchronized(lock) {
            activeCode = null
            sessions.clear()
            offers.values.filter { it.state == "pending" }.forEach { it.state = "expired"; cancelled += it.id }
            offers.clear()
        }
        cancelled.forEach { host.onOfferCancelled(it) }
    }

    // ---------------------------------------------------------------- вход

    suspend fun handle(path: String, body: String): HttpReply {
        return try {
            purge()
            when (path) {
                LocalProto.PATH_PAIR_INIT -> pairInit(body)
                LocalProto.PATH_PAIR_STATE -> pairState(body)
                LocalProto.PATH_OFFER, LocalProto.PATH_OFFER_STATUS, LocalProto.PATH_PUSH -> authed(path, body)
                else -> err(404, "not_found", "Неизвестный запрос")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalCryptoException) {
            err(400, "crypto", e.message ?: "Ошибка шифрования")
        } catch (e: JsonParseException) {
            err(400, "bad_json", "Некорректный запрос")
        } catch (e: Exception) {
            err(500, "internal", e.message ?: "Внутренняя ошибка")
        }
    }

    private fun err(status: Int, code: String, message: String) =
        HttpReply(status, gson.toJson(ErrorBody(message, code)))

    private fun purge() {
        val now = clock()
        val dead = ArrayList<PairSession>()
        val cancelled = ArrayList<String>()
        synchronized(lock) {
            sessions.values.filter { now - it.createdAt > LocalProto.PAIR_SESSION_TTL_MS }.forEach { dead += it; sessions.remove(it.sid) }
            offers.values.filter { it.state == "pending" && now - it.createdAt > LocalProto.OFFER_TTL_MS }.forEach { it.state = "expired"; cancelled += it.id }
            offers.values.removeAll { now - it.createdAt > LocalProto.OFFER_TTL_MS * 4 }
            val it = seenNonces.entries.iterator()
            while (it.hasNext()) { if (now - it.next().value > LocalProto.NONCE_TTL_MS) it.remove() else break }
            activeCode?.let { if (!it.isAlive(now)) activeCode = null }
        }
        dead.filter { !it.reported }.forEach { host.onPairingFinished(it.sid, false, null, "Время ожидания вышло") }
        cancelled.forEach { host.onOfferCancelled(it) }
    }

    // ---------------------------------------------------------------- связывание

    private fun pairInit(body: String): HttpReply {
        val req = gson.fromJson(body, PairInit::class.java) ?: return err(400, "bad_json", "Пустой запрос")
        if (req.v != LocalProto.VERSION) return err(400, "version", "Версии приложения на устройствах несовместимы. Обновите приложение.")
        if (req.deviceId.isBlank() || req.deviceId.length > 64 || req.pub.isBlank() || req.proof.isBlank()) return err(400, "bad_request", "Неполный запрос")
        if (req.deviceId == identity.deviceId) return err(400, "self", "Нельзя связать устройство с самим собой")
        val now = clock()
        val code = synchronized(lock) { activeCode?.takeIf { it.isAlive(now) } }
            ?: return err(410, "no_code", "На этом устройстве не показан код или он истёк. Нажмите «Показать код» и повторите.")

        val expected = LocalCrypto.hmac(code.code.toByteArray(Charsets.UTF_8), "init|${req.deviceId}|${req.pub}".toByteArray(Charsets.UTF_8))
        val given = try { LocalCrypto.unb64(req.proof) } catch (_: LocalCryptoException) { ByteArray(0) }
        if (!LocalCrypto.constantTimeEquals(expected, given)) {
            var dead = false
            synchronized(lock) {
                code.failed++
                if (!code.isAlive(now)) { if (activeCode === code) activeCode = null; dead = true }
            }
            if (dead) host.onCodeExpired("Слишком много неверных попыток — код сброшен. Покажите новый.")
            return err(401, "bad_code", "Неверный код")
        }

        val peerPub = LocalCrypto.unb64(req.pub)
        val kp = LocalCrypto.newKeyPair()
        val shared = LocalCrypto.agree(kp.private, peerPub)
        val sid = LocalCrypto.hex(LocalCrypto.randomBytes(12))
        val salt = code.code.toByteArray(Charsets.UTF_8)
        val key = LocalCrypto.hkdf(shared, salt, "zenith-local-v2|pair|$sid", 32)
        val secret = LocalCrypto.hkdf(shared, salt, "zenith-local-v2|secret|$sid", 32)
        val sas = LocalCrypto.sas(shared, peerPub, kp.publicEncoded)
        val session = PairSession(sid, req.deviceId, req.name.take(64).ifBlank { "Устройство" }, req.type.take(16), key, secret, now)
        synchronized(lock) {
            activeCode = null // код одноразовый
            sessions[sid] = session
        }
        host.onPairingRequest(sid, session.peerName, session.peerType, sas)
        return HttpReply(200, gson.toJson(PairInitReply(LocalProto.VERSION, identity.deviceId, identity.deviceName, identity.deviceType, LocalCrypto.b64(kp.publicEncoded), sid)))
    }

    private fun pairState(body: String): HttpReply {
        val env = gson.fromJson(body, Envelope::class.java) ?: return err(400, "bad_json", "Пустой запрос")
        val s = synchronized(lock) { sessions[env.d] } ?: return err(404, "no_session", "Сеанс связывания не найден или истёк")
        val req = try {
            gson.fromJson(Seal.openPair(s.key, s.sid, "req", env), PairStateReq::class.java)
        } catch (_: LocalCryptoException) {
            return err(401, "bad_auth", "Сообщение не прошло проверку")
        }
        synchronized(lock) {
            if (req.accept != null && s.peerDecision == null) s.peerDecision = req.accept
        }
        finishPairing(s)
        val reply = synchronized(lock) {
            val state = when {
                s.selfDecision == false || s.peerDecision == false -> "rejected"
                s.selfDecision == true && s.peerDecision == true -> "done"
                else -> "waiting"
            }
            // peerAccepted для опрашивающего: решил ли ЭТО (отвечающее) устройство
            PairStateReply(state, s.selfDecision == true)
        }
        return HttpReply(200, gson.toJson(Seal.pair(s.key, s.sid, "resp", gson.toJson(reply))))
    }

    private fun finishPairing(s: PairSession) {
        var ok: Boolean? = null
        var device: PairedDevice? = null
        var reason: String? = null
        synchronized(lock) {
            if (s.reported) return
            val rejected = s.selfDecision == false || s.peerDecision == false
            val done = s.selfDecision == true && s.peerDecision == true
            if (rejected) {
                s.reported = true
                ok = false
                reason = if (s.selfDecision == false) "Вы отклонили связывание" else "Другое устройство отклонило связывание"
            } else if (done) {
                s.reported = true
                val dev = PairedDevice(s.peerId, s.peerName, s.peerType, LocalCrypto.b64(s.secret), clock())
                store.put(dev)
                ok = true
                device = dev
            }
        }
        val result = ok ?: return
        host.onPairingFinished(s.sid, result, device, reason)
    }

    // ---------------------------------------------------------------- передача

    private suspend fun authed(path: String, body: String): HttpReply {
        val env = gson.fromJson(body, Envelope::class.java) ?: return err(400, "bad_json", "Пустой запрос")
        val device = store.get(env.d) ?: return err(403, "unknown_device", "Это устройство не связано с получателем. Свяжите устройства заново.")
        val secret = LocalCrypto.unb64(device.secret)
        val now = clock()
        if (abs(now - env.t) > LocalProto.CLOCK_SKEW_MS) {
            return err(408, "clock_skew", "Время на устройствах сильно расходится. Проверьте дату и время на обоих.")
        }
        val plain = try {
            Seal.openRequest(secret, path, env)
        } catch (_: LocalCryptoException) {
            return err(401, "bad_auth", "Ключ не подошёл. Свяжите устройства заново.")
        }
        val fresh = synchronized(lock) {
            if (seenNonces.containsKey(env.n)) false else { seenNonces[env.n] = now; true }
        }
        if (!fresh) return err(409, "replay", "Повторный запрос отклонён")

        val answer = when (path) {
            LocalProto.PATH_OFFER -> onOffer(device, gson.fromJson(plain, OfferReq::class.java), now)
            LocalProto.PATH_OFFER_STATUS -> onOfferStatus(device, gson.fromJson(plain, OfferStatusReq::class.java), now)
            else -> onPush(device, gson.fromJson(plain, PushReq::class.java), now)
        }
        return HttpReply(200, gson.toJson(Seal.response(secret, env, path, answer)))
    }

    private fun onOffer(device: PairedDevice, req: OfferReq, now: Long): String {
        if (req.transferId.length !in 8..64) throw JsonParseException("transferId")
        if (req.manifest.isEmpty()) return gson.toJson(OfferReply("declined"))
        val auto = device.autoAcceptLight && req.manifest.isLightOnly()
        val replaced = ArrayList<String>()
        val reply = synchronized(lock) {
            offers.values.filter { it.deviceId == device.id && it.state == "pending" }.forEach { it.state = "expired"; replaced += it.id }
            val o = IncomingOffer(req.transferId, device.id, req.manifest, now)
            if (auto) { o.state = "accepted"; o.accept = AcceptSet.LIGHT }
            offers[o.id] = o
            OfferReply(o.state, o.accept)
        }
        replaced.forEach { host.onOfferCancelled(it) }
        host.onOffer(req.transferId, device, req.manifest, auto)
        return gson.toJson(reply)
    }

    private fun onOfferStatus(device: PairedDevice, req: OfferStatusReq, now: Long): String {
        val reply = synchronized(lock) {
            val o = offers[req.transferId]?.takeIf { it.deviceId == device.id }
            when {
                o == null -> OfferReply("expired")
                o.state == "pending" && now - o.createdAt > LocalProto.OFFER_TTL_MS -> { o.state = "expired"; OfferReply("expired") }
                o.state == "accepted" -> OfferReply("accepted", o.accept)
                else -> OfferReply(o.state)
            }
        }
        return gson.toJson(reply)
    }

    private suspend fun onPush(device: PairedDevice, req: PushReq, now: Long): String {
        var accept: AcceptSet? = null
        synchronized(lock) {
            val o = offers[req.transferId]
            if (o != null && o.deviceId == device.id && o.state == "accepted" && !o.pushed) {
                o.pushed = true
                o.state = "receiving"
                accept = o.accept
            }
        }
        val allowed = accept ?: return gson.toJson(PushReply(false, null, "Передача не подтверждена получателем или уже выполнена"))

        // Отправителю не доверяем: ещё раз оставляем только разрешённое и убираем данные входа, если их не разрешили.
        val filtered: LocalSyncPayload = PayloadFilter.apply(req.payload, allowed)
        val stats = PayloadStats.of(filtered)
        return try {
            val raw = host.applyIncoming(device, allowed, filtered)
            val cats = LocalProto.CATEGORIES.associateWith { c ->
                val r = raw.categories[c] ?: CategoryResult()
                val st = stats.getValue(c)
                r.copy(received = st.count, idsChecksum = st.checksum)
            }
            val report = raw.copy(categories = cats)
            synchronized(lock) { offers[req.transferId]?.state = "done" }
            val ok = report.errors.isEmpty()
            store.get(device.id)?.let {
                store.put(it.copy(lastTransferAt = clock(), lastTransferOk = ok, lastTransferNote = if (ok) null else report.errors.first()))
            }
            host.onTransferFinished(req.transferId, device, report, null)
            gson.toJson(PushReply(true, report, null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(lock) { offers[req.transferId]?.state = "failed" }
            val msg = e.message ?: "Не удалось применить данные"
            host.onTransferFinished(req.transferId, device, null, msg)
            gson.toJson(PushReply(false, null, msg))
        }
    }
}
