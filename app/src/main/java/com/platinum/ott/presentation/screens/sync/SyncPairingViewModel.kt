package com.platinum.ott.presentation.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.SessionGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Диагностика PROMPT_ACCOUNT_REDESIGN.md, шаг 1: раньше redeemCode() (форма
// ввода кода на НОВОМ устройстве, верх экрана) и createCode() (показ кода на
// уже настроенном устройстве, низ экрана) писали в один общий uiState. Из-за
// этого нажатие "Подключить" наверху не давало заметной реакции рядом с
// собой — спиннер/ошибка отрисовывались только в нижней секции экрана, под
// заголовком "На уже настроенном устройстве", в связке с отсутствием
// verticalScroll на Column (см. SyncPairingScreen.kt) там же могли быть
// вообще не видны на TV/невысоком экране. Это и объясняет репортившийся
// симптом "вводишь код, жмёшь Подключить — минуту ничего не происходит,
// ни ошибок": реакция была, просто не там и не всегда видна. Разведено на
// два независимых состояния, каждое рендерится сразу под своей формой.
sealed interface RedeemUiState {
    object Idle : RedeemUiState
    object Loading : RedeemUiState
    object Success : RedeemUiState
    data class Error(val message: String) : RedeemUiState
}

sealed interface PairingUiState {
    object Idle : PairingUiState
    object Loading : PairingUiState
    data class CodeShown(val code: String, val secondsLeft: Int) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

@HiltViewModel
class SyncPairingViewModel @Inject constructor(
    private val sessionGraph: SessionGraph,
    // authPreferences не завязан на логин/реавторизацию (см.
    // core/di/PreferencesModule.kt) — берём его напрямую Hilt-синглтоном,
    // а не через sessionGraph, как и раньше в ServiceLocator это был
    // отдельный `by lazy`, не часть initAuth().
    private val prefs: AuthPreferences
) : ViewModel() {
    private val syncRepository = sessionGraph.syncRepository

    // Верхняя форма — ввод кода на НОВОМ устройстве.
    private val _redeemState = MutableStateFlow<RedeemUiState>(RedeemUiState.Idle)
    val redeemState: StateFlow<RedeemUiState> = _redeemState

    // Нижняя секция — показ своего кода на уже настроенном устройстве.
    private val _pairingState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val pairingState: StateFlow<PairingUiState> = _pairingState

    // Кнопка "Синхронизировать сейчас" — раньше была видна ТОЛЬКО сразу
    // после первого успешного сопряжения (внутри ветки RedeemSuccess),
    // на всех остальных визитах на экран кнопки не было нигде вообще.
    // Теперь у неё своё отдельное, всегда видимое состояние.
    private val _manualSyncState = MutableStateFlow<RedeemUiState>(RedeemUiState.Idle)
    val manualSyncState: StateFlow<RedeemUiState> = _manualSyncState

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
            _pairingState.value = PairingUiState.Loading
            syncRepository.createPairingCode()
                .onSuccess { pairing ->
                    var remaining = pairing.expiresInSeconds
                    _pairingState.value = PairingUiState.CodeShown(pairing.code, remaining)
                    // Обратный отсчёт — код живёт 10 минут на backend, но
                    // пользователь должен ВИДЕТЬ, что он вот-вот истечёт,
                    // а не узнать об этом только по ошибке при вводе на
                    // втором устройстве.
                    while (remaining > 0 && _pairingState.value is PairingUiState.CodeShown) {
                        delay(1000)
                        remaining--
                        _pairingState.value = PairingUiState.CodeShown(pairing.code, remaining)
                    }
                    if (_pairingState.value is PairingUiState.CodeShown) _pairingState.value = PairingUiState.Idle
                }
                .onFailure { _pairingState.value = PairingUiState.Error(it.message ?: "Не удалось создать код") }
        }
    }

    fun redeemCode(code: String) {
        if (code.length != 6 || code.any { !it.isDigit() }) {
            _redeemState.value = RedeemUiState.Error("Код — это 6 цифр")
            return
        }
        viewModelScope.launch {
            _redeemState.value = RedeemUiState.Loading
            syncRepository.redeemPairingCode(code)
                .onSuccess {
                    // Раньше здесь сразу показывался "Готово!" — но
                    // redeemPairingCode() только регистрирует пару устройств
                    // на backend, реальные избранное/историю не переносит.
                    // syncNow() нигде в приложении не вызывался ВООБЩЕ — ни
                    // здесь, ни где-либо ещё — сопряжение технически
                    // "срабатывало", а данные никогда не передавались.
                    sessionGraph.syncUseCase.syncNow()
                        .onSuccess { refreshLastSynced(); _redeemState.value = RedeemUiState.Success }
                        .onFailure {
                            // Пара устройств всё равно зарегистрирована — это
                            // не отменяем, только сообщаем, что сам перенос
                            // данных не удался и стоит попробовать вручную.
                            _redeemState.value = RedeemUiState.Error("Устройства сопряжены, но синхронизация данных не удалась: ${it.message ?: "ошибка сети"}. Нажмите «Синхронизировать сейчас».")
                        }
                }
                .onFailure { _redeemState.value = RedeemUiState.Error(it.message ?: "Код истёк или неверен") }
        }
    }

    // Раньше не было вообще никакого способа запустить sync() повторно
    // после первого сопряжения — ни автоматического (нет фонового воркера),
    // ни ручного (кнопка была спрятана внутри RedeemSuccess).
    fun syncNowManually() {
        viewModelScope.launch {
            _manualSyncState.value = RedeemUiState.Loading
            sessionGraph.syncUseCase.syncNow()
                .onSuccess { refreshLastSynced(); _manualSyncState.value = RedeemUiState.Success }
                .onFailure { _manualSyncState.value = RedeemUiState.Error(it.message ?: "Не удалось синхронизировать") }
        }
    }

    fun resetRedeem() { _redeemState.value = RedeemUiState.Idle }
    fun resetPairing() { _pairingState.value = PairingUiState.Idle }
}
