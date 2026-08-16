package com.platinum.ott.presentation.screens.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.platinum.ott.core.QualityPreferences
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.core.SubtitlePreferences
import com.platinum.ott.domain.model.StreamVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlaybackMenuTab { QUALITY, AUDIO, SUBTITLES, SPEED }

// Ссылку на TrackGroup держим напрямую (а не индекс) — это обычный
// immutable value-класс Media3, живёт ровно один снимок Tracks; каждый раз
// список опций строится заново из СВЕЖЕГО exoPlayer.currentTracks в
// onTracksChanged, так что устаревшей ссылки быть не может.
data class TrackOption(val trackGroup: TrackGroup, val trackIndexInGroup: Int, val label: String, val isSelected: Boolean)

sealed interface PlayerUiState {
    object Loading : PlayerUiState
    data class Ready(
        val variants: List<StreamVariant>,
        val currentVariant: StreamVariant,
        val title: String = "",
        val showPlaybackMenu: Boolean = false,
        val menuTab: PlaybackMenuTab = PlaybackMenuTab.QUALITY,
        val audioTracks: List<TrackOption> = emptyList(),
        val subtitleTracks: List<TrackOption> = emptyList(),
        val subtitlesEnabled: Boolean = false,
        val playbackSpeed: Float = 1f,
        // Убрали перемотку на ±10с из панели управления при просмотре
        // сериала — при линейном последовательном просмотре следующий/
        // предыдущий эпизод нужнее чаще, чем перемотка внутри одной серии
        // (сама перемотка никуда не делась: остаётся на D-pad Left/Right на
        // TV, свайпе прогресс-бара на телефоне). null у обоих — контент не
        // сериал (обычный фильм, или единственная известная серия), тогда
        // остаются кнопки перемотки, см. PlayerController.kt/PhonePlayerController.kt.
        val nextEpisodeId: String? = null,
        val previousEpisodeId: String? = null
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

// AndroidViewModel, а не обычный ViewModel — Application нужен напрямую
// (ConnectivityManager для isOnMeteredConnection(), ExoPlayer.Builder).
// Hilt поддерживает это "из коробки": Application доступен для инъекции в
// @HiltViewModel без дополнительных квалификаторов, специальный ViewModel-
// компонент Hilt даёт его сам.
@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    sessionGraph: SessionGraph
) : AndroidViewModel(application) {
    private val getPlayableUrl = sessionGraph.getPlayableUrlUseCase
    private val getMovie = sessionGraph.getMovieByIdUseCase
    private val watchHistory = sessionGraph.watchHistoryUseCase
    // Next/Previous episode (см. loadMovie()) — переиспользует тот же
    // источник, что и SeriesEpisodesViewModel (уже сортирует по
    // seasonNumber/episodeNumber), отдельного use case заводить не стали
    // ради одного вызова.
    private val playlistRepository = sessionGraph.playlistRepository
    private val qualityPrefs = QualityPreferences(application)
    // "Показывать субтитры по умолчанию" (см. loadMovie()) — раньше эта
    // настройка была убрана из SettingsScreen.kt как выдуманная под
    // несуществующую фичу; теперь субтитры реально поддерживаются
    // (TrackOption/selectSubtitleTrack), настройка снова осмысленна.
    private val subtitlePrefs = SubtitlePreferences(application)
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
        // Некоторые IPTV-каналы кодируют звук в AC-3/E-AC-3 (Dolby Digital) —
        // это лицензированный кодек, штатный MediaCodec большинства Android-
        // устройств его не поддерживает: видео идёт, звука нет, без ошибки
        // в логах. EXTENSION_RENDERER_MODE_PREFER готовит плеер использовать
        // decoder-расширение (например FFmpeg) вместо платформенного, КОГДА
        // такое расширение подключено в проект — само по себе оно звук не
        // чинит, FFmpeg-модуль для Media3 не публикуется в Maven Central
        // (юридические причины), собирается локально через NDK отдельным
        // шагом сборки, которого в проекте пока нет.
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(application)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        ExoPlayer.Builder(application, renderersFactory).setMediaSourceFactory(mediaSourceFactory).build()
    }
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState

    // Раньше PlayerScreen передавал useController=false и не выводил вообще
    // никакого UI управления — PlayerController.kt/PlaybackMenuOverlay.kt
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

