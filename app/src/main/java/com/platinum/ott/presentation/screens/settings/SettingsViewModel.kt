package com.platinum.ott.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.AuthPreferences
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.core.di.ThemeManager
import com.platinum.ott.domain.usecase.OtaUpdateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState { object Idle : SettingsUiState; object Loading : SettingsUiState; data class Success(val result: OtaUpdateUseCase.OtaResult) : SettingsUiState; data class Error(val message: String) : SettingsUiState }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionGraph: SessionGraph,
    private val themeManager: ThemeManager,
    // authPreferences не завязан на логин/реавторизацию (см. PreferencesModule.kt,
    // тот же аргумент, что уже был в SyncPairingViewModel) — берём его напрямую
    // Hilt-синглтоном, а не через sessionGraph, только чтобы прочитать
    // lastSyncTimestamp для статуса строки "Синхронизация" (см. ниже).
    private val authPreferences: AuthPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun runOtaUpdate() { viewModelScope.launch { _uiState.value = SettingsUiState.Loading; sessionGraph.otaUpdateUseCase.execute().onSuccess { _uiState.value = SettingsUiState.Success(it) }.onFailure { _uiState.value = SettingsUiState.Error(it.message ?: "Ошибка") } } }
    // logout()/isConnected() удалены вместе с кнопкой "Сменить аккаунт" и
    // секцией "Аккаунт" (см. SettingsScreen.kt) — задача "Источники"
    // (PROMPT_SOURCES_SCREEN.md) заменила единственный источник на список
    // PlaylistSource, понятия "залогинен/не залогинен" на один аккаунт
    // больше нет. sessionGraph.checkAuthUseCase/loginM3UUseCase/
    // loginXtreamUseCase/logoutUseCase и весь AuthRepository — удалены
    // отдельным аудитом позже (PROMPT_REVISION.md), после подтверждения,
    // что ничего в проекте их больше не вызывает.

    // Раньше здесь был временный двойной вызов ServiceLocator.reinitWithAuth()
    // + sessionGraph.reinitWithAuth() — тот же мост, что и в SetupViewModel.
    // ServiceLocator.kt удалён, оставлен только sessionGraph.reinitWithAuth().
    fun applyNetworkTimeoutChange() { sessionGraph.reinitWithAuth() }

    val darkThemeFlow: StateFlow<Boolean> = themeManager.darkThemeFlow

    fun setDarkTheme(enabled: Boolean) { themeManager.setDarkTheme(enabled) }

    // PROMPT_SETTINGS_UPGRADE.md п.5 — секции "Источники"/"Синхронизация"
    // стали навигационными строками со статусом вместо голого заголовка.
    // Источников уже может быть 0/1/много (PlaylistSource, не бинарный
    // AuthPreferences) — считаем реально сохранённые записи, не читаем
    // ничего из удалённого AuthRepository.
    private val _sourcesCount = MutableStateFlow(0)
    val sourcesCount: StateFlow<Int> = _sourcesCount

    // lastSyncTimestamp пишется в SyncRepositoryImpl при каждом успешном
    // sync() (тот же источник данных, что уже читает SyncPairingViewModel
    // для своей строки "Последняя синхронизация: ...") — здесь просто
    // всплывает то же значение на экран Настроек, отдельного стейта в
    // SyncRepositoryImpl под это заводить не нужно.
    private val _lastSyncedAtMs = MutableStateFlow(authPreferences.lastSyncTimestamp)
    val lastSyncedAtMs: StateFlow<Long> = _lastSyncedAtMs

    init { refreshStatusRows() }

    // Экран Настроек — отдельный composable() в NavHost, а не сохранённая
    // вкладка с restoreState, поэтому при возврате с "Источники"/
    // "Синхронизация" он пересоздаётся заново и это вызывается снова —
    // см. LaunchedEffect(Unit) в SettingsScreen.kt/PhoneSettingsScreen.kt.
    fun refreshStatusRows() {
        viewModelScope.launch {
            _sourcesCount.value = sessionGraph.playlistSourceRepository.getAll().size
            _lastSyncedAtMs.value = authPreferences.lastSyncTimestamp
        }
    }
}
