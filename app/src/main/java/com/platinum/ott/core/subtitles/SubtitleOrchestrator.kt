package com.platinum.ott.core.subtitles

import android.util.Log
import com.platinum.ott.core.subtitles.whisper.LocalWhisperTranscriber
import com.platinum.ott.core.subtitles.whisper.WhisperModelVariant
import com.platinum.ott.data.remote.dto.SttTranscriptionDto
import com.platinum.ott.data.repository.CloudSttRepository
import com.platinum.ott.domain.model.SubtitleFormat
import com.platinum.ott.domain.usecase.SearchOpenSubtitlesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 *   (подзадача 3) со скользящим окном 5 минут вперёд → если облако
 *   исчерпано (backend вернул 503, см. CloudSttRepository) → локальный
 *   Whisper (подзадача 5) для оставшихся сегментов ТОГО ЖЕ чанка.
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
 * КАЖДЫЙ 5-МИНУТНЫЙ ЧАНК ДЕКОДИРУЕТСЯ В PCM ДО ТРЁХ РАЗ (известная
 * неэффективность, не устранена в этой сессии): один раз внутри
 * SileroVadSegmenter (подзадача 4, инкапсулировано там), один раз здесь
 * (для нарезки WAV под облако) и ещё раз внутри LocalWhisperTranscriber
 * (подзадача 5, при локальном fallback). Объединение в один общий проход
 * потребовало бы менять публичные сигнатуры уже сданных подзадач — оставлено
 * как есть, стоит оптимизировать отдельным заходом, если профилирование на
 * реальном устройстве покажет, что это заметно.
 */
