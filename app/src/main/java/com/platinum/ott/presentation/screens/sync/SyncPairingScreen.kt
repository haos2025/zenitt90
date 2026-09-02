package com.platinum.ott.presentation.screens.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 *
 * Диагностика PROMPT_ACCOUNT_REDESIGN.md, шаг 1 (репорт "жмёшь Подключить —
 * ничего не происходит, ни ошибок", одинаково на TV и телефоне): было два
 * реальных бага, оба здесь.
 *  1) У Column не было verticalScroll — тот же класс бага, что уже чинили
 *     на TV в настройках (см. NEXT_STEPS.md). Экран показывает две полные
 *     секции подряд без скролла — на TV с overscan или на невысоком экране
 *     телефона нижняя часть могла просто не влезать в видимую область.
 *  2) Индикатор для формы ввода кода (верх, "На новом устройстве") делил
 *     один uiState с показом своего кода (низ, "На уже настроенном
 *     устройстве") и рендерился ТОЛЬКО в нижней секции — визуально и по
 *     смыслу под чужим заголовком, никак не привязан к месту, где на него
 *     смотрел бы человек, только что нажавший "Подключить" наверху.
 * Оба исправлены: добавлен verticalScroll, состояния разведены на
 * redeemState (рендерится сразу под формой ввода кода) и pairingState
 * (как раньше, под показом своего кода). Кнопка "Синхронизировать сейчас"
 * сделана всегда видимой, не только сразу после первого сопряжения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPairingScreen(onBackPressed: () -> Unit, viewModel: SyncPairingViewModel = hiltViewModel()) {
    val redeemState by viewModel.redeemState.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    val manualSyncState by viewModel.manualSyncState.collectAsStateWithLifecycle()
    val lastSyncedAtMs by viewModel.lastSyncedAtMs.collectAsStateWithLifecycle()
    var enteredCode by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Синхронизация устройств") }, navigationIcon = {
            TextButton(onClick = onBackPressed) { Text("Назад") }
        })
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(ZenithDimens.paddingL),
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
            // ниже видна всегда, независимо от текущего состояния формы.
            Text(
                if (lastSyncedAtMs > 0)
                    "Последняя синхронизация: ${SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(lastSyncedAtMs))}"
                else "Синхронизация ещё ни разу не выполнялась на этом устройстве",
                color = if (lastSyncedAtMs > 0) ZenithSuccess else Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(ZenithDimens.paddingS))
            when (val ms = manualSyncState) {
                is RedeemUiState.Loading -> Text("Синхронизация...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                is RedeemUiState.Success -> Text("Синхронизировано только что", color = ZenithSuccess, style = MaterialTheme.typography.bodySmall)
                is RedeemUiState.Error -> Text(ms.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                is RedeemUiState.Idle -> {}
            }
            TextButton(onClick = { viewModel.syncNowManually() }, enabled = manualSyncState !is RedeemUiState.Loading) {
                Text("Синхронизировать сейчас")
            }
            Spacer(Modifier.height(20.dp))

            Text("На новом устройстве", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text("Введите код, показанный на уже настроенном устройстве:", color = Color.Gray)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            OutlinedTextField(
                value = enteredCode,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                        enteredCode = it
                        if (redeemState !is RedeemUiState.Idle) viewModel.resetRedeem()
                    }
                },
                label = { Text("6-значный код") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(Modifier.height(ZenithDimens.paddingS))
            // Диагностика PROMPT_ACCOUNT_REDESIGN.md, шаг 1 (продолжение):
            // проверка логов Render показала, что запрос /sync/pairing/redeem
            // НИ РАЗУ не доходил до бэкенда, хотя /sync/pairing/create с
            // другого устройства успешно отрабатывал дважды — то есть дело
            // не в бэкенде вообще, а в том, что сетевой вызов ни разу не
            // происходил на клиенте. Раньше кнопка была enabled только при
            // enteredCode.length == 6 — если по любой причине (особенность
            // ввода на пульте TV, поведение конкретной клавиатуры/IME)
            // enteredCode не набирал ровно 6 символов, кнопка молча
            // оставалась disabled: нажатие не производило вообще ничего —
            // ни сети, ни состояния, ни ошибки, потому что onClick физически
            // не вызывался. Теперь кнопка активна при непустом вводе, а
            // проверку "ровно 6 цифр" делает redeemCode() и показывает
            // понятную ошибку — нажатие теперь ГАРАНТИРОВАННО даёт видимую
            // реакцию, а не тихо ничего.
            Button(onClick = { viewModel.redeemCode(enteredCode) }, enabled = enteredCode.isNotEmpty() && redeemState !is RedeemUiState.Loading) {
                Text("Подключить")
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            // Реакция на "Подключить" теперь сразу здесь, а не в нижней
            // секции под чужим заголовком.
            when (val state = redeemState) {
                is RedeemUiState.Loading -> CircularProgressIndicator()
                is RedeemUiState.Success -> Text("Готово! Избранное и история перенесены.", color = ZenithSuccess)
                is RedeemUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is RedeemUiState.Idle -> {}
            }

            Spacer(Modifier.height(40.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(Modifier.height(40.dp))

            Text("На уже настроенном устройстве", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text("Покажите код здесь и введите его на новом устройстве:", color = Color.Gray)
            Spacer(Modifier.height(ZenithDimens.paddingM))

            when (val state = pairingState) {
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
                is PairingUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
                }
                is PairingUiState.Idle -> Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
        }
    }
}
