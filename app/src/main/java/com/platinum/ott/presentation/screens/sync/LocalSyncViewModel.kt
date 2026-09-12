package com.platinum.ott.presentation.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.data.remote.dto.LocalSyncPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// PROMPT_LOCAL_SYNC_V1.md. Сводка результата — то, что реально видно
// пользователю после обмена (сколько чего перенесено), а не просто "Готово".
data class LocalSyncSummary(
    val favorites: Int, val history: Int, val sources: Int, val plugins: Int
) {
    companion object {
        fun from(payload: LocalSyncPayload) = LocalSyncSummary(
            favorites = payload.favorites.size, history = payload.watchHistory.size,
            sources = payload.sources.size, plugins = payload.plugins.size
        )
    }
}

// TV — показ QR (адрес) + кода.
sealed interface LocalSyncTvState {
    object Idle : LocalSyncTvState
    data class Showing(val qrContent: String, val code: String, val secondsLeft: Int) : LocalSyncTvState
    data class Applied(val summary: LocalSyncSummary) : LocalSyncTvState
    data class Error(val message: String) : LocalSyncTvState
}

// Телефон — после сканирования QR (адрес получен), ждём код от пользователя,
// затем сам обмен.
sealed interface LocalSyncPhoneState {
    object Idle : LocalSyncPhoneState
    object Connecting : LocalSyncPhoneState
    data class Success(val summary: LocalSyncSummary) : LocalSyncPhoneState
    data class Error(val message: String) : LocalSyncPhoneState
}

@HiltViewModel
class LocalSyncViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val repository = sessionGraph.localSyncRepository

    private val _tvState = MutableStateFlow<LocalSyncTvState>(LocalSyncTvState.Idle)
    val tvState: StateFlow<LocalSyncTvState> = _tvState

    private val _phoneState = MutableStateFlow<LocalSyncPhoneState>(LocalSyncPhoneState.Idle)
    val phoneState: StateFlow<LocalSyncPhoneState> = _phoneState

    // ---------- TV ----------

    fun startTv() {
        val ip = LocalNetworkUtils.getLocalIpAddress()
        if (ip == null) {
            _tvState.value = LocalSyncTvState.Error("Не удалось определить локальный IP-адрес. Проверьте, что TV подключён к сети.")
            return
        }
        val code = repository.startTvServer(onApplied = { merged ->
            // Уже на главном потоке (см. Handler.post в LocalSyncRepository) —
            // можно менять StateFlow напрямую. Сервер больше не нужен сразу
            // после успешного обмена — держать его дальше только увеличивает
            // окно, в которое ещё действующий (не истёкший по TTL) код
            // теоретически можно использовать повторно.
            _tvState.value = LocalSyncTvState.Applied(LocalSyncSummary.from(merged))
            repository.stopTvServer()
        })
        val port = repository.tvServerPort() ?: return
        val qrContent = "http://$ip:$port"
        var remaining = code.secondsLeft()
        _tvState.value = LocalSyncTvState.Showing(qrContent, code.code, remaining)
        viewModelScope.launch {
            // Тот же обратный отсчёт, что и у SyncPairingViewModel.createCode() —
            // пользователь должен видеть, что код вот-вот истечёт, а не
            // узнать об этом по ошибке на телефоне.
            while (remaining > 0 && _tvState.value is LocalSyncTvState.Showing) {
                delay(1000)
                remaining--
                _tvState.value = LocalSyncTvState.Showing(qrContent, code.code, remaining)
            }
            if (_tvState.value is LocalSyncTvState.Showing) {
                stopTv()
            }
        }
    }

    fun stopTv() {
        repository.stopTvServer()
        _tvState.value = LocalSyncTvState.Idle
    }

    fun resetTv() { _tvState.value = LocalSyncTvState.Idle }

    // ---------- Телефон ----------

    fun syncFromPhone(baseUrl: String, code: String) {
        if (code.length != 6 || code.any { !it.isDigit() }) {
            _phoneState.value = LocalSyncPhoneState.Error("Код — это 6 цифр")
            return
        }
        viewModelScope.launch {
            _phoneState.value = LocalSyncPhoneState.Connecting
            repository.syncWithTv(baseUrl, code)
                .onSuccess { _phoneState.value = LocalSyncPhoneState.Success(LocalSyncSummary.from(it)) }
                .onFailure { _phoneState.value = LocalSyncPhoneState.Error(it.message ?: "Не удалось синхронизироваться") }
        }
    }

    fun resetPhone() { _phoneState.value = LocalSyncPhoneState.Idle }

    override fun onCleared() {
        super.onCleared()
        repository.stopTvServer()
    }
}
