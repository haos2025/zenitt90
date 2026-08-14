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
import com.platinum.ott.core.platform.ZenithDimens
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.platinum.ott.ui.theme.*

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
fun SyncPairingScreen(onBackPressed: () -> Unit, viewModel: SyncPairingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastSyncedAtMs by viewModel.lastSyncedAtMs.collectAsStateWithLifecycle()
    var enteredCode by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Синхронизация устройств") }, navigationIcon = {
            TextButton(onClick = onBackPressed) { Text("Назад") }
        })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).padding(ZenithDimens.paddingL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Подключите ещё одно своё устройство, чтобы видеть одно и то же избранное и историю просмотра на обоих.",
                color = Color.White.copy(0.8f)
            )
            Spacer(Modifier.height(ZenithDimens.paddingSM))
            // Раньше единственным индикатором того, что синхронизация вообще
            // произошла, была мимолётная надпись "Готово!" сразу после
            // действия — уйдя с экрана, узнать статус было неоткуда, из-за
            // чего было неясно, работает ли синхронизация вообще. Строка
            // ниже видна всегда, независимо от текущего Idle/Loading/Error.
            Text(
                if (lastSyncedAtMs > 0)
                    "Последняя синхронизация: ${SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(lastSyncedAtMs))}"
                else "Синхронизация ещё ни разу не выполнялась на этом устройстве",
                color = if (lastSyncedAtMs > 0) ZenithSuccess else Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(20.dp))

            Text("На новом устройстве", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text("Введите код, показанный на уже настроенном устройстве:", color = Color.Gray)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            OutlinedTextField(
                value = enteredCode,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) enteredCode = it },
                label = { Text("6-значный код") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Button(onClick = { viewModel.redeemCode(enteredCode) }, enabled = enteredCode.length == 6) {
                Text("Подключить")
            }

            Spacer(Modifier.height(40.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(Modifier.height(40.dp))

            Text("На уже настроенном устройстве", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text("Покажите код здесь и введите его на новом устройстве:", color = Color.Gray)
            Spacer(Modifier.height(ZenithDimens.paddingM))

            when (val state = uiState) {
                is PairingUiState.CodeShown -> {
                    Text(
                        state.code,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Text("Истекает через ${state.secondsLeft / 60}:${(state.secondsLeft % 60).toString().padStart(2, '0')}", color = Color.Gray)
                }
                is PairingUiState.Loading -> CircularProgressIndicator()
                is PairingUiState.RedeemSuccess -> {
                    Text("Готово! Избранное и история перенесены.", color = ZenithSuccess)
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Button(onClick = { viewModel.syncNowManually() }) { Text("Синхронизировать сейчас") }
                }
                is PairingUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
                }
                is PairingUiState.Idle -> Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
            }
        }
    }
}
