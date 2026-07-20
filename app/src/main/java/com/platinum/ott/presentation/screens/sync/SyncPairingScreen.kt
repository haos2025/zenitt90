package com.platinum.ott.presentation.screens.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Один экран на TV и на телефон, а не пара TV/Phone-вариантов как у
 * остальных экранов приложения — сознательное отступление: это редко
 * используемая служебная функция (ввёл код один раз при первой настройке
 * второго устройства и забыл), не основной путь навигации, где важна
 * TV-специфичная стилистика (androidx.tv.material3). Обычный
 * androidx.compose.material3 нормально фокусируется пультом через
 * стандартную систему фокуса Compose, просто визуально не "TV-нативный".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPairingScreen(onBackPressed: () -> Unit, viewModel: SyncPairingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var enteredCode by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Синхронизация устройств") }, navigationIcon = {
            TextButton(onClick = onBackPressed) { Text("Назад") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().background(Color(0xFF101010)).padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Подключите ещё одно своё устройство, чтобы видеть одно и то же избранное и историю просмотра на обоих.",
                color = Color.White.copy(0.8f)
            )
            Spacer(Modifier.height(32.dp))

            Text("На новом устройстве", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Введите код, показанный на уже настроенном устройстве:", color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = enteredCode,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) enteredCode = it },
                label = { Text("6-значный код") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.redeemCode(enteredCode) }, enabled = enteredCode.length == 6) {
                Text("Подключить")
            }

            Spacer(Modifier.height(40.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(Modifier.height(40.dp))

            Text("На уже настроенном устройстве", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Покажите код здесь и введите его на новом устройстве:", color = Color.Gray)
            Spacer(Modifier.height(16.dp))

            when (val state = uiState) {
                is PairingUiState.CodeShown -> {
                    Text(
                        state.code,
                        color = Color(0xFF6C63FF),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Истекает через ${state.secondsLeft / 60}:${(state.secondsLeft % 60).toString().padStart(2, '0')}", color = Color.Gray)
                }
                is PairingUiState.Loading -> CircularProgressIndicator()
                is PairingUiState.RedeemSuccess -> Text("Готово! Устройства синхронизированы.", color = Color(0xFF4CAF50))
                is PairingUiState.Error -> {
                    Text(state.message, color = Color(0xFFFF6B6B))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
                }
                is PairingUiState.Idle -> Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
            }
        }
    }
}
