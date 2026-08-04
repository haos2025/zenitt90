package com.platinum.ott.presentation.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
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

sealed interface PlayerUiState { object Loading : PlayerUiState; data class Ready(val variants: List<StreamVariant>, val currentVariant: StreamVariant, val title: String = "", val showQualityMenu: Boolean = false) : PlayerUiState; data class Error(val message: String) : PlayerUiState }

@OptIn(UnstableApi::class)
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
    //
    // httpDataSourceFactory хранится ПОЛЕМ (не только внутри run{}) — раньше
    // User-Agent был один статический на все каналы сразу. Многие M3U-каналы
    // требуют СВОЙ заголовок (#EXTVLCOPT:http-user-agent=.../http-referrer=...
    // из плейлиста, см. M3uPlaylistParser) — playVariant() теперь
    // перевыставляет defaultRequestProperties под конкретный канал перед
    // каждым воспроизведением.
    private val defaultHeaders = mapOf("User-Agent" to "ZenithOTT/1.0 (Linux;Android) ExoPlayerLib/media3")
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(defaultHeaders)
        .setAllowCrossProtocolRedirects(true)
    val exoPlayer: ExoPlayer = run {
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

            // Раньше слушался только onIsPlayingChanged — реальные ошибки
            // ExoPlayer (HTTP 404/403 при попытке получить поток, не при
            // резолвинге ссылки) вообще никогда не долетали до
            // PlayerUiState.Error, потому что на них никто не был подписан.
            // Экран просто зависал без плеера и без единой кнопки — именно
            // то, что было описано как баг ("http 404, ни каких кнопок").
            override fun onPlayerError(error: PlaybackException) {
                val current = _uiState.value as? PlayerUiState.Ready
                val currentIndex = current?.variants?.indexOf(current.currentVariant) ?: -1
                val nextVariant = current?.variants?.getOrNull(currentIndex + 1)
                if (current != null && nextVariant != null) {
                    // Текущий источник не проигрался — пробуем следующий по
                    // списку автоматически (гибридная гонка backend/плагины
                    // уже может дать несколько вариантов на один фильм),
                    // прежде чем сдаваться и показывать ошибку целиком.
                    playVariant(nextVariant, 0L)
                    _uiState.value = current.copy(currentVariant = nextVariant)
                } else {
                    _uiState.value = PlayerUiState.Error(describePlaybackError(error))
                }
            }
        })
    }

    /**
     * Раньше error.message от ExoPlayer часто был неинформативным техническим
     * текстом. HttpDataSource.InvalidResponseCodeException внутри cause-цепочки
     * содержит реальный HTTP-код ответа сервера — вытаскиваем его явно, чтобы
     * в UI было видно "HTTP 404", а не общее "ошибка воспроизведения".
     */
    private fun describePlaybackError(error: PlaybackException): String {
        val httpCause = generateSequence(error as Throwable) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
        return if (httpCause != null) {
            "Сервер вернул HTTP ${httpCause.responseCode} — ссылка недоступна"
        } else {
            error.message ?: "Не удалось воспроизвести поток"
        }
    }

    fun togglePlayPause() { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
    fun seekForward() {
        val target = exoPlayer.currentPosition + 10_000
        val duration = exoPlayer.duration
        exoPlayer.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
    }
    fun seekBackward() { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) }
    // Нужен для перетаскивания слайдера прогресса на телефоне — на TV его
    // не было, там перемотка только шагами по 10с с пульта.
    fun seekTo(positionMs: Long) {
        val duration = exoPlayer.duration
        exoPlayer.seekTo(if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0))
    }

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
                val preferred = variants.firstOrNull { it.quality == saved } ?: variants.first()
                // "Макс. качество на моб." (QualityPreferences.getMaxQualityOnMobile)
                // существовал в коде с самого начала, но нигде не читался — экран
                // настроек показывал захардкоженное "720p", которое ни на что не
                // влияло. Теперь реально ограничивает СТАРТОВОЕ качество, если
                // активное соединение метровое (мобильные данные/раздача Wi-Fi),
                // не трогая ручной выбор пользователя в плеере после старта.
                val initial = capForMeteredNetwork(variants, preferred)
                playVariant(initial, resumePositionMs)
                _uiState.value = PlayerUiState.Ready(variants, initial, currentTitle)
                startHistoryAutosave()
            } catch (e: Exception) { _uiState.value = PlayerUiState.Error(e.message ?: "Ошибка") }
        }
    }

    private val qualityRankOrder = listOf("240p", "360p", "480p", "720p", "1080p", "1440p", "2160p")
    private fun isOnMeteredConnection(): Boolean {
        val cm = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
    private fun capForMeteredNetwork(variants: List<StreamVariant>, chosen: StreamVariant): StreamVariant {
        val cap = qualityPrefs.getMaxQualityOnMobile()
        if (cap == "Без ограничений" || !isOnMeteredConnection()) return chosen
        val capRank = qualityRankOrder.indexOf(cap)
        val chosenRank = qualityRankOrder.indexOf(chosen.quality)
        // Неизвестная метка качества (не из стандартного списка, например
        // произвольная метка из плагина) — не трогаем, лучше оставить выбор
        // как есть, чем сломать воспроизведение неверным сравнением.
        if (capRank == -1 || chosenRank == -1 || chosenRank <= capRank) return chosen
        return variants.filter { qualityRankOrder.indexOf(it.quality) in 0..capRank }
            .maxByOrNull { qualityRankOrder.indexOf(it.quality) } ?: chosen
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
    private fun playVariant(v: StreamVariant, seekTo: Long = 0L) {
        // Свои заголовки канала (#EXTVLCOPT) поверх общего дефолта — если
        // канал ничего не требует явно, edge-case не ломается, просто
        // используется тот же generic User-Agent, что и раньше.
        val effectiveHeaders = if (v.headers.isNotEmpty()) defaultHeaders + v.headers else defaultHeaders
        httpDataSourceFactory.setDefaultRequestProperties(effectiveHeaders)
        exoPlayer.setMediaItem(MediaItem.fromUri(v.url))
        exoPlayer.prepare()
        if (seekTo > 0) exoPlayer.seekTo(seekTo)
        exoPlayer.play()
    }
    override fun onCleared() {
        super.onCleared()
        historyAutosaveJob?.cancel()
        exoPlayer.release()
    }
}
