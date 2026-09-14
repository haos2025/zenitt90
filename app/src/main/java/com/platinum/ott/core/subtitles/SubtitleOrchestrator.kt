package com.platinum.ott.core.subtitles

import com.platinum.ott.core.subtitles.whisper.LocalWhisperTranscriber
import com.platinum.ott.core.subtitles.whisper.WhisperModelVariant
import com.platinum.ott.data.remote.dto.SttTranscriptionDto
import com.platinum.ott.data.repository.CloudSttRepository
import com.platinum.ott.domain.model.SubtitleFormat
import com.platinum.ott.domain.usecase.SearchOpenSubtitlesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
            _state.value = AutoSubtitleState.SearchingOpenSubtitles
            val match = runCatching { openSubtitlesUseCase.execute(title, year) }.getOrNull()
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
            val target = currentPositionMsProvider() + WINDOW_AHEAD_MS
            if (coveredUpToMs >= target) {
                delay(POLL_INTERVAL_MS)
                continue
            }

            val windowStart = coveredUpToMs
            val windowEnd = minOf(target, contentDurationMs)
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
            val chunkResult = audioExtractor.extract(
                AudioExtractionRequest(streamUrl, headers, windowStart, windowDuration)
            )
            if (chunkResult.isFailure) {
                _state.value = AutoSubtitleState.Error("Не удалось извлечь аудио: ${chunkResult.exceptionOrNull()?.message}")
                coveredUpToMs = windowEnd // не зацикливаемся вечно на одном и том же неудачном окне
                continue
            }
            val chunk = chunkResult.getOrThrow()

            val segmentsResult = vadSegmenter.detectSpeechSegments(chunk)
            if (segmentsResult.isFailure) {
                File(chunk.filePath).delete()
                coveredUpToMs = windowEnd
                continue
            }
            val segments = segmentsResult.getOrThrow()

            transcribeChunk(chunk, segments, language, whisperVariant)
            File(chunk.filePath).delete() // временный аудио-файл окна больше не нужен
            coveredUpToMs = windowEnd
        }
    }

    private suspend fun transcribeChunk(
        chunk: ExtractedAudioChunk,
        segments: List<SpeechSegment>,
        language: String,
        whisperVariant: WhisperModelVariant,
    ) {
        if (segments.isEmpty()) return
        val pcm = PcmAudioDecoder.decodeToMono16k(chunk.filePath)
        var cloudExhausted = false
        val localFallbackSegments = mutableListOf<SpeechSegment>()

        for (segment in segments) {
            if (cloudExhausted) {
                localFallbackSegments += segment
                continue
            }

            _state.value = AutoSubtitleState.GeneratingAi(AiSource.CLOUD)
            val slice = sliceSegmentPcm(pcm, chunk, segment)
            if (slice == null) continue

            val wavFile = File.createTempFile("stt_segment", ".wav").apply { writeBytes(WavEncoder.encode(slice)) }
            val outcome = try {
                cloudSttRepository.transcribe(wavFile, language)
            } finally {
                wavFile.delete()
            }
            when (outcome) {
                is CloudSttRepository.Outcome.Success -> appendCloudCues(outcome.result, segment)
                CloudSttRepository.Outcome.NeedsLocalFallback -> {
                    // "Локальный Whisper — когда офлайн или лимит облака
                    // исчерпан" (PROMPT_SUBTITLES.md) — backend уже перебрал
                    // всю цепочку облачных провайдеров и вернул 503,
                    // дальнейшие попытки в ЭТОМ чанке смысла не имеют.
                    cloudExhausted = true
                    localFallbackSegments += segment
                }
                is CloudSttRepository.Outcome.Error -> {
                    // Обычная сетевая/серверная ошибка — пропускаем ТОЛЬКО
                    // этот сегмент, не переключаем весь оставшийся пайплайн
                    // на локальный путь (в отличие от NeedsLocalFallback).
                }
            }
        }

        if (localFallbackSegments.isNotEmpty()) {
            _state.value = AutoSubtitleState.GeneratingAi(AiSource.LOCAL)
            localWhisperTranscriber.transcribeSegments(chunk, localFallbackSegments, variant = whisperVariant, language = language)
                .getOrNull()
                ?.forEach { r -> addCue(SubtitleCue(r.startMs, r.endMs, r.text)) }
        }
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
        const val SAMPLE_RATE = 16_000
        const val WINDOW_AHEAD_MS = 5 * 60 * 1000L // 5 минут вперёд, PROMPT_SUBTITLES.md, "Горизонт генерации"
        const val POLL_INTERVAL_MS = 15_000L // как часто проверяем, не пора ли расширить покрытое окно
    }
}
