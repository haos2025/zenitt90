package com.platinum.ott.presentation.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.sync.DeviceInfo
import com.platinum.ott.sync.SyncReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Состояния «ввода кода на этом устройстве» и «показа своего кода» разведены (см. историю
// диагностики PROMPT_ACCOUNT_REDESIGN.md): каждое рендерится прямо под своей формой.
sealed interface RedeemUiState {
    object Idle : RedeemUiState
    object Loading : RedeemUiState
    data class Success(val message: String) : RedeemUiState
    data class Error(val message: String) : RedeemUiState
}

sealed interface PairingUiState {
    object Idle : PairingUiState
    object Loading : PairingUiState
    data class CodeShown(val code: String, val secondsLeft: Int) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

sealed interface SyncRunState {
    object Idle : SyncRunState
    object Running : SyncRunState
    data class Failed(val message: String) : SyncRunState
}

data class DevicesUiState(
    val loading: Boolean = false,
    val devices: List<DeviceInfo> = emptyList(),
    val error: String? = null
)

/**
 * «Мои устройства» (серверный режим): список связанных устройств с подтверждением получения,
 * ручная синхронизация с отчётом, автосинхронизация, добавление устройства по коду.
 * Локальная синхронизация по Wi-Fi (LocalSyncViewModel) — отдельная секция того же экрана.
 */
@HiltViewModel
class SyncPairingViewModel @Inject constructor(
    private val sessionGraph: SessionGraph,
    private val prefs: AuthPreferences
) : ViewModel() {
    private val syncRepository get() = sessionGraph.syncRepository

    private val _devices = MutableStateFlow(DevicesUiState())
    val devices: StateFlow<DevicesUiState> = _devices

    private val _syncState = MutableStateFlow<SyncRunState>(SyncRunState.Idle)
    val syncState: StateFlow<SyncRunState> = _syncState

    private val _reports = MutableStateFlow<List<SyncReport>>(emptyList())
    val reports: StateFlow<List<SyncReport>> = _reports

    private val _redeemState = MutableStateFlow<RedeemUiState>(RedeemUiState.Idle)
    val redeemState: StateFlow<RedeemUiState> = _redeemState

    private val _pairingState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val pairingState: StateFlow<PairingUiState> = _pairingState

    private val _autoSync = MutableStateFlow(prefs.autoSyncEnabled)
    val autoSync: StateFlow<Boolean> = _autoSync

    private val _deviceName = MutableStateFlow(prefs.deviceName.orEmpty())
    val deviceName: StateFlow<String> = _deviceName

    private val _linksLost = MutableStateFlow(prefs.syncLinksLost)
    val linksLost: StateFlow<Boolean> = _linksLost

    private val _lastSyncedAtMs = MutableStateFlow(prefs.lastSyncTimestamp)
    val lastSyncedAtMs: StateFlow<Long> = _lastSyncedAtMs

    /** Сообщение о результате действия над устройством (переименование, отвязка). */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    val isRegistered: Boolean get() = !prefs.v2Token.isNullOrBlank()

    init {
        refreshAll()
    }

    private fun refreshAll() {
        _reports.value = syncRepository.recentReports()
        _lastSyncedAtMs.value = prefs.lastSyncTimestamp
        _linksLost.value = prefs.syncLinksLost
        _deviceName.value = prefs.deviceName.orEmpty()
        refreshDevices()
    }

    fun refreshDevices() {
        // Не регистрируем устройство на сервере молча при открытии экрана: первая регистрация —
        // по действию пользователя («Синхронизировать сейчас», «Показать код», ввод кода).
        if (!isRegistered) {
            _devices.value = DevicesUiState()
            return
        }
        viewModelScope.launch {
            _devices.value = _devices.value.copy(loading = true, error = null)
            syncRepository.devices()
                .onSuccess { _devices.value = DevicesUiState(devices = it) }
                .onFailure { _devices.value = DevicesUiState(devices = _devices.value.devices, error = it.message) }
            _linksLost.value = prefs.syncLinksLost
        }
    }

    fun syncNow() {
        if (_syncState.value is SyncRunState.Running) return
        viewModelScope.launch {
            _syncState.value = SyncRunState.Running
            syncRepository.sync("manual")
                .onSuccess { _syncState.value = SyncRunState.Idle }
                .onFailure { _syncState.value = SyncRunState.Failed(it.message ?: "Не удалось синхронизировать") }
            refreshAll()
        }
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.autoSyncEnabled = enabled
        _autoSync.value = enabled
    }

    fun renameDevice(id: String, name: String) {
        viewModelScope.launch {
            syncRepository.renameDevice(id, name)
                .onSuccess { _actionMessage.value = null; refreshAll() }
                .onFailure { _actionMessage.value = it.message ?: "Не удалось переименовать" }
        }
    }

    fun unlinkDevice(id: String) {
        viewModelScope.launch {
            syncRepository.unlinkDevice(id)
                .onSuccess { _actionMessage.value = null; refreshAll() }
                .onFailure { _actionMessage.value = it.message ?: "Не удалось отвязать устройство" }
        }
    }

    fun dismissActionMessage() { _actionMessage.value = null }

    fun dismissLinksLost() {
        prefs.syncLinksLost = false
        _linksLost.value = false
    }

    fun createCode() {
        viewModelScope.launch {
            _pairingState.value = PairingUiState.Loading
            syncRepository.createPairingCode()
                .onSuccess { pairing ->
                    var remaining = pairing.expiresInSeconds
                    _pairingState.value = PairingUiState.CodeShown(pairing.code, remaining)
                    // Обратный отсчёт: пользователь должен видеть, что код вот-вот истечёт.
                    while (remaining > 0 && _pairingState.value is PairingUiState.CodeShown) {
                        delay(1000)
                        remaining--
                        _pairingState.value = PairingUiState.CodeShown(pairing.code, remaining)
                    }
                    if (_pairingState.value is PairingUiState.CodeShown) _pairingState.value = PairingUiState.Idle
                    refreshDevices() // за время показа кода к группе могли присоединиться
                }
                .onFailure { _pairingState.value = PairingUiState.Error(it.message ?: "Не удалось создать код") }
        }
    }

    fun redeemCode(input: String) {
        // Код — 8 букв (без гласных), регистр и дефис не важны; формат проверяет и сервер.
        val code = input.uppercase().filter { it.isLetter() }
        if (code.length != CODE_LENGTH) {
            _redeemState.value = RedeemUiState.Error("Код состоит из 8 букв, например KFMQ-TNDT")
            return
        }
        viewModelScope.launch {
            _redeemState.value = RedeemUiState.Loading
            syncRepository.redeemPairingCode(code)
                .onSuccess { result ->
                    // Сопряжение только объединяет устройства в группу — данные переносит синхронизация.
                    syncRepository.sync("pairing")
                        .onSuccess {
                            _redeemState.value = RedeemUiState.Success("Устройство связано с «${result.inviterName}». Данные синхронизированы.")
                        }
                        .onFailure {
                            _redeemState.value = RedeemUiState.Error(
                                "Устройства связаны, но синхронизация не удалась: ${it.message ?: "ошибка сети"}. Нажмите «Синхронизировать сейчас»."
                            )
                        }
                    refreshAll()
                }
                .onFailure { _redeemState.value = RedeemUiState.Error(it.message ?: "Код неверен или истёк") }
        }
    }

    fun resetRedeem() { _redeemState.value = RedeemUiState.Idle }

    private companion object {
        const val CODE_LENGTH = 8
    }
}
