package com.platinum.ott.presentation.screens.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.sync.LocalSyncViewModel
import com.platinum.ott.presentation.screens.sync.PairingDialogs
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant

/**
 * «Ввод с телефона» — замена прежних QR-кодов (субтитры, поиск, ссылка на плагин, ссылка на M3U).
 * Устройство показывает, что ждёт текст; связанный телефон («Мои устройства» → «Ввести на устройстве»)
 * присылает его по защищённому каналу локальной синхронизации. Если телефон ещё не связан — на экране код
 * связывания и адрес для ручного ввода. Пока окно открыто, устройство видно в сети; при закрытии приём останавливается.
 *
 * Текст приходит в onText на главном потоке.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PhoneInputOverlay(
    mode: String,
    prompt: String,
    onText: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LocalSyncViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pair by vm.pair.collectAsStateWithLifecycle()
    val cancelFocus = remember { FocusRequester() }

    DisposableEffect(mode, prompt) {
        vm.beginInput(mode, prompt) { text -> onText(text) }
        onDispose {
            vm.endInput()
            vm.stopNearby()
        }
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }

    Box(modifier.background(Color.Black.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Ввод с телефона", style = MaterialTheme.typography.displaySmall, color = ZenithOnSurface)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text(prompt, color = ZenithOnSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Text(
                "На телефоне откройте Настройки → Мои устройства и нажмите «Ввести на устройстве» рядом с этим устройством. Оба должны быть в одной сети Wi-Fi.",
                color = ZenithOnSurfaceVariant, textAlign = TextAlign.Center
            )
            if (state.paired.isEmpty()) {
                Spacer(Modifier.height(ZenithDimens.paddingS))
                Text(
                    "Телефон ещё не связан с этим устройством: нажмите «Показать код» и введите его на телефоне (Мои устройства → Связать).",
                    color = ZenithOnSurfaceVariant, textAlign = TextAlign.Center
                )
            }
            val code = state.code
            if (code != null) {
                Spacer(Modifier.height(ZenithDimens.paddingM))
                Text(code, color = MaterialTheme.colorScheme.primary, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Код действует ${state.codeSecondsLeft / 60}:${(state.codeSecondsLeft % 60).toString().padStart(2, '0')}",
                    color = ZenithOnSurfaceVariant
                )
            }
            state.address?.let {
                Spacer(Modifier.height(ZenithDimens.paddingS))
                Text("Адрес для ручного ввода на телефоне: $it", color = ZenithOnSurfaceVariant)
            }
            state.notice?.let {
                Spacer(Modifier.height(ZenithDimens.paddingS))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM)) {
                if (code == null) {
                    Button(onClick = { vm.showCode() }, enabled = state.running) { Text("Показать код") }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus)) { Text("Отмена") }
            }
        }
    }

    PairingDialogs(pair, vm)
}
