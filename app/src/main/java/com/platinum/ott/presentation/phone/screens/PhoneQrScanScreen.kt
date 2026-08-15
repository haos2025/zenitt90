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

// v1 телефон-компаньона (ROADMAP.md п.6, PROMPT_PHONE_COMPANION.md) —
// первое применение: внешние субтитры по ссылке (см. QrScanScreen.kt на
// стороне TV и CompanionHttpServer.kt). Раньше здесь была заглушка под
// CameraX+ML Kit (убраны, дублировали ZXing — см. app/build.gradle.kts) —
// сканирование теперь через ScanContract из уже подключённого
// zxing-android-embedded: готовая Activity со своей камерой и запросом
// runtime-permission CAMERA, свой UI камеры не нужен.
private sealed interface CompanionState {
    object Scanning : CompanionState
    data class Connected(val baseUrl: String) : CompanionState
    object Sent : CompanionState
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
            state = CompanionState.Connected(scanned.trimEnd('/'))
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
                var subtitleUrl by remember { mutableStateOf("") }
                var sending by remember { mutableStateOf(false) }
                Text("TV найден", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("Вставьте ссылку на субтитры (.srt)", color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = subtitleUrl, onValueChange = { subtitleUrl = it },
                    label = { Text("Ссылка на субтитры") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = subtitleUrl.isNotBlank() && !sending,
                    onClick = {
                        sending = true
                        scope.launch {
                            val ok = sendSubtitleUrl(httpClient, s.baseUrl, subtitleUrl.trim())
                            sending = false
                            state = if (ok) CompanionState.Sent
                            else CompanionState.Failed("Не удалось отправить — проверьте, что телефон и TV в одной сети")
                        }
                    }
                ) { Text(if (sending) "Отправка..." else "Отправить на TV") }
            }
            is CompanionState.Sent -> {
                Text("✓ Отправлено", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text("Субтитры подключены на TV", color = Color.Gray)
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

private suspend fun sendSubtitleUrl(client: OkHttpClient, baseUrl: String, subtitleUrl: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val body = subtitleUrl.toRequestBody("text/plain".toMediaType())
            val request = Request.Builder().url("$baseUrl/subtitle").post(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
