package com.platinum.ott.presentation.screens.sync

import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithOnSurface
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.core.platform.isTV
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.platinum.ott.sync.CategoryReport
import com.platinum.ott.sync.DeviceInfo
import com.platinum.ott.sync.SyncReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.platinum.ott.ui.theme.*

/**
 * «Мои устройства»: один экран на TV и телефон (обычный material3, фокус пультом работает штатно).
 * Сверху — это устройство и синхронизация через сервер (отчёт, автосинхронизация), ниже — список
 * связанных устройств с подтверждением получения, добавление устройства по коду, и в конце прежняя
 * секция локальной синхронизации по Wi-Fi (она отдельный канал и пока без изменений).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncPairingScreen(
    onBackPressed: () -> Unit,
    // Только сторона телефона вызывает (кнопка «Сканировать QR с TV» в секции локальной синхронизации).
    onOpenLocalSyncScan: () -> Unit = {},
    viewModel: SyncPairingViewModel = hiltViewModel(),
    localSyncViewModel: LocalSyncViewModel = hiltViewModel()
) {
    val devicesState by viewModel.devices.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val redeemState by viewModel.redeemState.collectAsStateWithLifecycle()
    val pairingState by viewModel.pairingState.collectAsStateWithLifecycle()
    val autoSync by viewModel.autoSync.collectAsStateWithLifecycle()
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()
    val linksLost by viewModel.linksLost.collectAsStateWithLifecycle()
    val lastSyncedAtMs by viewModel.lastSyncedAtMs.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    var enteredCode by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<DeviceInfo?>(null) }
    var unlinkTarget by remember { mutableStateOf<DeviceInfo?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Мои устройства") })
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
                "Свяжите свои устройства, чтобы на всех было одно и то же избранное и история просмотра.",
                color = ZenithOnSurface.copy(0.8f)
            )
            Spacer(Modifier.height(ZenithDimens.paddingS))

            if (linksLost) {
                Text(
                    "Связи с другими устройствами потеряны: это устройство было отвязано или данные на сервере сброшены. " +
                        "Оно зарегистрировано заново — свяжите устройства снова кодом ниже.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { viewModel.dismissLinksLost() }) { Text("Понятно") }
                Spacer(Modifier.height(ZenithDimens.paddingS))
            }

            // ---- Это устройство и синхронизация ----
            Text("Это устройство", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
            Text(
                if (deviceName.isNotBlank()) deviceName else "Имя появится после первой синхронизации",
                color = ZenithOnSurfaceVariant
            )
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoSync, onCheckedChange = { viewModel.setAutoSync(it) })
                Spacer(Modifier.width(ZenithDimens.paddingS))
                Column {
                    Text("Автосинхронизация через сервер", color = ZenithOnSurface)
                    Text(
                        "Избранное и история — раз в 30 минут и вскоре после изменений",
                        color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text(
                if (lastSyncedAtMs > 0) "Последняя синхронизация: ${formatTime(lastSyncedAtMs)}"
                else "Синхронизация ещё ни разу не выполнялась на этом устройстве",
                color = if (lastSyncedAtMs > 0) ZenithSuccess else ZenithOnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            when (val st = syncState) {
                is SyncRunState.Running -> Text("Синхронизация…", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                is SyncRunState.Failed -> Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                is SyncRunState.Idle -> {}
            }
            Button(onClick = { viewModel.syncNow() }, enabled = syncState !is SyncRunState.Running) {
                Text("Синхронизировать сейчас")
            }
            reports.firstOrNull()?.let { latest ->
                Spacer(Modifier.height(ZenithDimens.paddingS))
                SyncReportCard(latest)
            }
            if (reports.size > 1) {
                Spacer(Modifier.height(ZenithDimens.paddingS))
                Text("Предыдущие синхронизации", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                reports.drop(1).take(5).forEach { r ->
                    Text(
                        "${formatTime(r.finishedAt)} · ${triggerLabel(r.trigger)} · " +
                            if (r.ok) "получено ${r.favorites.received + r.history.received}, отправлено ${r.favorites.sent + r.history.sent + r.favorites.deletedSent + r.history.deletedSent}"
                            else "ошибка: ${r.error ?: "—"}",
                        color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(Modifier.height(24.dp))

            // ---- Список устройств ----
            Text("Связанные устройства", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            when {
                !viewModel.isRegistered ->
                    Text("Список появится после первой синхронизации или связывания.", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                devicesState.loading && devicesState.devices.isEmpty() -> CircularProgressIndicator()
                else -> devicesState.devices.forEach { d ->
                    DeviceCard(
                        device = d,
                        onRename = { renameTarget = d },
                        onUnlink = { unlinkTarget = d }
                    )
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                }
            }
            devicesState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            actionMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.dismissActionMessage() }) { Text("Скрыть") }
            }
            if (viewModel.isRegistered) {
                TextButton(onClick = { viewModel.refreshDevices() }) { Text("Обновить список") }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(Modifier.height(24.dp))

            // ---- Добавить устройство ----
            Text("Добавить устройство", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text("На этом устройстве покажите код — и введите его на другом:", color = ZenithOnSurfaceVariant)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            when (val state = pairingState) {
                is PairingUiState.CodeShown -> {
                    Text(state.code, color = MaterialTheme.colorScheme.primary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Text(
                        "Истекает через ${state.secondsLeft / 60}:${(state.secondsLeft % 60).toString().padStart(2, '0')}",
                        color = ZenithOnSurfaceVariant
                    )
                }
                is PairingUiState.Loading -> CircularProgressIndicator()
                is PairingUiState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
                }
                is PairingUiState.Idle -> Button(onClick = { viewModel.createCode() }) { Text("Показать код") }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))
            Text("Или введите код, показанный на другом устройстве:", color = ZenithOnSurfaceVariant)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            OutlinedTextField(
                value = enteredCode,
                onValueChange = {
                    // Только буквы и дефис, регистр не важен; 8 букв + дефис.
                    val cleaned = it.uppercase().filter { c -> c.isLetter() || c == '-' }
                    if (cleaned.length <= 9) {
                        enteredCode = cleaned
                        if (redeemState !is RedeemUiState.Idle) viewModel.resetRedeem()
                    }
                },
                label = { Text("Код из 8 букв, например KFMQ-TNDT") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                singleLine = true
            )
            Spacer(Modifier.height(ZenithDimens.paddingS))
            // Кнопка активна при любом непустом вводе: нажатие всегда даёт видимую реакцию (проверку делает ViewModel).
            Button(onClick = { viewModel.redeemCode(enteredCode) }, enabled = enteredCode.isNotEmpty() && redeemState !is RedeemUiState.Loading) {
                Text("Подключить")
            }
            Spacer(Modifier.height(ZenithDimens.paddingS))
            when (val state = redeemState) {
                is RedeemUiState.Loading -> CircularProgressIndicator()
                is RedeemUiState.Success -> Text(state.message, color = ZenithSuccess)
                is RedeemUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is RedeemUiState.Idle -> {}
            }

            Spacer(Modifier.height(40.dp))
            HorizontalDivider(color = Color.DarkGray)
            Spacer(Modifier.height(40.dp))

            // PROMPT_LOCAL_SYNC_V1.md — второй, независимый канал (см.
            // обоснование в LocalSyncDtos.kt/LocalSyncRepository.kt): напрямую
            // между устройствами по локальной сети, без бэкенда, зато
            // переносит больше (настройки, плагины, список источников), не
            // только избранное и историю, как секции выше.
            Text("Локальная синхронизация (без интернета)", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            Text(
                "Избранное, история, настройки, источники и плагины — напрямую между устройствами в одной Wi-Fi сети. Оба устройства должны быть физически рядом.",
                color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(ZenithDimens.paddingM))

            val context = LocalContext.current
            val isTv = remember { isTV(context) }
            val tvState by localSyncViewModel.tvState.collectAsStateWithLifecycle()

            if (isTv) {
                when (val state = tvState) {
                    is LocalSyncTvState.Idle -> Button(onClick = { localSyncViewModel.startTv() }) { Text("Начать") }
                    is LocalSyncTvState.Showing -> {
                        Text(state.code, color = MaterialTheme.colorScheme.primary, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        Text(
                            "Истекает через ${state.secondsLeft / 60}:${(state.secondsLeft % 60).toString().padStart(2, '0')}",
                            color = ZenithOnSurfaceVariant
                        )
                        Spacer(Modifier.height(ZenithDimens.paddingM))
                        val bitmap = remember(state.qrContent) { generateLocalSyncQrBitmap(state.qrContent, 260) }
                        if (bitmap != null) {
                            Image(
                                bitmap.asImageBitmap(), contentDescription = "QR-код",
                                modifier = Modifier.size(260.dp).background(ZenithOnSurface).padding(ZenithDimens.paddingS)
                            )
                        }
                        Spacer(Modifier.height(ZenithDimens.paddingM))
                        Text(
                            "На телефоне: «Мои устройства» → «Сканировать QR с TV», затем введите код выше вручную.",
                            color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        OutlinedButton(onClick = { localSyncViewModel.stopTv() }) { Text("Отмена") }
                    }
                    is LocalSyncTvState.Applied -> {
                        Text(
                            "Готово! Перенесено: ${state.summary.favorites} избранного, ${state.summary.history} записей истории, " +
                                "${state.summary.sources} источников, ${state.summary.plugins} плагинов.",
                            color = ZenithSuccess
                        )
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        Button(onClick = { localSyncViewModel.resetTv() }) { Text("Ок") }
                    }
                    is LocalSyncTvState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        Button(onClick = { localSyncViewModel.startTv() }) { Text("Повторить") }
                    }
                }
            } else {
                Text("Отсканируйте QR-код, показанный на TV в этом же разделе.", color = ZenithOnSurfaceVariant)
                Spacer(Modifier.height(ZenithDimens.paddingS))
                Button(onClick = onOpenLocalSyncScan) { Text("Сканировать QR с TV") }
            }

            Spacer(Modifier.height(ZenithDimens.paddingL))
        }
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать устройство") },
            text = { OutlinedTextField(value = text, onValueChange = { if (it.length <= 64) text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { viewModel.renameDevice(target.id, text); renameTarget = null }, enabled = text.isNotBlank()) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } }
        )
    }
    unlinkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unlinkTarget = null },
            title = { Text("Отвязать устройство?") },
            text = {
                Text(
                    if (target.isCurrent) "Это устройство перестанет синхронизироваться с остальными. Данные на нём останутся."
                    else "«${target.name}» перестанет получать ваши избранное и историю. Данные на нём останутся."
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.unlinkDevice(target.id); unlinkTarget = null }) { Text("Отвязать") } },
            dismissButton = { TextButton(onClick = { unlinkTarget = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, onRename: () -> Unit, onUnlink: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ZenithDimens.paddingM)) {
            Text(
                device.name + if (device.isCurrent) " (это устройство)" else "",
                color = ZenithOnSurface, fontWeight = FontWeight.Bold
            )
            Text(typeLabel(device.type) + " · " + presence(device), color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(
                if (device.lastSyncAt > 0) "Синхронизировалось: ${formatTime(device.lastSyncAt)}" else "Ещё не синхронизировалось",
                color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            device.ackAt?.let { at ->
                val applied = "избранное ${device.ackApplied["favorite"] ?: 0}, история ${device.ackApplied["history"] ?: 0}"
                val ok = device.ackMatchesServer == true
                Text(
                    (if (ok) "✓ Подтвердило получение: " else "⚠ Получило, но данные ещё расходятся: ") + "$applied (${formatTime(at)})",
                    color = if (ok) ZenithSuccess else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                TextButton(onClick = onRename) { Text("Переименовать") }
                TextButton(onClick = onUnlink) { Text(if (device.isCurrent) "Отвязать это устройство" else "Отвязать") }
            }
        }
    }
}

@Composable
private fun SyncReportCard(r: SyncReport) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ZenithDimens.paddingM)) {
            Text(
                "Отчёт: ${formatTime(r.finishedAt)} · ${triggerLabel(r.trigger)}",
                color = ZenithOnSurface, fontWeight = FontWeight.Bold
            )
            if (!r.ok) {
                Text("Не удалось: ${r.error ?: "ошибка"}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            Text(categoryLine("Избранное", r.favorites), color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(categoryLine("История", r.history), color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(
                "Сервер принял: новых ${r.serverAccepted}, обновил ${r.serverUpdated}, без изменений ${r.serverIgnored}" +
                    if (r.serverRejected > 0) ", отклонил ${r.serverRejected}" else "",
                color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            when (r.checksumMatches) {
                true -> Text("✓ Данные на этом устройстве совпадают с серверными", color = ZenithSuccess, style = MaterialTheme.typography.bodySmall)
                false -> Text(
                    "⚠ Данные расходятся с серверными: другое устройство могло ещё не отправить изменения — повторите синхронизацию",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
                null -> Text("Подтверждение от сервера не получено", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            r.notes.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun categoryLine(title: String, c: CategoryReport): String {
    val sent = c.sent + c.deletedSent
    val sentPart = if (c.deletedSent > 0) "отправлено $sent (удалений ${c.deletedSent})" else "отправлено $sent"
    val received = "получено ${c.received}" +
        if (c.received > 0) " (новых ${c.added}, обновлено ${c.updated}, удалено ${c.deleted}, пропущено ${c.skipped})" else ""
    return "$title: $sentPart; $received" + if (c.errors > 0) "; ошибок ${c.errors}" else ""
}

private fun triggerLabel(trigger: String) = when (trigger) {
    "auto" -> "автоматически"
    "pairing" -> "после связывания"
    else -> "вручную"
}

private fun typeLabel(type: String) = when (type) {
    "tv" -> "ТВ"
    "phone" -> "Телефон"
    "tablet" -> "Планшет"
    else -> "Устройство"
}

/** «В сети» — если сервер видел устройство за последние 5 минут (оно обращается к серверу только при синхронизации). */
private fun presence(d: DeviceInfo): String {
    if (d.lastSeenAt <= 0) return "не было на связи"
    return if (System.currentTimeMillis() - d.lastSeenAt < 5 * 60_000) "на связи" else "было на связи ${formatTime(d.lastSeenAt)}"
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))


// Тот же helper, что и в presentation/screens/qr/QrScanScreen.kt — не
// переиспользован оттуда напрямую (тот файл — TV/tv-material3, этот экран
// сознательно на обычном material3, см. комментарий в шапке файла), но
// реализация идентична: ZXing QRCodeWriter → Bitmap пиксель за пикселем.
private fun generateLocalSyncQrBitmap(content: String, sizePx: Int): Bitmap? = try {
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    bitmap
} catch (_: Exception) {
    null
}
