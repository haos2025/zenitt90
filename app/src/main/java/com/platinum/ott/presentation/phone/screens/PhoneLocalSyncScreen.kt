package com.platinum.ott.presentation.phone.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.platinum.ott.presentation.screens.sync.LocalSyncPhoneState
import com.platinum.ott.presentation.screens.sync.LocalSyncViewModel

/**
 * PROMPT_LOCAL_SYNC_V1.md, сторона телефона. Не переиспользует
 * PhoneQrScanScreen.kt/CompanionMode — тот экран рассчитан на один текстовый
 * POST без защиты кодом (см. его собственные комментарии в шапке файла);
 * здесь нужен второй шаг (ручной ввод кода перед обменом) и JSON-обмен
 * через LocalSyncViewModel/LocalSyncRepository, а не голая строка. QR здесь
 * кодирует только "http://ip:port" — без "#режим", код передаётся
 * отдельно, вручную (см. обоснование в SyncPairingScreen.kt/PROMPT_LOCAL_SYNC_V1.md
 * — защита кодом, а не просто "телефон отсканировал QR").
 */
private sealed interface ScanState {
    object Scanning : ScanState
    data class Connected(val baseUrl: String) : ScanState
}

@Composable
fun PhoneLocalSyncScreen(navController: NavHostController, viewModel: LocalSyncViewModel = hiltViewModel()) {
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    val phoneState by viewModel.phoneState.collectAsStateWithLifecycle()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (scanned.isNullOrBlank()) {
            navController.popBackStack()
        } else {
            state = ScanState.Connected(scanned.trimEnd('/'))
        }
    }
    LaunchedEffect(Unit) {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Наведите камеру на QR-код в разделе «Синхронизация устройств» на TV")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (val s = state) {
            is ScanState.Scanning -> {
                Text("Сканирование QR-кода", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text("Наведите камеру на QR-код на экране TV", color = Color.Gray)
            }
            is ScanState.Connected -> {
                var code by remember { mutableStateOf("") }
                when (val ps = phoneState) {
                    is LocalSyncPhoneState.Idle, is LocalSyncPhoneState.Error -> {
                        Text("TV найден", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Введите код, показанный на TV рядом с QR-кодом:", color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                            label = { Text("6-значный код") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            enabled = code.isNotEmpty(),
                            onClick = { viewModel.syncFromPhone(s.baseUrl, code) }
                        ) { Text("Синхронизировать") }
                        if (ps is LocalSyncPhoneState.Error) {
                            Spacer(Modifier.height(12.dp))
                            Text(ps.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is LocalSyncPhoneState.Connecting -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Синхронизация...", color = Color.Gray)
                    }
                    is LocalSyncPhoneState.Success -> {
                        Text("✓ Готово", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Перенесено: ${ps.summary.favorites} избранного, ${ps.summary.history} записей истории, " +
                                "${ps.summary.sources} источников, ${ps.summary.plugins} плагинов.",
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { navController.popBackStack() }) { Text("Готово") }
                    }
                }
            }
        }
    }
}
