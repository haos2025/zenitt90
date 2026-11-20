package com.platinum.ott.presentation.screens.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.sync.local.CodeAlphabet
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Показанный код: один и тот же для сервера и Wi-Fi. */
data class HubCode(
    /** XXXX-XXXX */
    val code: String,
    val secondsLeft: Int,
    /** Код принимает сервер (устройство можно связать из любой сети). */
    val server: Boolean,
    /** Код принимает приём по Wi-Fi (второе устройство рядом, в той же сети). */
    val wifi: Boolean
)

sealed interface HubShowState {
    object Idle : HubShowState
    object Loading : HubShowState
    data class Shown(val code: HubCode) : HubShowState
    data class Error(val message: String) : HubShowState
}

sealed interface HubEnterState {
    object Idle : HubEnterState
    data class Working(val text: String) : HubEnterState
    data class Success(val message: String) : HubEnterState
    data class Error(val message: String) : HubEnterState
}

/**
 * Единый ввод кода в «Мои устройства»: одна кнопка «Показать код» и одно поле «Ввести код» вместо двух пар
 * (сервер и Wi-Fi).
 *
 *  • Показать: код выдаёт сервер, и тот же код принимает приём по Wi-Fi. Нет сервера — код создаёт само устройство
 *    (только Wi-Fi), нет Wi-Fi — остаётся серверный.
 *  • Ввести: сначала сервер (там есть защита от перебора), затем — по Wi-Fi устройства рядом, которые показывают код
 *    (проверяются запросом без секретов, неверные попытки на чужих устройствах не расходуются).
 *  • Переключатель «Автосвязь по Wi-Fi»: включён — связываем и по серверу, и по Wi-Fi, если устройство рядом; выключен —
 *    только через сервер, а Wi-Fi лишь когда сервер недоступен. Связь по Wi-Fi всегда с обязательной сверкой цифр.
 */
