package com.platinum.ott.core.subtitles

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * PROMPT_SUBTITLES.md, подзадача 2 — "извлечение аудиодорожки из потока
 * отдельно от воспроизведения" (архитектурное требование, сопоставимое по
 * сложности с EPG/NDK-интеграцией согласно самому промту).
 *
 * ПОЧЕМУ Transformer, а не второй ExoPlayer/AudioSink-хук: задача — получить
 * аудио НЕ в реальном времени (для VOD нужно окно на 5 минут ВПЕРЁД
 * позиции плеера, т.е. быстрее, чем идёт обычное воспроизведение), а обычный
 * ExoPlayer (даже с отключённым видео-рендерером) всё равно пишет звук в
 * AudioTrack и физически привязан к темпу реального времени через сам
 * аудио-выход устройства. Media3 Transformer декодирует/перекодирует с
 * максимальной скоростью декодера, без какого-либо аудио-вывода на
 * устройство — ровно то, что нужно. Реализация через
 * ExoPlayerAssetLoader.Factory(context, decoderFactory, clock,
 * mediaSourceFactory) — примечание: Transformer.Builder.setMediaSourceFactory()
 * был удалён из API ещё в androidx.media3 1.1.0 (см. release notes), поэтому
 * кастомный DataSource.Factory (нужен для заголовков M3U/Xtream-источников,
 * тот же смысл, что и PlayerViewModel.buildDataSourceFactory()) пробрасывается
 * только через ExoPlayerAssetLoader.Factory, не напрямую через Transformer.Builder.
 *
 * EditedMediaItem.setRemoveVideo(true) — видео не декодируется вообще, не
 * только не используется: реальная экономия CPU на слабых TV-приставках
 * (см. README.md — тестовое устройство Xiaomi TV Stick 4K), не просто
 * "лишний трек выбрасывается на выходе".
 *
 * Результат — компактный аудио-файл (AAC-в-MP4, не сырой PCM): этого
 * формата достаточно и для облачных STT API (Groq/аналоги принимают сжатое
 * аудио напрямую, экономия трафика важна отдельно — см. VAD в следующих
 * подзадачах), и для локального Whisper (там всё равно потребуется
 * перекодировка в 16кГц PCM непосредственно перед подачей в whisper.cpp —
 * это забота подзадачи 5, не этой).
 *
 * НЕ ПРОВЕРЕНО на реальном устройстве/сборкой CI (см. COMPATIBILITY.md —
 * то, что "нельзя проверить без реальной среды сборки", здесь тем более
 * актуально: Transformer/ExoPlayerAssetLoader — весь под @UnstableApi,
 * первый реальный тест API-поверхности будет только на GitHub Actions).
 *
 * ИЗВЕСТНОЕ ОГРАНИЧЕНИЕ, не решённое в этой подзадаче: для живых каналов
 * ClippingConfiguration с абсолютным startPositionMs имеет другой смысл
 * (нет фиксированного таймлайна, есть live edge) — эта реализация проверена
 * рассуждением только для VOD (movieId с известной длительностью потока).
 * Формирование запроса под живой канал ("прогретый конвейер", короткий чанк
 * от текущего момента без фиксированного starPositionMs) — задача
 * оркестратора (подзадача 6), возможно потребует отдельного пути здесь же.
 */
@UnstableApi
class StreamAudioExtractor(private val context: Context) {

    // Отдельный OkHttpClient, не переиспользует mediaHttpClient из
    // PlayerViewModel (тот привязан к жизненному циклу конкретного
    // PlayerViewModel/onCleared) — этот компонент живёт в SessionGraph и
    // должен работать независимо от того, открыт ли сейчас экран плеера.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun extract(request: AudioExtractionRequest): Result<ExtractedAudioChunk> = withContext(Dispatchers.Main) {
        // Transformer требует, чтобы весь публичный API вызывался с одного
        // потока с Looper'ом (по документации Media3 — обычно main thread) —
        // отсюда withContext(Dispatchers.Main), а не Dispatchers.IO, как у
        // большинства остального сетевого кода в проекте.
        suspendCancellableCoroutine { cont ->
            val outputFile = File(context.cacheDir, "subs_audio_${System.currentTimeMillis()}.m4a")
            try {
                val dataSourceFactory = DataSource.Factory {
                    OkHttpDataSource.Factory(okHttpClient)
                        .setDefaultRequestProperties(request.headers)
                        .createDataSource()
                }
                val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
                val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
                    context, DefaultDecoderFactory(context), Clock.DEFAULT, mediaSourceFactory
                )

                val transformer = Transformer.Builder(context)
                    .setAssetLoaderFactory(assetLoaderFactory)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) {
                                cont.resume(
                                    Result.success(
                                        ExtractedAudioChunk(
                                            filePath = outputFile.absolutePath,
                                            startPositionMs = request.startPositionMs,
                                            durationMs = request.durationMs,
                                            mimeType = MimeTypes.AUDIO_AAC
                                        )
                                    )
                                )
                            }
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            outputFile.delete()
                            if (cont.isActive) cont.resume(Result.failure(exportException))
                        }
                    })
                    .build()

                val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.startPositionMs)
                    .setEndPositionMs(request.startPositionMs + request.durationMs)
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setUri(request.streamUrl)
                    .setClippingConfiguration(clippingConfiguration)
                    .build()
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveVideo(true)
                    .build()

                transformer.start(editedMediaItem, outputFile.absolutePath)
                cont.invokeOnCancellation {
                    transformer.cancel()
                    outputFile.delete()
                }
            } catch (e: Exception) {
                outputFile.delete()
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }
    }
}
