package com.platinum.ott.presentation.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.sync.local.AcceptSet
import com.platinum.ott.sync.local.ApplyReport
import com.platinum.ott.sync.local.ClientPairSession
import com.platinum.ott.sync.local.CodeAlphabet
import com.platinum.ott.sync.local.EndpointParser
import com.platinum.ott.sync.local.Endpoint
import com.platinum.ott.sync.local.Manifest
import com.platinum.ott.sync.local.NearbyPeer
import com.platinum.ott.sync.local.PairStep
import com.platinum.ott.sync.local.PairedDevice
import com.platinum.ott.sync.local.ReceiverEvents
import com.platinum.ott.sync.local.SendResult
import com.platinum.ott.sync.local.SendStage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Состояние приёма: пока раздел открыт, устройство видно в сети и может принимать. */
data class NearbyUiState(
    val running: Boolean = false,
    /** «192.168.1.5:37221» — для ручного ввода на другом устройстве. */
    val address: String? = null,
    val paired: List<PairedDevice> = emptyList(),
    /** Код связывания в виде XXXX-XXXX, пока он показан. */
    val code: String? = null,
    val codeSecondsLeft: Int = 0,
    val notice: String? = null
)

/** Окно связывания (одинаково для обеих сторон: сверка контрольной строки). */
sealed interface PairUi {
    object None : PairUi
    data class Connecting(val text: String) : PairUi
    data class Confirm(val peerName: String, val sas: String, val selfDecided: Boolean) : PairUi
    data class Done(val peerName: String) : PairUi
    data class Failed(val message: String) : PairUi
}

data class IncomingUi(val transferId: String, val deviceId: String, val deviceName: String, val manifest: Manifest)

sealed interface SendUi {
    object None : SendUi
    data class Running(val deviceName: String, val stage: SendStage) : SendUi
    data class Done(val result: SendResult) : SendUi
    data class Failed(val deviceName: String, val message: String) : SendUi
}

data class ReceivedUi(val deviceName: String, val report: ApplyReport?, val error: String?)

/**
 * «Устройства рядом» (локальная синхронизация по Wi-Fi, протокол v2). Приём работает, пока раздел открыт:
 * startNearby()/stopNearby() вызывает экран по жизненному циклу.
 */
