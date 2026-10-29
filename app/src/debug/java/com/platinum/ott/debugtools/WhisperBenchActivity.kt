package com.platinum.ott.debugtools

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum.ott.core.subtitles.whisper.WhisperContext
import com.platinum.ott.core.subtitles.whisper.WhisperModelManager
import com.platinum.ott.core.subtitles.whisper.WhisperModelVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Только debug. Замер скорости локального Whisper на реальном устройстве.
 * Запускается отдельным значком «Whisper замер». Ничего не меняет в данных
 * приложения, кроме скачивания моделей в filesDir/whisper_models (их же
 * использует обычный путь автосубтитров).
 */
class WhisperBenchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { BenchScreen() }
    }
}

@Composable
private fun BenchScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1220))
            .padding(32.dp),
    ) {
        Text("Замер локального Whisper", color = Color.White, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            modifier = Modifier.focusRequester(focus),
            enabled = !running,
            onClick = {
                running = true
                lines.clear()
                val log: (String) -> Unit = { line ->
                    Log.i("WhisperBench", line)
                    scope.launch { lines.add(line) }
                }
                scope.launch {
                    try {
                        WhisperBench.run(context, log)
                    } finally {
                        running = false
                    }
                }
            },
        ) {
            Text(if (running) "Идёт замер… не закрывайте экран" else "Запустить замер")
        }
        LazyColumn(state = listState, modifier = Modifier.padding(top = 16.dp)) {
            items(lines) { line ->
                Text(line, color = Color(0xFFEAF6FF), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }
    }
}

private object WhisperBench {

    private const val SAMPLE_RATE = 16_000

    private data class Row(val variant: WhisperModelVariant, val threads: Int, val lenSec: Int, val ms: Long)

    suspend fun run(context: Context, log: (String) -> Unit) = withContext(Dispatchers.Default) {
        log(deviceInfo(context))
        try {
            log("Whisper: " + WhisperContext.systemInfo())
        } catch (t: Throwable) {
            log("Нативная библиотека не загрузилась: ${t.message}")
            return@withContext
        }

        val cores = Runtime.getRuntime().availableProcessors()
        val threadSet = listOf(2, 4).filter { it <= cores }.ifEmpty { listOf(cores) }
        val lengths = listOf(5, 30)
        val manager = WhisperModelManager(context)
        val rows = mutableListOf<Row>()

        log("Готовлю тестовый звук…")
        val source = synthSpeechLike(30)

        for (variant in WhisperModelVariant.entries) {
            log("")
            log("— Модель ${variant.name}")
            log("Проверяю модель, при первом запуске она скачивается (до 142 МБ)…")
            val file: File? = manager.ensureModelDownloaded(variant).getOrNull()
            if (file == null) {
                log("Модель не скачалась, пропускаю")
                continue
            }
            val loadStart = SystemClock.elapsedRealtime()
            val ctx = try {
                WhisperContext.createFromFile(file.absolutePath)
            } catch (t: Throwable) {
                log("Модель не загрузилась: ${t.message}")
                continue
            }
            log("Загрузка модели в память: ${SystemClock.elapsedRealtime() - loadStart} мс")
            try {
                val warmStart = SystemClock.elapsedRealtime()
                ctx.transcribe(source.copyOf(5 * SAMPLE_RATE), "ru", threadSet.first())
                log("Прогрев (не в зачёт): ${fmt((SystemClock.elapsedRealtime() - warmStart) / 1000.0)} с")

                for (threads in threadSet) {
                    for (len in lengths) {
                        val pcm = source.copyOf(len * SAMPLE_RATE)
                        val start = SystemClock.elapsedRealtime()
                        val segments = ctx.transcribe(pcm, "ru", threads)
                        val ms = SystemClock.elapsedRealtime() - start
                        rows += Row(variant, threads, len, ms)
                        val speed = len * 1000.0 / ms
                        log(
                            "${variant.name} потоков=$threads фрагмент=$len с: " +
                                "${fmt(ms / 1000.0)} с на обработку, скорость x${fmt(speed)} (сегментов ${segments.size})",
                        )
                    }
                }
            } catch (t: Throwable) {
                log("Ошибка во время замера: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                ctx.release()
            }
        }

        log("")
        log("=== ИТОГ ===")
        log("x1.0 = обработка за то же время, что длится речь. Видео, идущее параллельно, отнимет часть процессора, поэтому нужен запас.")
        for (variant in WhisperModelVariant.entries) {
            for (threads in threadSet) {
                val short = rows.firstOrNull { it.variant == variant && it.threads == threads && it.lenSec == 5 }
                    ?: continue
                val speed = short.lenSec * 1000.0 / short.ms
                val verdict = when {
                    speed >= 2.0 -> "запас есть"
                    speed >= 1.0 -> "на грани"
                    else -> "не успевает"
                }
                log("${variant.name} потоков=$threads, фраза 5 с: x${fmt(speed)} — $verdict")
            }
        }
        log("Замер закончен. Сделайте снимок экрана и пришлите.")
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun deviceInfo(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val freq = try {
            File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000
        } catch (e: Exception) {
            -1L
        }
        return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
            "ABI: ${Build.SUPPORTED_ABIS.joinToString()}, процесс ${if (Process.is64Bit()) "64" else "32"}-бит\n" +
            "ядер: ${Runtime.getRuntime().availableProcessors()}, макс. частота ядра 0: ${if (freq > 0) "$freq МГц" else "н/д"}\n" +
            "память всего: ${mem.totalMem / 1_048_576} МБ, свободно: ${mem.availMem / 1_048_576} МБ"
    }

    /**
     * Звук, похожий на речь по спектру и слоговому ритму (не настоящая речь).
     * Время работы энкодера Whisper от содержания почти не зависит, поэтому
     * замер показывает основную часть стоимости. Реальная речь добавит ещё
     * время на декодер (несколько процентов для tiny, больше для base).
     */
    private fun synthSpeechLike(seconds: Int): FloatArray {
        val n = seconds * SAMPLE_RATE
        val out = FloatArray(n)
        val rnd = java.util.Random(42)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val f0 = 120.0 + 30.0 * sin(2 * PI * 0.4 * t)
            phase += 2 * PI * f0 / SAMPLE_RATE
            var s = 0.0
            for (k in 1..12) s += sin(k * phase) / k
            val env = max(0.0, sin(2 * PI * 3.0 * t)).pow(0.5)
            out[i] = (0.25 * env * s + 0.005 * (rnd.nextDouble() * 2 - 1)).toFloat()
        }
        return out
    }
}