@HiltViewModel
class PairingHubViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val syncRepository get() = sessionGraph.syncRepository
    private val prefs = context.getSharedPreferences("zenith_pairing", Context.MODE_PRIVATE)

    private val _autoWifi = MutableStateFlow(prefs.getBoolean(KEY_AUTO_WIFI, true))
    val autoWifi: StateFlow<Boolean> = _autoWifi

    fun setAutoWifi(enabled: Boolean) {
        _autoWifi.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_WIFI, enabled).apply()
    }

    private val _show = MutableStateFlow<HubShowState>(HubShowState.Idle)
    val show: StateFlow<HubShowState> = _show

    private val _enter = MutableStateFlow<HubEnterState>(HubEnterState.Idle)
    val enter: StateFlow<HubEnterState> = _enter

    private var showJob: Job? = null

    // ---------------------------------------------------------------- показать код

    fun showCode(local: LocalSyncViewModel) {
        showJob?.cancel()
        showJob = viewModelScope.launch {
            _show.value = HubShowState.Loading
            val serverRes = syncRepository.createPairingCode()
            val serverInfo = serverRes.getOrNull()
            val serverRaw = serverInfo?.code?.let { CodeAlphabet.normalize(it) }
            // Wi-Fi: включён переключатель либо сервер недоступен. Серверный код не по формату — по Wi-Fi не используем
            // (иначе на экране было бы два разных кода).
            val wifiWanted = _autoWifi.value || serverInfo == null
            val wifiRaw = if (wifiWanted && (serverInfo == null || serverRaw != null)) local.showCode(serverRaw) else null
            val raw = serverRaw ?: wifiRaw
            val display = when {
                serverInfo != null -> CodeAlphabet.format(serverRaw ?: serverInfo.code)
                raw != null -> CodeAlphabet.format(raw)
                else -> null
            }
            if (display == null) {
                _show.value = HubShowState.Error(serverRes.exceptionOrNull()?.message ?: "Не удалось получить код. Проверьте интернет и Wi-Fi.")
                return@launch
            }
            var remaining = serverInfo?.expiresInSeconds?.takeIf { it > 0 } ?: 300
            while (remaining > 0 && _show.value !is HubShowState.Idle) {
                val wifiAlive = wifiRaw != null && local.wifiCodeAlive()
                // Только Wi-Fi и код погас (его ввели или попытки кончились) — показывать больше нечего.
                if (serverInfo == null && !wifiAlive) break
                _show.value = HubShowState.Shown(HubCode(display, remaining, server = serverInfo != null, wifi = wifiAlive))
                delay(1000)
                remaining--
            }
            local.hideCode()
            _show.value = HubShowState.Idle
        }
    }

    fun hideCode(local: LocalSyncViewModel) {
        showJob?.cancel()
        local.hideCode()
        _show.value = HubShowState.Idle
    }

    // ---------------------------------------------------------------- ввести код

    fun enterCode(input: String, local: LocalSyncViewModel, manualAddress: String) {
        val code = CodeAlphabet.normalize(input)
        if (code == null) {
            _enter.value = HubEnterState.Error("Код состоит из 8 букв без гласных, например KFMQ-TNDT")
            return
        }
        if (_enter.value is HubEnterState.Working) return
        viewModelScope.launch {
            _enter.value = HubEnterState.Working("Проверяю код…")
            // 1. Сервер.
            var serverMessage: String? = null
            var serverError: String? = null
            val redeem = syncRepository.redeemPairingCode(code)
            if (redeem.isSuccess) {
                val inviter = redeem.getOrNull()?.inviterName.orEmpty()
                // Связывание только объединяет устройства в группу — данные переносит синхронизация.
                serverMessage = syncRepository.sync("pairing").fold(
                    onSuccess = { "Связано через сервер с «$inviter». Данные синхронизированы." },
                    onFailure = { "Связано через сервер с «$inviter», но синхронизация не удалась: ${it.message ?: "ошибка сети"}. Нажмите «Синхронизировать сейчас»." }
                )
            } else {
                serverError = redeem.exceptionOrNull()?.message ?: "Код неверен или истёк"
            }
            val serverOk = serverMessage != null

            // 2. Wi-Fi: по адресу вручную либо устройства рядом, показывающие код.
            // Переключатель выключен — только если сервер не сработал (иначе Wi-Fi не нужен).
            var wifiStarted = false
            var wifiNoPeers = false
            if (_autoWifi.value || !serverOk) {
                _enter.value = HubEnterState.Working(if (serverOk) "Ищу устройство рядом по Wi-Fi…" else "Сервер не ответил, пробую по Wi-Fi…")
                if (manualAddress.isNotBlank()) {
                    local.pairManual(manualAddress.trim(), code)
                    wifiStarted = true
                } else {
                    val peer = local.codePeers().firstOrNull()
                    if (peer != null) { local.pairWithPeer(peer, code); wifiStarted = true } else wifiNoPeers = true
                }
            }

            _enter.value = when {
                serverOk && wifiStarted -> HubEnterState.Success("$serverMessage Теперь сверьте цифры в окне, чтобы связать и по Wi-Fi.")
                serverOk && wifiNoPeers -> HubEnterState.Success("$serverMessage По Wi-Fi устройство рядом не найдено — связь только через сервер.")
                serverOk -> HubEnterState.Success(serverMessage.orEmpty())
                wifiStarted -> HubEnterState.Success("Через сервер связать не удалось (${serverError.orEmpty()}). Связываю по Wi-Fi — сверьте цифры в окне.")
                else -> HubEnterState.Error(
                    serverError.orEmpty() + if (wifiNoPeers) " Устройств, показывающих этот код, рядом по Wi-Fi тоже не найдено. Проверьте, что оба в одной сети и на втором открыты «Мои устройства»." else ""
                )
            }
        }
    }

    fun resetEnter() { if (_enter.value !is HubEnterState.Working) _enter.value = HubEnterState.Idle }

    private companion object {
        const val KEY_AUTO_WIFI = "auto_wifi_link"
    }
}
