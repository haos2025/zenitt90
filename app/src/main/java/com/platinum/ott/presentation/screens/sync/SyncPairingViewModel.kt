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
    private val prefs = ServiceLocator.authPreferences
    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val uiState: StateFlow<PairingUiState> = _uiState

    // Раньше единственный признак того, что синхронизация вообще
    // когда-либо срабатывала, — это мимолётное "Готово!" в момент самого
    // redeemCode()/syncNowManually(). Уйдя с экрана и вернувшись, узнать
    // "а была ли синхронизация вообще и когда" было неоткуда — сопряжение
    // и перенос данных выглядели неотличимо, если не следить за экраном
    // не отрываясь. lastSyncTimestamp уже пишется в SyncRepositoryImpl
    // при каждом успешном sync() — здесь просто читаем его в состояние.
    private val _lastSyncedAtMs = MutableStateFlow(prefs.lastSyncTimestamp)
    val lastSyncedAtMs: StateFlow<Long> = _lastSyncedAtMs

    private fun refreshLastSynced() { _lastSyncedAtMs.value = prefs.lastSyncTimestamp }

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
                .onSuccess {
                    // Раньше здесь сразу показывался "Готово!" — но
                    // redeemPairingCode() только регистрирует пару устройств
                    // на backend, реальные избранное/историю не переносит.
                    // syncNow() нигде в приложении не вызывался ВООБЩЕ — ни
                    // здесь, ни где-либо ещё — сопряжение технически
                    // "срабатывало", а данные никогда не передавались.
                    ServiceLocator.syncUseCase.syncNow()
                        .onSuccess { refreshLastSynced(); _uiState.value = PairingUiState.RedeemSuccess }
                        .onFailure {
                            // Пара устройств всё равно зарегистрирована — это
                            // не отменяем, только сообщаем, что сам перенос
                            // данных не удался и стоит попробовать вручную.
                            _uiState.value = PairingUiState.Error("Устройства сопряжены, но синхронизация данных не удалась: ${it.message ?: "ошибка сети"}. Нажмите «Синхронизировать сейчас».")
                        }
                }
                .onFailure { _uiState.value = PairingUiState.Error(it.message ?: "Код истёк или неверен") }
        }
    }

    // Раньше не было вообще никакого способа запустить sync() повторно
    // после первого сопряжения — ни автоматического (нет фонового воркера),
    // ни ручного (не было кнопки нигде в приложении).
    fun syncNowManually() {
        viewModelScope.launch {
            _uiState.value = PairingUiState.Loading
            ServiceLocator.syncUseCase.syncNow()
                .onSuccess { refreshLastSynced(); _uiState.value = PairingUiState.RedeemSuccess }
                .onFailure { _uiState.value = PairingUiState.Error(it.message ?: "Не удалось синхронизировать") }
        }
    }

    fun reset() { _uiState.value = PairingUiState.Idle }
}
