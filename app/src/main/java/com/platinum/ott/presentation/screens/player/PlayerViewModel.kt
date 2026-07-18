package com.platinum.ott.presentation.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.domain.model.StreamVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState { object Loading : PlayerUiState; data class Ready(val variants: List<StreamVariant>, val currentVariant: StreamVariant, val showQualityMenu: Boolean = false) : PlayerUiState; data class Error(val message: String) : PlayerUiState }

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val getPlayableUrl = ServiceLocator.getPlayableUrlUseCase
    private val qualityPrefs = QualityPreferences(application)
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build()
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState

    // Раньше PlayerScreen передавал useController=false и не выводил вообще
    // никакого UI управления — PlayerController.kt/QualityMenuOverlay.kt
    // лежали неиспользуемыми. isPlaying нужен PlayerController для иконки
    // play/pause; у ExoPlayer нет готового Flow под это, поэтому слушаем
    // через Player.Listener и прокидываем в StateFlow.
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }
        })
    }

    fun togglePlayPause() { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
    fun seekForward() {
        val target = exoPlayer.currentPosition + 10_000
        val duration = exoPlayer.duration
        exoPlayer.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }
    fun seekBackward() { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) }

    fun loadMovie(movieId: String) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            try {
                val variants = getPlayableUrl.execute(movieId)
                if (variants.isEmpty()) { _uiState.value = PlayerUiState.Error("Нет потоков"); return@launch }
                val saved = qualityPrefs.getSelectedQuality()
                val initial = variants.firstOrNull { it.quality == saved } ?: variants.first()
                playVariant(initial)
                _uiState.value = PlayerUiState.Ready(variants, initial)
            } catch (e: Exception) { _uiState.value = PlayerUiState.Error(e.message ?: "Ошибка") }
        }
    }

    fun selectQuality(variant: StreamVariant) {
        val current = _uiState.value as? PlayerUiState.Ready ?: return
        playVariant(variant, exoPlayer.currentPosition)
        qualityPrefs.setSelectedQuality(variant.quality)
        _uiState.value = current.copy(currentVariant = variant, showQualityMenu = false)
    }
    fun toggleQualityMenu() { val c = _uiState.value as? PlayerUiState.Ready ?: return; _uiState.value = c.copy(showQualityMenu = !c.showQualityMenu) }
    fun dismissQualityMenu() { val c = _uiState.value as? PlayerUiState.Ready ?: return; if (c.showQualityMenu) { _uiState.value = c.copy(showQualityMenu = false); exoPlayer.play() } }
    private fun playVariant(v: StreamVariant, seekTo: Long = 0L) { exoPlayer.setMediaItem(MediaItem.fromUri(v.url)); exoPlayer.prepare(); if (seekTo > 0) exoPlayer.seekTo(seekTo); exoPlayer.play() }
    override fun onCleared() { super.onCleared(); exoPlayer.release() }
}
