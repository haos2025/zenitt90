package com.platinum.ott.presentation.phone.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// v2 телефон-компаньона (ROADMAP.md п.6, PROMPT_PHONE_COMPANION.md) — два
// применения одного канала: внешние субтитры ("/subtitle") и поиск текстом
// ("/search"), см. QrScanScreen.kt/CompanionHttpServer.kt на стороне TV.
// QR-код кодирует "http://ip:port#режим" — суффикс после "#" говорит
// телефону, какой из двух экранов показать и на какой путь слать POST, без
// отдельного QR-формата/JSON ради одного слова. Раньше здесь была заглушка
// под CameraX+ML Kit (убраны, дублировали ZXing — см. app/build.gradle.kts)
// — сканирование через ScanContract из уже подключённого
// zxing-android-embedded: готовая Activity со своей камерой и запросом
// runtime-permission CAMERA, свой UI камеры не нужен.
private enum class CompanionMode(val endpointPath: String, val fieldLabel: String, val hint: String, val doneMessage: String) {
    SUBTITLE("/subtitle", "Ссылка на субтитры", "Вставьте ссылку на субтитры (.srt)", "Субтитры подключены на TV"),
    SEARCH("/search", "Текст для поиска", "Введите, что искать", "Запрос отправлен на TV")
}

private sealed interface CompanionState {
    object Scanning : CompanionState
    data class Connected(val baseUrl: String, val mode: CompanionMode) : CompanionState
    data class Sent(val mode: CompanionMode) : CompanionState
    data class Failed(val message: String) : CompanionState
}

@Composable
fun PhoneQrScanScreen(navController: NavHostController) {
    var state by remember { mutableStateOf<CompanionState>(CompanionState.Scanning) }
    val scope = rememberCoroutineScope()
    // Короткоживущий одноразовый клиент для одного локального запроса —
    // общий OkHttpClient приложения настроен под таймауты/интерцепторы
    // стримингового бэкенда (см. SessionGraph), здесь ни один из них не
    // нужен: локальный запрос в той же Wi-Fi сети, доли секунды, без ретраев.
    val httpClient = remember { OkHttpClient() }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (scanned.isNullOrBlank()) {
            // Отмена сканирования (кнопка "назад" в самой Activity сканера) —
            // некуда вести дальше на этом экране, возвращаемся сразу.
            navController.popBackStack()
        } else {
            val raw = scanned.trimEnd('/')
            val hashIdx = raw.indexOf('#')
            val baseUrl = if (hashIdx >= 0) raw.substring(0, hashIdx) else raw
            // SUBTITLE — дефолт для QR без суффикса (обратная совместимость
            // с v1, если где-то остался старый формат без "#режим").
            val mode = when (if (hashIdx >= 0) raw.substring(hashIdx + 1) else "") {
                "search" -> CompanionMode.SEARCH
                else -> CompanionMode.SUBTITLE
            }
            state = CompanionState.Connected(baseUrl, mode)
        }
    }
    LaunchedEffect(Unit) {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Наведите камеру на QR-код на экране TV")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (val s = state) {
            is CompanionState.Scanning -> {
                Text("Сканирование QR-кода", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text("Наведите камеру на QR-код на экране TV", color = Color.Gray)
            }
            is CompanionState.Connected -> {
                var value by remember { mutableStateOf("") }
                var sending by remember { mutableStateOf(false) }
                Text("TV найден", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(s.mode.hint, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = value, onValueChange = { value = it },
                    label = { Text(s.mode.fieldLabel) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = value.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        scope.launch {
                            val ok = sendValue(httpClient, s.baseUrl, s.mode.endpointPath, value.trim())
                            sending = false
                            state = if (ok) CompanionState.Sent(s.mode)
                            else CompanionState.Failed("Не удалось отправить — проверьте, что телефон и TV в одной сети")
                        }
                    }
                ) { Text(if (sending) "Отправка..." else "Отправить на TV") }
            }
            is CompanionState.Sent -> {
                Text("✓ Отправлено", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(s.mode.doneMessage, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { navController.popBackStack() }) { Text("Готово") }
            }
            is CompanionState.Failed -> {
                Text("⚠ Ошибка", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(s.message, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { state = CompanionState.Scanning }) { Text("Отсканировать заново") }
            }
        }
    }
}

private suspend fun sendValue(client: OkHttpClient, baseUrl: String, endpointPath: String, value: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val body = value.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder().url("$baseUrl$endpointPath").post(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