@HiltViewModel
class LocalSyncViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val repo get() = sessionGraph.localSyncRepository

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state

    val peers: StateFlow<List<NearbyPeer>> get() = repo.peers

    private val _pair = MutableStateFlow<PairUi>(PairUi.None)
    val pair: StateFlow<PairUi> = _pair

    private val _incoming = MutableStateFlow<IncomingUi?>(null)
    val incoming: StateFlow<IncomingUi?> = _incoming

    private val _send = MutableStateFlow<SendUi>(SendUi.None)
    val send: StateFlow<SendUi> = _send

    private val _received = MutableStateFlow<ReceivedUi?>(null)
    val received: StateFlow<ReceivedUi?> = _received

    private var codeJob: Job? = null
    private var pairSession: ClientPairSession? = null
    private var responderSid: String? = null

    /** События от движка приёма (приходят из потока HTTP-сервера; StateFlow потокобезопасен). */
    private val events = object : ReceiverEvents {
        override fun onCodeExpired(reason: String) {
            codeJob?.cancel()
            _state.value = _state.value.copy(code = null, notice = reason)
        }

        override fun onPairingRequest(sessionId: String, peerName: String, peerType: String, sas: String) {
            codeJob?.cancel()
            responderSid = sessionId
            _state.value = _state.value.copy(code = null)
            _pair.value = PairUi.Confirm(peerName, sas, selfDecided = false)
        }

        override fun onPairingFinished(sessionId: String, ok: Boolean, device: PairedDevice?, reason: String?) {
            if (sessionId != responderSid) return
            responderSid = null
            _pair.value = if (ok && device != null) PairUi.Done(device.name) else PairUi.Failed(reason ?: "Связывание не завершено")
            refreshPaired()
        }

        override fun onOffer(transferId: String, device: PairedDevice, manifest: Manifest, autoAccepted: Boolean) {
            if (!autoAccepted) _incoming.value = IncomingUi(transferId, device.id, device.name, manifest)
        }

        override fun onOfferCancelled(transferId: String) {
            if (_incoming.value?.transferId == transferId) _incoming.value = null
        }

        override fun onTransferFinished(transferId: String, device: PairedDevice, report: ApplyReport?, error: String?) {
            if (_incoming.value?.transferId == transferId) _incoming.value = null
            _received.value = ReceivedUi(device.name, report, error)
            refreshPaired()
        }
    }

    // ---------------------------------------------------------------- приём

    fun startNearby() {
        if (_state.value.running) return
        val info = repo.startNearby(events)
        _state.value = if (info == null) {
            NearbyUiState(paired = repo.pairedDevices(), notice = "Не удалось запустить приём. Проверьте подключение к Wi-Fi.")
        } else {
            NearbyUiState(running = true, address = info.address, paired = repo.pairedDevices())
        }
    }

    fun stopNearby() {
        codeJob?.cancel()
        repo.stopNearby()
        _incoming.value = null
        _state.value = _state.value.copy(running = false, code = null)
    }

    private fun refreshPaired() {
        _state.value = _state.value.copy(paired = repo.pairedDevices())
    }

    fun dismissNotice() { _state.value = _state.value.copy(notice = null) }

    // ---------------------------------------------------------------- код на этом устройстве

    fun showCode() {
        val engine = repo.engine() ?: run {
            _state.value = _state.value.copy(notice = "Приём не запущен. Проверьте подключение к Wi-Fi.")
            return
        }
        val code = engine.showCode()
        codeJob?.cancel()
        codeJob = viewModelScope.launch {
            while (engine.currentCode() === code) {
                _state.value = _state.value.copy(
                    code = CodeAlphabet.format(code.code), codeSecondsLeft = code.secondsLeft(System.currentTimeMillis()), notice = null
                )
                delay(1000)
            }
            _state.value = _state.value.copy(code = null)
        }
    }

    fun hideCode() {
        repo.engine()?.hideCode()
        codeJob?.cancel()
        _state.value = _state.value.copy(code = null)
    }

    // ---------------------------------------------------------------- связывание (вводим код)

    fun pairWithPeer(peer: NearbyPeer, code: String) = startPairing(Endpoint(peer.host, peer.port), code, peer.name)

    fun pairManual(address: String, code: String) {
        val ep = EndpointParser.parse(address)
        if (ep == null) {
            _pair.value = PairUi.Failed("Адрес указан неверно. Пример: 192.168.1.5 или 192.168.1.5:37221")
            return
        }
        startPairing(ep, code, ep.host)
    }

    private fun startPairing(ep: Endpoint, code: String, label: String) {
        viewModelScope.launch {
            _pair.value = PairUi.Connecting("Подключаюсь к «$label»…")
            repo.client().pairInit(ep, code)
                .onSuccess {
                    pairSession = it
                    _pair.value = PairUi.Confirm(it.peerName, it.sas, selfDecided = false)
                }
                .onFailure { _pair.value = PairUi.Failed(it.message ?: "Не удалось связаться") }
        }
    }

    /** match = true: пользователь подтвердил, что контрольная строка совпадает с экраном второго устройства. */
    fun confirmPairing(match: Boolean) {
        val current = _pair.value as? PairUi.Confirm ?: return
        val session = pairSession
        if (session == null) {
            // Сторона, показавшая код.
            val sid = responderSid ?: return
            repo.engine()?.decidePairing(sid, match)
            if (match) _pair.value = current.copy(selfDecided = true)
            else { responderSid = null; _pair.value = PairUi.Failed("Вы отклонили связывание.") }
            return
        }
        viewModelScope.launch {
            val client = repo.client()
            if (!match) {
                client.pairStep(session, false)
                pairSession = null
                _pair.value = PairUi.Failed("Контрольные строки не совпали — связывание отменено. Возможно, в сети кто-то вмешивается.")
                return@launch
            }
            _pair.value = current.copy(selfDecided = true)
            var step = client.pairStep(session, true).getOrElse {
                pairSession = null
                _pair.value = PairUi.Failed(it.message ?: "Связывание не завершено")
                return@launch
            }
            if (step == PairStep.WAITING) {
                step = client.pairAwait(session).getOrElse {
                    pairSession = null
                    _pair.value = PairUi.Failed(it.message ?: "Связывание не завершено")
                    return@launch
                }
            }
            pairSession = null
            _pair.value = if (step == PairStep.DONE) PairUi.Done(session.peerName) else PairUi.Failed("Другое устройство отклонило связывание.")
            refreshPaired()
        }
    }

    fun dismissPair() {
        _pair.value = PairUi.None
    }

    // ---------------------------------------------------------------- передача

    fun sendTo(device: PairedDevice, manualAddress: String) {
        if (_send.value is SendUi.Running) return
        val peer = peers.value.firstOrNull { it.deviceId == device.id }
        val endpoint = if (peer != null) Endpoint(peer.host, peer.port) else EndpointParser.parse(manualAddress)
        if (endpoint == null) {
            _send.value = SendUi.Failed(
                device.name,
                "Устройство не найдено рядом. Откройте на нём «Мои устройства» (оба должны быть в одной Wi-Fi сети) " +
                    "или введите его адрес вручную."
            )
            return
        }
        viewModelScope.launch {
            _send.value = SendUi.Running(device.name, SendStage.OFFERING)
            repo.sendTo(endpoint, device) { stage -> _send.value = SendUi.Running(device.name, stage) }
                .onSuccess { _send.value = SendUi.Done(it) }
                .onFailure { _send.value = SendUi.Failed(device.name, it.message ?: "Не удалось отправить") }
            refreshPaired()
        }
    }

    fun dismissSend() { _send.value = SendUi.None }
    fun dismissReceived() { _received.value = null }

    fun forget(device: PairedDevice) {
        repo.forget(device.id)
        refreshPaired()
    }

    // ---------------------------------------------------------------- входящее предложение

    fun acceptIncoming(accept: AcceptSet, alwaysLight: Boolean) {
        val offer = _incoming.value ?: return
        repo.engine()?.decideOffer(offer.transferId, accept, alwaysLight)
        _incoming.value = null
    }

    fun declineIncoming() {
        val offer = _incoming.value ?: return
        repo.engine()?.decideOffer(offer.transferId, null, false)
        _incoming.value = null
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopNearby()
    }
}
