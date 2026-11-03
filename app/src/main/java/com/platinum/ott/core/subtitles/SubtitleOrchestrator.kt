package com.platinum.ott.core.subtitles

import android.util.Log
import com.platinum.ott.data.remote.dto.SttTranscriptionDto
import com.platinum.ott.data.repository.CloudSttRepository
import com.platinum.ott.domain.model.SubtitleFormat
import com.platinum.ott.domain.usecase.SearchOpenSubtitlesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PROMPT_SUBTITLES.md, подзадача 6 — оркестратор. Связывает всё, что
 * сделано в подзадачах 1-5, в единый механизм с приоритетом источников
 * ровно как в самом промте:
 *
 *   VOD: OpenSubtitles (подзадача 1) → если не нашлось → облачный STT
 *   (подзадача 3) со скользящим окном 5 минут вперёд. Локального Whisper
 *   больше нет: если облако исчерпано (backend вернул 503, см.
 *   CloudSttRepository), показываем ошибку и останавливаем пайплайн.
 *
 * Специально НЕ знает про ExoPlayer/PlayerViewModel/Compose — координирует
 * только источники субтитров, а найденный OpenSubtitles-матч отдаёт через
 * колбэк [start]'а (сам `loadExternalSubtitle` — дело PlayerViewModel, он
 * уже реализован в подзадаче 1).
 *
 * ЖИВЫЕ КАНАЛЫ НЕ РЕАЛИЗОВАНЫ — открытый вопрос ещё с подзадачи 2:
 * `AudioExtractionRequest.startPositionMs` осмыслен только для VOD с
 * известной длительностью; для live нет фиксированного таймлайна, есть
 * live edge, и это не проверено на реальном потоке. [start] предполагает
 * VOD (принимает `contentDurationMs`); для живых каналов нужен отдельный
 * путь, которого здесь нет.
 *
 * КАЖДЫЙ ЧАНК ДЕКОДИРУЕТСЯ В PCM ДВАЖДЫ (известная неэффективность): один
 * раз внутри SileroVadSegmenter, один раз здесь (для нарезки WAV под облако).
 */
