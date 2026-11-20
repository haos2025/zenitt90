package com.platinum.ott.presentation.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithSuccess

/**
 * «Связать устройство»: один блок вместо двух пар «показать код / ввести код» (сервер и Wi-Fi).
 * Один и тот же код работает обоими способами; переключатель «Автосвязь по Wi-Fi» решает, связывать ли заодно и по Wi-Fi.
 */
@Composable
fun PairingHubSection(hub: PairingHubViewModel, local: LocalSyncViewModel, onLinked: () -> Unit) {
    val show by hub.show.collectAsStateWithLifecycle()
    val enter by hub.enter.collectAsStateWithLifecycle()
    val autoWifi by hub.autoWifi.collectAsStateWithLifecycle()
    val nearby by local.state.collectAsStateWithLifecycle()
    val manualAddress by local.manualAddress.collectAsStateWithLifecycle()

    var entered by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }

    // Код погас (время вышло, ввели на втором устройстве) — за это время к группе могли присоединиться: обновляем список.
    LaunchedEffect(show is HubShowState.Idle) { if (show is HubShowState.Idle) onLinked() }
    LaunchedEffect(enter is HubEnterState.Success) { if (enter is HubEnterState.Success) onLinked() }

    Column(Modifier.fillMaxWidth()) {
        Text("Связать устройство", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(ZenithDimens.paddingS))
        Text(
            "Один код для обоих способов: через сервер (из любой сети) и по Wi-Fi (дома, без интернета).",
            color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(ZenithDimens.paddingS))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = autoWifi, onCheckedChange = { hub.setAutoWifi(it) })
            Spacer(Modifier.width(ZenithDimens.paddingS))
            Column {
                Text("Автосвязь по Wi-Fi", color = ZenithOnSurface)
                Text(
                    if (autoWifi) "Если второе устройство рядом, связать и по Wi-Fi (быстрая передача без интернета)"
                    else "Связывать только через сервер; по Wi-Fi — лишь если сервер недоступен",
                    color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(ZenithDimens.paddingM))

        // ---- показать код
        Text("На этом устройстве покажите код — и введите его на другом:", color = ZenithOnSurfaceVariant)
        Spacer(Modifier.height(ZenithDimens.paddingS))
        when (val s = show) {
            is HubShowState.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            is HubShowState.Shown -> {
                Text(s.code.code, color = MaterialTheme.colorScheme.primary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Истекает через ${s.code.secondsLeft / 60}:${(s.code.secondsLeft % 60).toString().padStart(2, '0')} · " +
                        "сервер: ${if (s.code.server) "да" else "нет"} · Wi-Fi: ${if (s.code.wifi) "да" else "нет"}",
                    color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { hub.hideCode(local) }) { Text("Скрыть код") }
            }
            is HubShowState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = { hub.showCode(local) }) { Text("Показать код") }
            }
            is HubShowState.Idle -> Button(onClick = { hub.showCode(local) }) { Text("Показать код") }
        }

        // ---- ввести код
        Spacer(Modifier.height(ZenithDimens.paddingM))
        Text("Или введите код, показанный на другом устройстве:", color = ZenithOnSurfaceVariant)
        Spacer(Modifier.height(ZenithDimens.paddingS))
        OutlinedTextField(
            value = entered,
            onValueChange = {
                // Только буквы и дефис, регистр не важен; 8 букв + дефис.
                val cleaned = it.uppercase().filter { c -> c.isLetter() || c == '-' }
                if (cleaned.length <= 9) { entered = cleaned; hub.resetEnter() }
            },
            label = { Text("Код из 8 букв, например KFMQ-TNDT") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            singleLine = true
        )
        Spacer(Modifier.height(ZenithDimens.paddingS))
        Button(
            onClick = { hub.enterCode(entered, local, manualAddress) },
            enabled = entered.isNotEmpty() && enter !is HubEnterState.Working
        ) { Text("Подключить") }
        Spacer(Modifier.height(ZenithDimens.paddingS))
        when (val e = enter) {
            is HubEnterState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(ZenithDimens.paddingS))
                Text(e.text, color = ZenithOnSurfaceVariant)
            }
            is HubEnterState.Success -> Text(e.message, color = ZenithSuccess)
            is HubEnterState.Error -> Text(e.message, color = MaterialTheme.colorScheme.error)
            is HubEnterState.Idle -> {}
        }

        // ---- дополнительно: адрес вручную (если устройство не находится само)
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Дополнительно ▴" else "Дополнительно ▾") }
        if (advanced) {
            Text(
                "Устройство не находится по Wi-Fi? Введите его адрес — код тот же. Этот адрес используется и для отправки данных и ввода на устройстве.",
                color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = manualAddress, onValueChange = { local.setManualAddress(it) },
                label = { Text("Адрес, например 192.168.1.5") }, singleLine = true
            )
            nearby.address?.let {
                Text("Адрес этого устройства: $it", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