            // Раньше аудиодорожки и субтитры вообще нигде не читались —
            // ExoPlayer сам автоматически выбирал первую подходящую
            // дорожку, переключить вручную было нельзя. onTracksChanged
            // вызывается Media3 на каждую смену состава дорожек (новый
            // MediaItem, ответ HLS-манифеста и т.п.) — пересобираем список
            // опций из АКТУАЛЬНОГО снимка, не храним ничего между вызовами.
            override fun onTracksChanged(tracks: Tracks) {
                val current = _uiState.value as? PlayerUiState.Ready ?: return
                val audio = mutableListOf<TrackOption>()
                val subtitles = mutableListOf<TrackOption>()
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        if (!group.isTrackSupported(i)) continue
                        val format = group.getTrackFormat(i)
                        val label = format.label ?: format.language?.uppercase() ?: "Дорожка ${i + 1}"
                        val option = TrackOption(group.mediaTrackGroup, i, label, group.isTrackSelected(i))
                        when (group.type) {
                            C.TRACK_TYPE_AUDIO -> audio += option
                            C.TRACK_TYPE_TEXT -> subtitles += option
                            else -> {}
                        }
                    }
                }
                _uiState.value = current.copy(
                    audioTracks = audio,
                    subtitleTracks = subtitles,
                    subtitlesEnabled = tracks.isTypeSelected(C.TRACK_TYPE_TEXT)
                )
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
        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
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
                // "Показывать субтитры по умолчанию" (Настройки →
                // Воспроизведение) — не даёт стопроцентной гарантии, что
                // ExoPlayer сразу выберет конкретную дорожку (это по-прежнему
                // системное поведение DefaultTrackSelector/CaptioningManager,
                // если явно не выбрать трек через selectSubtitleTrack()), но
                // явно РАЗРЕШАЕТ или ЗАПРЕЩАЕТ тип трека — реальный переключатель,
                // а не фиктивный, как было убрано из настроек раньше.
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlePrefs.getShowByDefault())
                    .build()
                playVariant(initial, resumePositionMs)

                // "Следующий/предыдущий эпизод" вместо перемотки в панели
                // управления (просили заменить кнопки ±10с при просмотре
                // сериала) — раньше в проекте вообще не было понятия
                // "следующий эпизод" нигде (см. старый комментарий в
                // SettingsScreen.kt про несуществующие настройки). movie.seriesId
                // — тот же признак, что уже использует SeriesEpisodesScreen.
                var nextEpisodeId: String? = null
                var previousEpisodeId: String? = null
                if (movie?.seriesId != null) {
                    try {
                        val episodes = playlistRepository.getEpisodesForSeries(movie.seriesId)
                        val idx = episodes.indexOfFirst { it.id == movieId }
                        if (idx >= 0) {
                            previousEpisodeId = episodes.getOrNull(idx - 1)?.id
                            nextEpisodeId = episodes.getOrNull(idx + 1)?.id
                        }
                    } catch (_: Exception) { /* не сериал/плейлист недоступен — оставляем перемотку как есть */ }
                }

                _uiState.value = PlayerUiState.Ready(variants, initial, currentTitle, nextEpisodeId = nextEpisodeId, previousEpisodeId = previousEpisodeId)
                startHistoryAutosave()
            } catch (e: Exception) { _uiState.value = PlayerUiState.Error(e.message ?: "Ошибка") }
        }
    }

    // loadMovie() уже сохраняет позицию/переинициализирует всё нужное —
    // следующий/предыдущий эпизод переиспользуют его целиком, а не
    // отдельный облегчённый путь, чтобы история просмотра/качество/
    // субтитры вели себя одинаково что при обычном открытии, что при
    // переключении серии.
    fun playNextEpisode() {
        val id = (_uiState.value as? PlayerUiState.Ready)?.nextEpisodeId ?: return
        loadMovie(id)
    }

    fun playPreviousEpisode() {
        val id = (_uiState.value as? PlayerUiState.Ready)?.previousEpisodeId ?: return
        loadMovie(id)
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
        _uiState.value = current.copy(currentVariant = variant, showPlaybackMenu = false)
    }
    fun togglePlaybackMenu() { val c = _uiState.value as? PlayerUiState.Ready ?: return; _uiState.value = c.copy(showPlaybackMenu = !c.showPlaybackMenu) }
    fun dismissPlaybackMenu() { val c = _uiState.value as? PlayerUiState.Ready ?: return; if (c.showPlaybackMenu) { _uiState.value = c.copy(showPlaybackMenu = false); exoPlayer.play() } }
    fun setMenuTab(tab: PlaybackMenuTab) { val c = _uiState.value as? PlayerUiState.Ready ?: return; _uiState.value = c.copy(menuTab = tab) }

    // pitch всегда 1f, независимо от speed — PlaybackParameters(speed, pitch)
    // это два независимых параметра Media3, SonicAudioProcessor умеет
    // растягивать/сжимать время БЕЗ изменения тона. Без этого голоса на
    // 1.5x звучали бы писклявее ("эффект бурундука") — для видео это почти
    // всегда нежелательно, в отличие от аудиокниг/подкастов.
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed, 1f)
        val current = _uiState.value as? PlayerUiState.Ready ?: return
        _uiState.value = current.copy(playbackSpeed = speed)
    }

    fun selectAudioTrack(option: TrackOption) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndexInGroup))
            .build()
    }
    fun selectSubtitleTrack(option: TrackOption) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(option.trackGroup, option.trackIndexInGroup))
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }
    // "Выключить субтитры" — не то же самое, что просто не выбирать
    // дорожку: ExoPlayer по умолчанию сам может включить подходящую по
    // системному языку, если явно не запретить тип трека.
    fun disableSubtitles() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private var externalSubtitleUrl: String? = null
    // Внешние SRT по ссылке — раньше субтитры могли быть только те, что
    // зашиты в сам поток/контейнер. SubtitleConfiguration можно приложить
    // только при сборке MediaItem, а не добавить "на лету" в уже играющий
    // поток — поэтому пересобираем текущий вариант через playVariant(),
    // сохраняя позицию воспроизведения.
    fun loadExternalSubtitle(url: String) {
        externalSubtitleUrl = url
        val current = _uiState.value as? PlayerUiState.Ready ?: return
        playVariant(current.currentVariant, exoPlayer.currentPosition)
    }
    private fun playVariant(v: StreamVariant, seekTo: Long = 0L) {
        // Свои заголовки канала (#EXTVLCOPT) поверх общего дефолта — если
        // канал ничего не требует явно, edge-case не ломается, просто
        // используется тот же generic User-Agent, что и раньше.
        var effectiveHeaders = if (v.headers.isNotEmpty()) defaultHeaders + v.headers else defaultHeaders
        // ИСПРАВЛЕНО: раньше здесь стояла проверка `v.source == "Zenith"` —
        // но source="Zenith" стоит у ЛЮБОГО backend-варианта, и у yt_, и у
        // ia_ (см. GetPlayableUrlUseCase.executeWithPluginRace — оба
        // префикса дают один и тот же source). Реально Archive.org — это
        // ТОЛЬКО ia_. Добавление Referer archive.org к yt_-вариантам,
        // которые к Archive.org никакого отношения не имеют, могло сбивать
        // их собственные CDN (часть проверяет Referer на конкретный домен
        // и не ожидает чужой) — правдоподобное объяснение репорта "один
        // раз сработало, потом опять сломалось": скорее всего сработавший
        // раз был ia_, а сломавшийся — yt_ с другим сервером. Проверяем
        // по фактическому хосту URL, не по source.
        if (v.url.contains("archive.org", ignoreCase = true)) {
            effectiveHeaders = effectiveHeaders + ("Referer" to "https://archive.org/")
        }
        httpDataSourceFactory.setDefaultRequestProperties(effectiveHeaders)
        val mediaItemBuilder = MediaItem.Builder().setUri(v.url)
        // ВТОРАЯ гипотеза той же природы: ExoPlayer определяет контейнер по
        // расширению в URL (Util.inferContentType) — рабочие М3У/Xtream-
        // ссылки почти всегда оканчиваются на .m3u8/.mp4/.ts явно, а
        // backend/Archive.org иногда отдаёт ссылку на файл без узнаваемого
        // расширения (редирект/API-эндпоинт). Без подсказки MIME-типа
        // ExoPlayer в этом случае либо падает с ошибкой (тогда её увидит
        // onPlayerError), либо в редких случаях зависает на буферизации
        // без явной ошибки — снаружи выглядит как "плеер открылся, видео
        // нет". Явный video/mp4 не трогает случаи, где расширение уже
        // узнаётся (в т.ч. HLS-плейлисты М3У/Xtream — там ветка не сработает).
        if (v.source == "Zenith" && !hasRecognizableMediaExtension(v.url)) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }
        externalSubtitleUrl?.let { subUrl ->
            mediaItemBuilder.setSubtitleConfigurations(listOf(
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("ru")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            ))
        }
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        if (seekTo > 0) exoPlayer.seekTo(seekTo)
        exoPlayer.play()
    }
    private fun hasRecognizableMediaExtension(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return listOf(".mp4", ".m3u8", ".mkv", ".webm", ".ts", ".mov", ".avi", ".ogv")
            .any { path.endsWith(it, ignoreCase = true) }
    }

    override fun onCleared() {
        super.onCleared()
        historyAutosaveJob?.cancel()
        exoPlayer.release()
    }
}
