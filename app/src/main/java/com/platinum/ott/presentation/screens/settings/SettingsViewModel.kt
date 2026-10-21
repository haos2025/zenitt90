package com.platinum.ott.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.AuthPreferences
import android.content.Context
import com.platinum.ott.core.InterfacePreferences
import com.platinum.ott.core.NetworkPreferences
import com.platinum.ott.core.NotificationPreferences
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.SubtitlePreferences
import com.platinum.ott.core.nextAfter
import dagger.hilt.android.qualifiers.ApplicationContext
import com.platinum.ott.core.PlayerOverlaySettings
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
    private val authPreferences: AuthPreferences,
    private val interfacePreferences: InterfacePreferences,
    private val networkPreferences: NetworkPreferences,
    private val notificationPreferences: NotificationPreferences,
    @ApplicationContext context: Context
) : ViewModel() {
    // Настройки воспроизведения/сети/уведомлений раньше создавали
    // QualityPreferences(context) и т.п. прямо внутри composable — отдельно на ТВ и на
    // телефоне. Теперь одно место (план 09, раздел 5): оба экрана читают эти потоки.
    private val qualityPrefs = QualityPreferences(context)
    private val subtitlePrefs = SubtitlePreferences(context)

    val qualityOptions = listOf("Авто", "1080p", "720p", "480p")
    private val _quality = MutableStateFlow(qualityPrefs.getSelectedQuality() ?: "Авто")
    val quality: StateFlow<String> = _quality
    fun cycleQuality() {
        val next = qualityOptions.nextAfter(_quality.value)
        if (next == "Авто") qualityPrefs.clearSelectedQuality() else qualityPrefs.setSelectedQuality(next)
        _quality.value = next
    }

    private val _subsByDefault = MutableStateFlow(subtitlePrefs.getShowByDefault())
    val subsByDefault: StateFlow<Boolean> = _subsByDefault
    fun setSubsByDefault(enabled: Boolean) { subtitlePrefs.setShowByDefault(enabled); _subsByDefault.value = enabled }

    private val _preferAccuracy = MutableStateFlow(subtitlePrefs.getPreferLocalAccuracy())
    val preferAccuracy: StateFlow<Boolean> = _preferAccuracy
    fun setPreferAccuracy(enabled: Boolean) { subtitlePrefs.setPreferLocalAccuracy(enabled); _preferAccuracy.value = enabled }

    private val _newEpisodes = MutableStateFlow(notificationPreferences.isNewEpisodesEnabled())
    val newEpisodes: StateFlow<Boolean> = _newEpisodes
    fun setNewEpisodes(enabled: Boolean) { notificationPreferences.setNewEpisodesEnabled(enabled); _newEpisodes.value = enabled }

    private val timeoutOptions = listOf(10, 15, 20, 30)
    private val _timeoutSeconds = MutableStateFlow(networkPreferences.getTimeoutSeconds())
    val timeoutSeconds: StateFlow<Int> = _timeoutSeconds
    fun cycleTimeout() {
        val next = timeoutOptions.nextAfter(_timeoutSeconds.value)
        networkPreferences.setTimeoutSeconds(next)
        _timeoutSeconds.value = next
        applyNetworkTimeoutChange()
    }

    private val mobileQualityOptions = listOf("480p", "720p", "1080p", "Без ограничений")
    private val _maxMobileQuality = MutableStateFlow(qualityPrefs.getMaxQualityOnMobile())
    val maxMobileQuality: StateFlow<String> = _maxMobileQuality
    fun cycleMaxMobileQuality() {
        val next = mobileQualityOptions.nextAfter(_maxMobileQuality.value)
        qualityPrefs.setMaxQualityOnMobile(next)
        _maxMobileQuality.value = next
    }

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

    // Оверлей плеера (TV и телефон): тот же источник — InterfacePreferences.
    private val _playerOverlay = MutableStateFlow(interfacePreferences.playerOverlaySettings())
    val playerOverlay: StateFlow<PlayerOverlaySettings> = _playerOverlay

    fun setControlsHideDelayMs(ms: Int) { interfacePreferences.controlsHideDelayMs = ms; refreshOverlay() }
    fun setShowProgressStrip(enabled: Boolean) { interfacePreferences.showProgressStrip = enabled; refreshOverlay() }
    fun setProgressStripDp(dp: Int) { interfacePreferences.progressStripDp = dp; refreshOverlay() }
    fun setKeepControlsWhenPaused(enabled: Boolean) { interfacePreferences.keepControlsWhenPaused = enabled; refreshOverlay() }
    fun setShowBuffered(enabled: Boolean) { interfacePreferences.showBuffered = enabled; refreshOverlay() }
    fun setLiveArrowMode(mode: com.platinum.ott.core.LiveArrowMode) { interfacePreferences.liveArrowMode = mode; refreshOverlay() }
    fun setBackClosesAll(enabled: Boolean) { interfacePreferences.backClosesAll = enabled; refreshOverlay() }
    private fun refreshOverlay() { _playerOverlay.value = interfacePreferences.playerOverlaySettings() }

    // PROMPT_SETTINGS_UPGRADE.md п.5 — секции "Источники"/"Синхронизация"
    // стали навигационными строками со статусом вместо голого заголовка.
    // Источников уже может быть 0/1/много (PlaylistSource, не бинарный
    // AuthPreferences) — считаем реально сохранённые записи, не читаем
    // ничего из удалённого AuthRepository.
    private val _sourcesCount = MutableStateFlow(0)
    val sourcesCount: StateFlow<Int> = _sourcesCount

    // PROMPT_IPTV_FOUNDATION.md — тот же принцип, что и sourcesCount выше:
    // реально сохранённые записи (здесь — ПОДПИСАННЫЕ каналы, не все
    // импортированные, см. ChannelRepository.getSubscribedCount()), не
    // прогноз/кэш.
    private val _channelsCount = MutableStateFlow(0)
    val channelsCount: StateFlow<Int> = _channelsCount

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
            _channelsCount.value = sessionGraph.channelRepository.getSubscribedCount()
            _lastSyncedAtMs.value = authPreferences.lastSyncTimestamp
        }
    }
}