class SubtitleOrchestrator(
    private val audioExtractor: StreamAudioExtractor,
    private val vadSegmenter: SileroVadSegmenter,
    private val openSubtitlesUseCase: SearchOpenSubtitlesUseCase,
    private val cloudSttRepository: CloudSttRepository,
    private val cueCache: SubtitleCueCache? = null,
) {
    private val _state = MutableStateFlow<AutoSubtitleState>(AutoSubtitleState.Off)
    val state: StateFlow<AutoSubtitleState> = _state

    // Накопленные AI-сгенерированные подписи (когда OpenSubtitles не нашёлся)
    // — в отличие от OpenSubtitles-пути (готовый файл, ExoPlayer рендерит
    // сам), это прогрессивно растущий список: рендерить его в самом плеере
    // (подбирать текущую реплику по currentPosition) — дело подзадачи 8,
    // здесь только источник данных.
    private val _cues = MutableStateFlow<List<SubtitleCue>>(emptyList())
    val cues: StateFlow<List<SubtitleCue>> = _cues

    private var job: Job? = null

    // Кэш распознанного (см. SubtitleCueCache): ключ текущего видео и уже покрытые куски [start, end).
    private var cacheKey: String? = null
    private var coveredRanges: List<LongRange> = emptyList()
    // Окно распознано без сбоев — только такие окна считаются покрытыми.
    private var windowOk = true
    // Диагностика кэша для meta: nokey / miss / hit.
    private var cacheStatus = "nokey"
    // Сглаженное отношение «сколько реально шло извлечение звука окна / длина окна». Нужно, чтобы
    // на медленном источнике начинать окно не с текущей позиции (оно будет готово, когда эта часть
    // уже сыграна), а с запасом на время извлечения — тогда реплики приходят вовремя.
    private var extractRatio = -1.0
    // Сколько реально шло извлечение предзагруженного окна (с момента запуска, а не ожидания).
    @Volatile private var prefetchTookMs = 0L

    /**
     * @param contentDurationMs полная длительность VOD-контента — окно не
     * должно вылезать за конец файла.
     * @param currentPositionMsProvider вызывается каждый цикл опроса, а не
     * один раз — окно "сдвигается по мере просмотра", а не считается один
     * раз в начале (PROMPT_SUBTITLES.md).
     * @param onOpenSubtitlesFound см. докстринг класса — единственная точка
     * связи с ExoPlayer, вызывается на потоке [scope].
     */
    fun start(
        scope: CoroutineScope,
        title: String,
        year: Int,
        streamUrl: String,
        headers: Map<String, String>,
        contentDurationMs: Long,
        currentPositionMsProvider: () -> Long,
        language: String = "ru",
        onOpenSubtitlesFound: (url: String, language: String, format: SubtitleFormat) -> Unit,
    ) {
        stop()
        _cues.value = emptyList()
        cacheKey = null
        coveredRanges = emptyList()
        cacheStatus = "nokey"
        extractRatio = -1.0
        job = scope.launch {
            // Будим сервер заранее: пока ищем OpenSubtitles и извлекаем звук первого окна, он просыпается.
            launch { cloudSttRepository.warmUp() }
            // Без года (внешний плеер: только имя файла из ссылки) поиск по OpenSubtitles
            // бессмыслен — сразу генерируем.
            val match = if (year > 0) {
                _state.value = AutoSubtitleState.SearchingOpenSubtitles
                runCatching { openSubtitlesUseCase.execute(title, year) }.getOrNull()
            } else null
            if (match != null) {
                _state.value = AutoSubtitleState.UsingOpenSubtitles(match.language)
                onOpenSubtitlesFound(match.downloadUrl, match.language, match.format)
                return@launch
            }
            // Уже распознанное для этого видео (прошлый просмотр) показываем сразу.
            val key = cueCache?.key(title, year, contentDurationMs, language)
            cacheKey = key
            if (key != null) cacheStatus = "miss"
            if (key != null) {
                val cached = withContext(Dispatchers.IO) { cueCache?.load(key) }
                if (cached != null && cached.cues.isNotEmpty()) {
                    _cues.value = cached.cues.sortedBy { it.startMs }
                    coveredRanges = cached.covered
                    cacheStatus = "hit"
                    progressLabel = "кэш"
                    setProgress("загружено")
                }
            }
            runAiPipelineForVod(streamUrl, headers, contentDurationMs, currentPositionMsProvider, language)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = AutoSubtitleState.Off
        _cues.value = emptyList()
        cacheKey = null
        coveredRanges = emptyList()
    }

    /**
     * Откуда начинать окно, чтобы оно было готово к моменту, когда просмотр до него дойдёт.
     * Только облегчённый путь и только когда уже известна скорость извлечения. Если за время
     * извлечения просмотр всё равно уйдёт заметно дальше [base] (отставание от плеера), берём
     * позицию + ожидаемое время извлечения: пропускаем кусок, который всё равно окажется «в
     * прошлом» к готовности. Пропуск разовый: после него окна идут подряд, пока скорость ≥ просмотра.
     */
    private fun liveStart(base: Long, position: Long, windowLen: Long): Long {
        if (!LIGHT_PIPELINE || extractRatio < 0) return base
        val est = minOf((extractRatio * windowLen).toLong(), MAX_SKIP_MS) + 2_000L
        val live = position + est
        return if (live - base >= SKIP_MIN_MS) live else base
    }

    /** Куски, уже распознанные раньше: перепрыгиваем их, а новое окно обрезаем перед следующим из них. */
    private fun skipCovered(pos: Long): Long {
        var p = pos
        while (true) {
            val r = coveredRanges.firstOrNull { p >= it.first && p <= it.last } ?: return p
            p = r.last + 1
        }
    }

    private fun capToCovered(start: Long, duration: Long): Long {
        val nextStart = coveredRanges.filter { it.first > start }.minOfOrNull { it.first } ?: return duration
        return minOf(duration, nextStart - start)
    }

    /** Помечает [start, end) покрытым и сохраняет реплики в кэш (если окно прошло без сбоев). */
    private suspend fun recordCovered(start: Long, end: Long) {
        if (!windowOk || end <= start) return
        val merged = mutableListOf<LongRange>()
        for (r in (coveredRanges + listOf(start until end)).sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1_000) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
            } else {
                merged += r
            }
        }
        coveredRanges = merged
        val key = cacheKey ?: return
        val snapshot = SubtitleCueCache.Entry(_cues.value, merged)
        withContext(Dispatchers.IO) { cueCache?.save(key, snapshot) }
    }

    private suspend fun runAiPipelineForVod(
        streamUrl: String,
        headers: Map<String, String>,
        contentDurationMs: Long,
        currentPositionMsProvider: () -> Long,
        language: String,
    ) {
        coroutineScope {
            var coveredUpToMs = 0L
            var windowIndex = 0
            // Предзагрузка: пока текущее окно распознаётся (в основном ожидание ответов сети),
            // следующее уже извлекается из потока. Раньше шаги шли строго друг за другом:
            // окно 2 минут занимало ~100 с (≈70 с звук + ≈27 с запросы) и пайплайн едва
            // обгонял просмотр, из-за чего в первые минуты были пропуски в субтитрах.
            var prefetch: Deferred<Result<ExtractedAudioChunk>>? = null
            var prefetchStartMs = -1L
            var prefetchDurationMs = 0L

            while (isActive && coveredUpToMs < contentDurationMs) {
                val position = currentPositionMsProvider().coerceAtLeast(0L)
                // Открыли с позиции или перемотали вперёд — пропущенный кусок не обрабатываем
                // (раньше первое окно тянулось от нуля до позиции+5 минут одним куском).
                if (position > coveredUpToMs) coveredUpToMs = position
                // Предзагрузка, которая уже идёт с позиции около текущей, не выбрасываем: если
                // извлечение первого окна заняло дольше его длины, воспроизведение успело уйти вперёд,
                // и раньше такая предзагрузка отменялась из-за расхождения в секунды (вся работа зря).
                // Берём её, пока она ещё покрывает хотя бы MIN_PREFETCH_MS будущего звука.
                if (prefetch != null) {
                    val pEnd = prefetchStartMs + prefetchDurationMs
                    // Предзагрузка может начинаться чуть позже конца прошлого окна — это сознательный
                    // пропуск вперёд для медленного источника (см. extractRatio), её тоже берём.
                    val adoptable = if (prefetchStartMs >= coveredUpToMs) {
                        prefetchStartMs - coveredUpToMs <= MAX_SKIP_MS + 5_000
                    } else {
                        pEnd >= coveredUpToMs + MIN_PREFETCH_MS
                    }
                    if (adoptable) coveredUpToMs = prefetchStartMs
                }
                // Кусок уже распознан раньше (кэш) — перепрыгиваем.
                val skipped = skipCovered(coveredUpToMs)
                if (skipped != coveredUpToMs) {
                    coveredUpToMs = skipped
                    continue
                }
                val target = position + WINDOW_AHEAD_MS
                // Когда запас вперёд уже у потолка (5 минут), раньше брали окна по 1–5 с и слали по
                // запросу каждые ~2 с (≈26 в минуту: впритык к лимиту, треть мелких кусков Whisper
                // «выдумывал» и фильтр их отбрасывал). Теперь ждём, пока набежит хотя бы
                // MIN_REFILL_MS, и берём нормальное окно. Хвост видео (конец) берём как есть.
                val reachable = minOf(target, contentDurationMs)
                val needed = reachable - coveredUpToMs
                val atTail = reachable >= contentDurationMs
                if (needed <= 0 || (needed < MIN_REFILL_MS && !atTail)) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val baseStart = coveredUpToMs
                val pending = prefetch
                prefetch = null
                val usePrefetch = pending != null && prefetchStartMs == baseStart
                // Окно без предзагрузки на медленном источнике: начинаем с запасом на время извлечения.
                val windowStart = if (usePrefetch) baseStart else skipCovered(liveStart(baseStart, position, windowLengthMs(windowIndex)))
                // Окна растут: 30 с → 60 с → 120 с. Первое короткое — первые реплики появляются
                // быстрее, на слабой приставке не декодируем сразу много минут.
                val windowDuration = if (usePrefetch) {
                    prefetchDurationMs
                } else {
                    capToCovered(windowStart, minOf(target, windowStart + windowLengthMs(windowIndex), contentDurationMs) - windowStart)
                }
                if (windowDuration <= 0) {
                    pending?.cancel()
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                val windowEnd = windowStart + windowDuration
                windowIndex++

                // Раньше до первого запроса к облаку состояние оставалось «Выкл», и было не видно,
                // что обработка вообще запущена. Теперь пользователь видит «Распознаём…» сразу.
                progressLabel = "окно $windowIndex"
                windowOk = true
                setProgress("звук")
                val t0 = System.currentTimeMillis()
                Log.d(TAG, "window #$windowIndex $windowStart..$windowEnd ms, prefetched=$usePrefetch")
                // Не использованную предзагрузку (пользователь перемотал) отменяем; уже созданный
                // ею временный файл остаётся в кэше приложения (до пары МБ, чистится системой).
                val chunkResult = if (usePrefetch && pending != null) {
                    pending.await()
                } else {
                    pending?.cancel()
                    audioExtractor.extract(AudioExtractionRequest(streamUrl, headers, windowStart, windowDuration))
                }
                Log.d(TAG, "window #$windowIndex audio ready after ${System.currentTimeMillis() - t0} ms")
                // Kotlin 2.1.20 (проект ещё не на 2.2) не разрешает non-local
                // continue внутри инлайн-лямбды (getOrElse{}) — поэтому явная проверка Result.
                if (chunkResult.isFailure) {
                    Log.w(TAG, "extract failed", chunkResult.exceptionOrNull())
                    _state.value = AutoSubtitleState.Error("Не удалось извлечь аудио: ${chunkResult.exceptionOrNull()?.message}")
                    coveredUpToMs = windowEnd // не зацикливаемся вечно на одном и том же неудачном окне
                    continue
                }
                val chunk = chunkResult.getOrThrow()
                setProgress("звук готов за ${(System.currentTimeMillis() - t0) / 1000} с")
                // Диагностика для логов Render (поле meta): сколько заняло извлечение звука, насколько
                // окно опережает просмотр и откуда поток. Без токенов и путей — только тип и хост.
                val extractMs = System.currentTimeMillis() - t0
                val leadMs = windowStart - currentPositionMsProvider()
                val srcTag = sourceTag(streamUrl)
                // Реальное время извлечения: для предзагруженного окна extractMs — лишь остаток ожидания.
                val tookMs = if (usePrefetch && prefetchTookMs > 0) maxOf(prefetchTookMs, extractMs) else extractMs
                val ratio = (tookMs.toDouble() / windowDuration).coerceIn(0.0, 3.0)
                extractRatio = if (extractRatio < 0) ratio else 0.5 * extractRatio + 0.5 * ratio

                // Запускаем извлечение следующего окна параллельно с поиском речи и запросами.
                // Если звук окна извлекался дольше, чем оно длится, воспроизведение уже ушло вперёд:
                // следующее окно начинаем с текущей позиции, а не с конца только что взятого.
                val nowPosition = currentPositionMsProvider().coerceAtLeast(0L)
                val nextStart = skipCovered(liveStart(maxOf(windowEnd, nowPosition), nowPosition, windowLengthMs(windowIndex)))
                val nextTarget = nowPosition + WINDOW_AHEAD_MS
                val nextDuration = capToCovered(
                    nextStart,
                    minOf(nextTarget, nextStart + windowLengthMs(windowIndex), contentDurationMs) - nextStart,
                )
                val nextIsTail = nextStart + nextDuration >= contentDurationMs
                // Предзагрузка только на слабых устройствах: второе извлечение звука параллельно с
                // обработкой нагружает процессор, память и открывает второе соединение к источнику;
                // на телефоне это приводило к зависаниям.
                if (LIGHT_PIPELINE && nextDuration >= MIN_PREFETCH_MS &&
                    (nextDuration >= MIN_REFILL_MS || nextIsTail)
                ) {
                    prefetchStartMs = nextStart
                    prefetchDurationMs = nextDuration
                    prefetch = async {
                        val startedAt = System.currentTimeMillis()
                        val r = audioExtractor.extract(AudioExtractionRequest(streamUrl, headers, nextStart, nextDuration))
                        prefetchTookMs = System.currentTimeMillis() - startedAt
                        r
                    }
                }

                // Слабое 32-битное устройство (Xiaomi TV Stick): ни поиска речи (нейросеть на CPU),
                // ни декодирования в PCM на устройстве — отправляем окно целиком одним файлом,
                // реплики и время размечает сам Whisper на сервере.
                if (LIGHT_PIPELINE) {
                    val meta = "w=$windowIndex;start=${windowStart / 1000};dur=${windowDuration / 1000};extract_ms=$extractMs;took_ms=$tookMs;lead_ms=$leadMs;cache=$cacheStatus;src=$srcTag"
                    val stop = transcribeWholeWindow(chunk, language, meta)
                    File(chunk.filePath).delete()
                    Log.d(TAG, "window #$windowIndex (light) done after ${System.currentTimeMillis() - t0} ms")
                    if (stop) {
                        prefetch?.cancel()
                        return@coroutineScope
                    }
                    recordCovered(windowStart, windowEnd)
                    coveredUpToMs = windowEnd
                    continue
                }

                val segmentsResult = vadSegmenter.detectSpeechSegments(chunk)
                if (segmentsResult.isFailure) {
                    Log.w(TAG, "VAD failed", segmentsResult.exceptionOrNull())
                    _state.value = AutoSubtitleState.Error("Не удалось найти речь: ${segmentsResult.exceptionOrNull()?.message}")
                    File(chunk.filePath).delete()
                    coveredUpToMs = windowEnd
                    continue
                }
                val segments = segmentsResult.getOrThrow()
                Log.d(TAG, "window #$windowIndex speech segments: ${segments.size}, +${System.currentTimeMillis() - t0} ms")
                setProgress("фраз речи: ${segments.size}")

                val vadMs = System.currentTimeMillis() - t0 - extractMs
                val metaBase = "w=$windowIndex;start=${windowStart / 1000};dur=${windowDuration / 1000};extract_ms=$extractMs;took_ms=$tookMs;vad_ms=$vadMs;lead_ms=$leadMs;cache=$cacheStatus;src=$srcTag"
                val aborted = transcribeChunk(chunk, segments, language, metaBase)
                File(chunk.filePath).delete() // временный аудио-файл окна больше не нужен
                Log.d(TAG, "window #$windowIndex done after ${System.currentTimeMillis() - t0} ms")
                // Облако недоступно целиком — дальше нет смысла
                // тратить процессор на извлечение звука.
                if (aborted) {
                    prefetch?.cancel()
                    return@coroutineScope
                }
                recordCovered(windowStart, windowEnd)
                coveredUpToMs = windowEnd
                setProgress("готово")
            }
            prefetch?.cancel()
            // Дошли до конца видео (в том числе целиком из кэша): состояние не должно остаться «Выкл».
            if (isActive && _state.value !is AutoSubtitleState.Error) setProgress("готово")
        }
    }

    /**
     * Облегчённый путь: всё окно (AAC в .m4a) одним запросом, без VAD и без PCM на устройстве.
     * Возвращает true, если дальше продолжать нет смысла (облако недоступно целиком).
     */
    private suspend fun transcribeWholeWindow(chunk: ExtractedAudioChunk, language: String, meta: String): Boolean {
        setProgress("запрос")
        return when (val outcome = transcribeWithRetry(File(chunk.filePath), language, "audio/mp4", meta)) {
            is CloudSttRepository.Outcome.Success -> {
                // Время реплик в ответе — от начала загруженного окна.
                for (s in outcome.result.segments) {
                    if (s.text.isNotBlank()) {
                        addCue(SubtitleCue(chunk.startPositionMs + s.startMs, chunk.startPositionMs + s.endMs, s.text))
                    }
                }
                false
            }
            CloudSttRepository.Outcome.NeedsLocalFallback -> {
                _state.value = AutoSubtitleState.Error("Облачное распознавание недоступно (ответ сервера 503)")
                true
            }
            CloudSttRepository.Outcome.RateLimited -> {
                windowOk = false
                _state.value = AutoSubtitleState.Error("Слишком частые запросы, окно пропущено")
                false
            }
            is CloudSttRepository.Outcome.Error -> {
                windowOk = false
                Log.w(TAG, "whole-window upload failed", outcome.throwable)
                _state.value = AutoSubtitleState.Error("Сеть: ${outcome.throwable.message}")
                false
            }
        }
    }

    /**
     * Длина окна извлечения по порядковому номеру. Слабая приставка: короткие окна 15 → 30 → 60 с
     * (с предзагрузкой). Телефон: первое окно 30 с, дальше крупные по 2 минуты — каждое извлечение
     * звука читает источник (Lampa/TorrServer) с новой позиции и тормозит основной плеер, поэтому
     * частые короткие окна вызывали подвисания каждые ~20 секунд.
     */
    private fun windowLengthMs(index: Int): Long =
        if (LIGHT_PIPELINE) when (index) {
            0 -> FIRST_WINDOW_MS
            1 -> SECOND_WINDOW_MS
            else -> LIGHT_MAX_WINDOW_MS
        } else when (index) {
            0 -> PHONE_FIRST_WINDOW_MS
            else -> MAX_EXTRACT_MS
        }

    private suspend fun transcribeChunk(
        chunk: ExtractedAudioChunk,
        segments: List<SpeechSegment>,
        language: String,
        metaBase: String,
    ): Boolean {
        if (segments.isEmpty()) return false
        // Оркестратор крутится в viewModelScope (главный поток). Декодирование 2 минут AAC в PCM —
        // секунды чистой работы процессора: на главном потоке это замораживало интерфейс и приводило
        // к ANR («приложение не отвечает»). Выносим в Default.
        val pcm = withContext(Dispatchers.Default) { PcmAudioDecoder.decodeToMono16k(chunk.filePath) }

        // Один запрос на ГРУППУ соседних фраз (до ~28 с), а не на каждую фразу:
        // лимит backend 10 запросов/мин, у Groq 20/мин и минимум 10 с на запрос,
        // поэтому «фраза = запрос» упирается в 429 и теряет большую часть речи.
        val groups = groupSegments(segments)
        for ((groupIndex, group) in groups.withIndex()) {
            setProgress("запрос ${groupIndex + 1} из ${groups.size}")
            val span = SpeechSegment(group.first().startMs, group.last().endMs)
            val slice = sliceSegmentPcm(pcm, chunk, span)
            if (slice == null) continue

            // Кодирование WAV и запись файла на диск — тоже не на главном потоке.
            val wavFile = withContext(Dispatchers.IO) {
                File.createTempFile("stt_segment", ".wav").apply { writeBytes(WavEncoder.encode(slice)) }
            }
            val outcome = try {
                transcribeWithRetry(wavFile, language, meta = "$metaBase;g=${groupIndex + 1}/${groups.size}")
            } finally {
                wavFile.delete()
            }
            when (outcome) {
                is CloudSttRepository.Outcome.Success -> appendCloudCues(outcome.result, span)
                CloudSttRepository.Outcome.NeedsLocalFallback -> {
                    // backend уже перебрал всю цепочку облачных провайдеров и вернул 503 —
                    // дальнейшие попытки смысла не имеют.
                    _state.value = AutoSubtitleState.Error("Облачное распознавание недоступно (ответ сервера 503)")
                    return true
                }
                CloudSttRepository.Outcome.RateLimited,
                is CloudSttRepository.Outcome.Error -> {
                    windowOk = false
                    // Сетевая/серверная ошибка или лимит не отпустил после повторов —
                    // пропускаем ТОЛЬКО эту группу, не переключаем весь пайплайн на локальный.
                }
            }
            delay(REQUEST_PACE_MS) // не частим: лимиты запросов в минуту у backend и провайдеров
        }

        return false
    }

    /** При 429 ждём и повторяем (до MAX_RATE_LIMIT_RETRIES раз), остальные исходы — как есть. */
    private suspend fun transcribeWithRetry(wavFile: File, language: String, mimeType: String = "audio/wav", meta: String? = null): CloudSttRepository.Outcome {
        var attempt = 0
        while (true) {
            val outcome = cloudSttRepository.transcribe(wavFile, language, mimeType, meta)
            if (outcome != CloudSttRepository.Outcome.RateLimited || attempt >= MAX_RATE_LIMIT_RETRIES) return outcome
            attempt++
            delay(RATE_LIMIT_BACKOFF_MS)
        }
    }

    /**
     * Короткая метка источника для диагностики в логах сервера: схема, тип потока, публичный/локальный
     * адрес и (только для публичных) хост. Путь, параметры и токены в метку не попадают; символы —
     * только буквы, цифры и «_», чтобы серверный фильтр поля meta ничего не вырезал.
     */
    private fun sourceTag(url: String): String {
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase() ?: "x"
        val host = uri?.host?.lowercase().orEmpty()
        val path = uri?.path?.lowercase().orEmpty()
        val kind = when {
            path.contains("m3u8") -> "hls"
            path.endsWith(".mpd") -> "dash"
            path.endsWith(".mp4") -> "mp4"
            path.endsWith(".mkv") -> "mkv"
            path.endsWith(".ts") -> "ts"
            else -> "other"
        }
        val loop = host == "localhost" || host.startsWith("127.")
        val lan = host.startsWith("10.") || host.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
        // nohost — адрес не разобрался (необычные символы в ссылке): раньше это ошибочно называлось «local».
        val where = when {
            host.isEmpty() -> "nohost"
            loop -> "loop"
            lan -> "lan"
            else -> "pub"
        }
        val hostPart = if (where == "pub") "_" + host.replace(Regex("[^a-z0-9]"), "_").take(40) else ""
        return "${scheme}_${kind}_$where$hostPart"
    }

    /** Склеивает соседние фразы в группы: пауза между ними ≤ MAX_GROUP_GAP_MS, длина группы ≤ MAX_GROUP_SPAN_MS. */
    private fun groupSegments(segments: List<SpeechSegment>): List<List<SpeechSegment>> {
        val groups = mutableListOf<MutableList<SpeechSegment>>()
        for (seg in segments) {
            val current = groups.lastOrNull()
            if (current != null &&
                seg.startMs - current.last().endMs <= MAX_GROUP_GAP_MS &&
                seg.endMs - current.first().startMs <= MAX_GROUP_SPAN_MS
            ) current += seg else groups += mutableListOf(seg)
        }
        return groups
    }

    private fun sliceSegmentPcm(pcm: ShortArray, chunk: ExtractedAudioChunk, segment: SpeechSegment): ShortArray? {
        val localStartMs = (segment.startMs - chunk.startPositionMs).coerceAtLeast(0)
        val localEndMs = (segment.endMs - chunk.startPositionMs).coerceAtMost(chunk.durationMs)
        val startSample = (localStartMs * SAMPLE_RATE / 1000).toInt().coerceIn(0, pcm.size)
        val endSample = (localEndMs * SAMPLE_RATE / 1000).toInt().coerceIn(startSample, pcm.size)
        return if (endSample <= startSample) null else pcm.copyOfRange(startSample, endSample)
    }

    private fun appendCloudCues(result: SttTranscriptionDto, segment: SpeechSegment) {
        // response_format=verbose_json на backend уже сконвертирован в мс
        // (app/routers/subtitles.py) и ОТНОСИТЕЛЕН к загруженному WAV
        // (т.е. к самому сегменту, не ко всему чанку) — сдвиг на
        // segment.startMs (уже абсолютная координата потока, см.
        // SileroVadSegmenter) даёт итоговую абсолютную позицию.
        if (result.segments.isEmpty()) {
            if (result.text.isNotBlank()) addCue(SubtitleCue(segment.startMs, segment.endMs, result.text))
            return
        }
        result.segments.forEach { s ->
            if (s.text.isNotBlank()) addCue(SubtitleCue(segment.startMs + s.startMs, segment.startMs + s.endMs, s.text))
        }
    }

    // Подпись шага для строки статуса в меню: по ней видно, где именно остановилась обработка.
    private var progressLabel = ""
    private fun setProgress(step: String) {
        _state.value = AutoSubtitleState.GeneratingAi(AiSource.CLOUD, "$progressLabel: $step, реплик ${_cues.value.size}")
    }

    private fun addCue(cue: SubtitleCue) {
        _cues.value = (_cues.value + cue).sortedBy { it.startMs }
    }

    // Публичный: переключатель forceLightPipeline используется из меню плеера на телефоне.
    companion object {
        const val TAG = "ZenithSubs"
        const val SAMPLE_RATE = 16_000
        // 32-битный процесс = слабое устройство (замеры на Xiaomi TV Stick 4K): всё тяжёлое — на сервер.
        // Тестовый переключатель (меню субтитров телефона): прогнать на телефоне тот же облегчённый путь,
        // что и на приставке (окна 15→30→60 с, предзагрузка, без VAD/PCM, целиком одним файлом).
        // Действует на следующий запуск автосубтитров; в память не сохраняется.
        @Volatile var forceLightPipeline: Boolean = false
        val LIGHT_PIPELINE: Boolean get() = forceLightPipeline || !android.os.Process.is64Bit()
        const val WINDOW_AHEAD_MS = 5 * 60 * 1000L // 5 минут вперёд, PROMPT_SUBTITLES.md, "Горизонт генерации"
        const val MAX_EXTRACT_MS = 2 * 60 * 1000L // за один заход извлекаем не больше 2 минут звука
        const val FIRST_WINDOW_MS = 15_000L // первое окно совсем короткое — первые реплики быстрее
        const val SECOND_WINDOW_MS = 30_000L
        const val LIGHT_MAX_WINDOW_MS = 60_000L
        const val PHONE_FIRST_WINDOW_MS = 30_000L
        const val MIN_PREFETCH_MS = 5_000L // совсем короткий хвост заранее не извлекаем
        const val SKIP_MIN_MS = 5_000L // пропуск вперёд короче этого не делаем
        const val MAX_SKIP_MS = 90_000L // и не дальше этого (оценка времени извлечения ограничена)
        const val MIN_REFILL_MS = 30_000L // у потолка запаса не берём окна короче (иначе запрос каждые ~2 с)
        const val MAX_GROUP_GAP_MS = 2_000L // пауза между фразами в одной группе
        const val MAX_GROUP_SPAN_MS = 28_000L // Whisper работает окнами по 30 с
        const val REQUEST_PACE_MS = 3_000L // ≤ 20 запросов в минуту
        const val RATE_LIMIT_BACKOFF_MS = 10_000L
        const val MAX_RATE_LIMIT_RETRIES = 3
        const val POLL_INTERVAL_MS = 15_000L // как часто проверяем, не пора ли расширить покрытое окно
    }
}