class SubtitleOrchestrator(
    private val audioExtractor: StreamAudioExtractor,
    private val vadSegmenter: SileroVadSegmenter,
    private val openSubtitlesUseCase: SearchOpenSubtitlesUseCase,
    private val cloudSttRepository: CloudSttRepository,
    private val localWhisperTranscriber: LocalWhisperTranscriber,
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
        // PROMPT_SUBTITLES.md, подзадача 9 — "ползунок скорость/точность",
        // влияет только на локальный Whisper-фолбэк (облачный путь модель
        // выбирает сам backend). TINY по умолчанию — тот же дефолт, что и
        // в SubtitlePreferences.getPreferLocalAccuracy() (false → TINY).
        whisperVariant: WhisperModelVariant = WhisperModelVariant.TINY,
        onOpenSubtitlesFound: (url: String, language: String, format: SubtitleFormat) -> Unit,
    ) {
        stop()
        _cues.value = emptyList()
        job = scope.launch {
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
            runAiPipelineForVod(streamUrl, headers, contentDurationMs, currentPositionMsProvider, language, whisperVariant)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = AutoSubtitleState.Off
        _cues.value = emptyList()
    }

    private suspend fun runAiPipelineForVod(
        streamUrl: String,
        headers: Map<String, String>,
        contentDurationMs: Long,
        currentPositionMsProvider: () -> Long,
        language: String,
        whisperVariant: WhisperModelVariant,
    ) {
        var coveredUpToMs = 0L
        while (kotlinx.coroutines.currentCoroutineContext().isActive && coveredUpToMs < contentDurationMs) {
            val position = currentPositionMsProvider().coerceAtLeast(0L)
            // Открыли с позиции или перемотали вперёд — пропущенный кусок не обрабатываем
            // (раньше первое окно тянулось от нуля до позиции+5 минут одним куском).
            if (position > coveredUpToMs) coveredUpToMs = position
            val target = position + WINDOW_AHEAD_MS
            if (coveredUpToMs >= target) {
                delay(POLL_INTERVAL_MS)
                continue
            }

            val windowStart = coveredUpToMs
            // Окно не длиннее MAX_EXTRACT_MS: первые реплики появляются раньше,
            // и на слабой приставке не декодируем сразу много минут.
            val windowEnd = minOf(target, windowStart + MAX_EXTRACT_MS, contentDurationMs)
            val windowDuration = windowEnd - windowStart
            if (windowDuration <= 0) {
                delay(POLL_INTERVAL_MS)
                continue
            }

            // Kotlin 2.1.20 (проект ещё не на 2.2) не разрешает non-local
            // continue внутри инлайн-лямбды (getOrElse{}) — раньше здесь
            // было .getOrElse { ...; continue }, пришлось развернуть в
            // явную проверку Result, чтобы continue относился напрямую к
            // this while, а не к лямбде.
            // Раньше до первого запроса к облаку состояние оставалось «Выкл», и было не видно,
            // что обработка вообще запущена. Теперь пользователь видит «Распознаём…» сразу.
            _state.value = AutoSubtitleState.GeneratingAi(AiSource.CLOUD)
            Log.d(TAG, "extract window $windowStart..$windowEnd ms")
            val chunkResult = audioExtractor.extract(
                AudioExtractionRequest(streamUrl, headers, windowStart, windowDuration)
            )
            if (chunkResult.isFailure) {
                Log.w(TAG, "extract failed", chunkResult.exceptionOrNull())
                _state.value = AutoSubtitleState.Error("Не удалось извлечь аудио: ${chunkResult.exceptionOrNull()?.message}")
                coveredUpToMs = windowEnd // не зацикливаемся вечно на одном и том же неудачном окне
                continue
            }
            val chunk = chunkResult.getOrThrow()

            val segmentsResult = vadSegmenter.detectSpeechSegments(chunk)
            if (segmentsResult.isFailure) {
                Log.w(TAG, "VAD failed", segmentsResult.exceptionOrNull())
                _state.value = AutoSubtitleState.Error("Не удалось найти речь: ${segmentsResult.exceptionOrNull()?.message}")
                File(chunk.filePath).delete()
                coveredUpToMs = windowEnd
                continue
            }
            val segments = segmentsResult.getOrThrow()
            Log.d(TAG, "speech segments in window: ${segments.size}")

            val aborted = transcribeChunk(chunk, segments, language, whisperVariant)
            File(chunk.filePath).delete() // временный аудио-файл окна больше не нужен
            // Облако недоступно, а локальный Whisper на этом устройстве бесполезен — дальше нет смысла
            // тратить процессор на извлечение звука.
            if (aborted) return
            coveredUpToMs = windowEnd
        }
    }

    private suspend fun transcribeChunk(
        chunk: ExtractedAudioChunk,
        segments: List<SpeechSegment>,
        language: String,
        whisperVariant: WhisperModelVariant,
    ): Boolean {
        if (segments.isEmpty()) return false
        // Оркестратор крутится в viewModelScope (главный поток). Декодирование 2 минут AAC в PCM —
        // секунды чистой работы процессора: на главном потоке это замораживало интерфейс и приводило
        // к ANR («приложение не отвечает»). Выносим в Default.
        val pcm = withContext(Dispatchers.Default) { PcmAudioDecoder.decodeToMono16k(chunk.filePath) }
        var cloudExhausted = false
        val localFallbackSegments = mutableListOf<SpeechSegment>()

        // Один запрос на ГРУППУ соседних фраз (до ~28 с), а не на каждую фразу:
        // лимит backend 10 запросов/мин, у Groq 20/мин и минимум 10 с на запрос,
        // поэтому «фраза = запрос» упирается в 429 и теряет большую часть речи.
        for (group in groupSegments(segments)) {
            // Локальный Whisper всегда кодирует окно 30 с, поэтому время почти не зависит от длины
            // фрагмента (замер: 5 с и 30 с — одинаково ~4 с на tiny). Отдаём ему СКЛЕЕННУЮ группу
            // целиком, а не каждую фразу отдельно.
            val groupSpan = SpeechSegment(group.first().startMs, group.last().endMs)
            if (cloudExhausted) {
                localFallbackSegments += groupSpan
                continue
            }

            _state.value = AutoSubtitleState.GeneratingAi(AiSource.CLOUD)
            val span = SpeechSegment(group.first().startMs, group.last().endMs)
            val slice = sliceSegmentPcm(pcm, chunk, span)
            if (slice == null) continue

            // Кодирование WAV и запись файла на диск — тоже не на главном потоке.
            val wavFile = withContext(Dispatchers.IO) {
                File.createTempFile("stt_segment", ".wav").apply { writeBytes(WavEncoder.encode(slice)) }
            }
            val outcome = try {
                transcribeWithRetry(wavFile, language)
            } finally {
                wavFile.delete()
            }
            when (outcome) {
                is CloudSttRepository.Outcome.Success -> appendCloudCues(outcome.result, span)
                CloudSttRepository.Outcome.NeedsLocalFallback -> {
                    // "Локальный Whisper — когда офлайн или лимит облака
                    // исчерпан" (PROMPT_SUBTITLES.md) — backend уже перебрал
                    // всю цепочку облачных провайдеров и вернул 503,
                    // дальнейшие попытки в ЭТОМ чанке смысла не имеют.
                    cloudExhausted = true
                    localFallbackSegments += groupSpan
                }
                CloudSttRepository.Outcome.RateLimited,
                is CloudSttRepository.Outcome.Error -> {
                    // Сетевая/серверная ошибка или лимит не отпустил после повторов —
                    // пропускаем ТОЛЬКО эту группу, не переключаем весь пайплайн на локальный.
                }
            }
            delay(REQUEST_PACE_MS) // не частим: лимиты запросов в минуту у backend и провайдеров
        }

        if (localFallbackSegments.isNotEmpty()) {
            if (!LOCAL_WHISPER_USABLE) {
                _state.value = AutoSubtitleState.Error(
                    "Облачное распознавание недоступно, а локальное на этом устройстве слишком медленное"
                )
                return true
            }
            _state.value = AutoSubtitleState.GeneratingAi(AiSource.LOCAL)
            localWhisperTranscriber.transcribeSegments(chunk, localFallbackSegments, variant = whisperVariant, language = language)
                .getOrNull()
                ?.forEach { r -> addCue(SubtitleCue(r.startMs, r.endMs, r.text)) }
        }
        return false
    }

    /** При 429 ждём и повторяем (до MAX_RATE_LIMIT_RETRIES раз), остальные исходы — как есть. */
    private suspend fun transcribeWithRetry(wavFile: File, language: String): CloudSttRepository.Outcome {
        var attempt = 0
        while (true) {
            val outcome = cloudSttRepository.transcribe(wavFile, language)
            if (outcome != CloudSttRepository.Outcome.RateLimited || attempt >= MAX_RATE_LIMIT_RETRIES) return outcome
            attempt++
            delay(RATE_LIMIT_BACKOFF_MS)
        }
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
        // SileroVadSegmenter) даёт итоговую абсолютную позицию, тот же
        // приём, что и в LocalWhisperTranscriber для локального пути.
        if (result.segments.isEmpty()) {
            if (result.text.isNotBlank()) addCue(SubtitleCue(segment.startMs, segment.endMs, result.text))
            return
        }
        result.segments.forEach { s ->
            if (s.text.isNotBlank()) addCue(SubtitleCue(segment.startMs + s.startMs, segment.startMs + s.endMs, s.text))
        }
    }

    private fun addCue(cue: SubtitleCue) {
        _cues.value = (_cues.value + cue).sortedBy { it.startMs }
    }

    private companion object {
        const val TAG = "ZenithSubs"
        const val SAMPLE_RATE = 16_000
        // Замер на Xiaomi TV Stick 4K (32-битный процесс, armeabi-v7a): tiny на куске 30 с — 113 с (×0.26),
        // base — 270 с. Для субтитров это бесполезно. На 64-битном телефоне (arm64) тот же замер: ×7 и ×3.
        // Поэтому локальный Whisper разрешён только в 64-битном процессе.
        val LOCAL_WHISPER_USABLE: Boolean = android.os.Process.is64Bit()
        const val WINDOW_AHEAD_MS = 5 * 60 * 1000L // 5 минут вперёд, PROMPT_SUBTITLES.md, "Горизонт генерации"
        const val MAX_EXTRACT_MS = 2 * 60 * 1000L // за один заход извлекаем не больше 2 минут звука
        const val MAX_GROUP_GAP_MS = 2_000L // пауза между фразами в одной группе
        const val MAX_GROUP_SPAN_MS = 28_000L // Whisper работает окнами по 30 с
        const val REQUEST_PACE_MS = 3_000L // ≤ 20 запросов в минуту
        const val RATE_LIMIT_BACKOFF_MS = 10_000L
        const val MAX_RATE_LIMIT_RETRIES = 3
        const val POLL_INTERVAL_MS = 15_000L // как часто проверяем, не пора ли расширить покрытое окно
    }
}
