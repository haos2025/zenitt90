package com.platinum.ott.presentation.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.domain.model.StreamVariant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState { object Loading : PlayerUiState; data class Ready(val variants: List<StreamVariant>, val currentVariant: StreamVariant, val showQualityMenu: Boolean = false) : PlayerUiState; data class Error(val message: String) : PlayerUiState }

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val getPlayableUrl = ServiceLocator.getPlayableUrlUseCase
    private val getMovie = ServiceLocator.getMovieByIdUseCase
    private val watchHistory = ServiceLocator.watchHistoryUseCase
    private val qualityPrefs = QualityPreferences(application)
    // Раньше ExoPlayer собирался с дефолтным ExoPlayer.Builder(application).build()
    // без кастомного HTTP data source — MediaItem.fromUri() уходил на сервер
    // с дефолтным User-Agent'ом ExoPlayer'а. Многие IPTV-панели (M3U/Xtream —
    // ровно то, о чём сообщили как о "http 404, ни каких кнопок") отклоняют
    // запросы без узнаваемого UA или блокируют кросс-протокольные редиректы
    // (http→https между балансировщиком и реальным CDN) — оба этих случая
    // теперь явно разрешены/обработаны.
    val exoPlayer: ExoPlayer = run {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("ZenithOTT/1.0 (Linux;Android) ExoPlayerLib/media3")
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(application).setDataSourceFactory(httpDataSourceFactory)
        ExoPlayer.Builder(application).setMediaSourceFactory(mediaSourceFactory).build()
    }
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState

    // Раньше PlayerScreen передавал useController=false и не выводил вообще
    // никакого UI управления — PlayerController.kt/QualityMenuOverlay.kt
    // лежали неиспользуемыми. isPlaying нужен PlayerController для иконки
    // play/pause; у ExoPlayer нет готового Flow под это, поэтому слушаем
    // через Player.Listener и прокидываем в StateFlow.
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    // WatchHistoryUseCase.saveProgress() существовал с самого начала, но
    // PlayerViewModel ни разу его не вызывал — история просмотра никогда
    // не записывалась, экран "История" всегда был пуст не из-за бага чтения,
    // а потому что писать было некому.
    private var currentMovieId: String = ""
    private var currentTitle: String = ""
    private var currentPoster: String? = null
    private var historyAutosaveJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                // Сохраняем сразу на паузу/остановку — это ловит выход
                // пользователя из плеера куда надёжнее, чем только
                // периодический автосейв (viewModelScope уже отменён к
                // моменту onCleared(), досохранить "последнюю точку" там
                // технически нельзя — это его компенсирует).
                if (!isPlaying) viewModelScope.launch { saveHistoryNow() }
            }
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
        currentMovieId = movieId
        historyAutosaveJob?.cancel()
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            try {
                val variants = getPlayableUrl.execute(movieId)
                if (variants.isEmpty()) { _uiState.value = PlayerUiState.Error("Нет потоков"); return@launch }

                // Название/постер нужны для записи в историю (WatchHistoryEntity
                // хранит их денормализованно, как и FavoriteEntity) — раньше
                // PlayerViewModel вообще не знал название фильма, только id.
                val movie = getMovie.execute(movieId).getOrNull()
                currentTitle = movie?.title ?: ""
                currentPoster = movie?.poster

                // "Продолжить N%" на DetailScreen показывал прогресс из истории,
                // но кнопка "Смотреть"/"Продолжить" вела в плеер без передачи
                // позиции — воспроизведение всегда начиналось с нуля, несмотря
                // на то что процент был показан честно. Теперь действительно
                // продолжает, если фильм не был досмотрен (completed == false).
                val existingHistory = watchHistory.getByContentId(movieId)
                val resumePositionMs = if (existingHistory != null && !existingHistory.completed) existingHistory.positionMs else 0L

                val saved = qualityPrefs.getSelectedQuality()
                val initial = variants.firstOrNull { it.quality == saved } ?: variants.first()
                playVariant(initial, resumePositionMs)
                _uiState.value = PlayerUiState.Ready(variants, initial)
                startHistoryAutosave()
            } catch (e: Exception) { _uiState.value = PlayerUiState.Error(e.message ?: "Ошибка") }
        }
    }

    private fun startHistoryAutosave() {
        historyAutosaveJob = viewModelScope.launch {
            while (true) {
                delay(10_000)
                saveHistoryNow()
            }
        }
    }

    private suspend fun saveHistoryNow() {
        if (currentMovieId.isEmpty()) return
        val duration = exoPlayer.duration
        if (duration <= 0) return // длительность ещё не определена — нечего сохранять
        watchHistory.saveProgress(currentMovieId, currentTitle, currentPoster, exoPlayer.currentPosition, duration)
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
    override fun onCleared() {
        super.onCleared()
        historyAutosaveJob?.cancel()
        exoPlayer.release()
    }
}
