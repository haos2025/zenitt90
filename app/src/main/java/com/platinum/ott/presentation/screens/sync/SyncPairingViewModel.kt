package com.platinum.ott.presentation.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PairingUiState {
    object Idle : PairingUiState
    object Loading : PairingUiState
    data class CodeShown(val code: String, val secondsLeft: Int) : PairingUiState
    object RedeemSuccess : PairingUiState
    data class Error(val message: String) : PairingUiState
}

class SyncPairingViewModel : ViewModel() {
    private val syncRepository = ServiceLocator.syncRepository
    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState

    fun createCode() {
        viewModelScope.launch {
            _uiState.value = PairingUiState.Loading
            syncRepository.createPairingCode()
                .onSuccess { pairing ->
                    var remaining = pairing.expiresInSeconds
                    _uiState.value = PairingUiState.CodeShown(pairing.code, remaining)
                    // Обратный отсчёт — код живёт 10 минут на backend, но
                    // пользователь должен ВИДЕТЬ, что он вот-вот истечёт,
                    // а не узнать об этом только по ошибке при вводе на
                    // втором устройстве.
                    while (remaining > 0 && _uiState.value is PairingUiState.CodeShown) {
                        delay(1000)
                        remaining--
                        _uiState.value = PairingUiState.CodeShown(pairing.code, remaining)
                    }
                    if (_uiState.value is PairingUiState.CodeShown) _uiState.value = PairingUiState.Idle
                }
                .onFailure { _uiState.value = PairingUiState.Error(it.message ?: "Не удалось создать код") }
        }
    }

    fun redeemCode(code: String) {
        if (code.length != 6 || code.any { !it.isDigit() }) {
            _uiState.value = PairingUiState.Error("Код — это 6 цифр")
            return
        }
        viewModelScope.launch {
            _uiState.value = PairingUiState.Loading
            syncRepository.redeemPairingCode(code)
                .onSuccess { _uiState.value = PairingUiState.RedeemSuccess }
                .onFailure { _uiState.value = PairingUiState.Error(it.message ?: "Код истёк или неверен") }
        }
    }

    fun reset() { _uiState.value = PairingUiState.Idle }
}
