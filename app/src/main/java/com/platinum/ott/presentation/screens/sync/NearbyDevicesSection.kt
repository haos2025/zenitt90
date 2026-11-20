package com.platinum.ott.presentation.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.sync.local.AcceptSet
import com.platinum.ott.sync.local.ApplyReport
import com.platinum.ott.sync.local.LocalProto
import com.platinum.ott.sync.local.NearbyPeer
import com.platinum.ott.sync.local.PairedDevice
import com.platinum.ott.sync.local.SendResult
import com.platinum.ott.sync.local.SendStage
import com.platinum.ott.ui.theme.ZenithOnSurface
import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import com.platinum.ott.ui.theme.ZenithSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Куда вводим код: найденное устройство или адрес, набранный вручную. */

/**
 * «Устройства рядом»: связывание по Wi-Fi (код из 8 букв + сверка контрольной строки) и передача данных
 * связанным устройствам с подтверждением на получателе. Приём работает, пока раздел на экране.
 */
@Composable
fun NearbyDevicesSection(vm: LocalSyncViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val pair by vm.pair.collectAsStateWithLifecycle()
    val incoming by vm.incoming.collectAsStateWithLifecycle()
    val send by vm.send.collectAsStateWithLifecycle()
    val received by vm.received.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()

    // Адрес вручную вводится в блоке «Связать устройство → Дополнительно» и общий для связывания, отправки и ввода.
    val manualAddress by vm.manualAddress.collectAsStateWithLifecycle()
    var forgetTarget by remember { mutableStateOf<PairedDevice?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.startNearby()
            else if (event == Lifecycle.Event.ON_STOP) vm.stopNearby()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopNearby()
        }
    }

    Text("Устройства рядом (по Wi-Fi, без интернета)", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(ZenithDimens.paddingS))
    Text(
        "Передача избранного, истории, источников, плагинов и настроек напрямую между устройствами в одной сети. " +
            "Данные шифруются, а получатель сам решает, что принять. Пока этот раздел открыт, устройство видно остальным.",
        color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(ZenithDimens.paddingS))
    if (state.running) {
        Text(
            "Адрес этого устройства для ручного ввода: ${state.address ?: "не определён (нет Wi-Fi?)"}",
            color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
    }
    state.notice?.let {
        Spacer(Modifier.height(ZenithDimens.paddingS))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { vm.dismissNotice() }) { Text("Понятно") }
    }
    Spacer(Modifier.height(ZenithDimens.paddingM))

    // ---- связанные устройства
    Text("Связанные устройства", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(ZenithDimens.paddingS))
    if (state.paired.isEmpty()) {
        Text("Пока нет. Свяжите устройство кодом в блоке «Связать устройство» выше.", color = ZenithOnSurfaceVariant)
    }
    for (d in state.paired) {
        val nearby = peers.any { it.deviceId == d.id }
        Card(Modifier.fillMaxWidth().padding(bottom = ZenithDimens.paddingS)) {
            Column(Modifier.padding(ZenithDimens.paddingM)) {
                Text(d.name, color = ZenithOnSurface, fontWeight = FontWeight.Bold)
                Text(
                    typeName(d.type) + " · " + if (nearby) "рядом" else "не найдено рядом",
                    color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
                if (d.lastTransferAt > 0) {
                    val mark = when (d.lastTransferOk) { true -> "✓"; false -> "⚠"; null -> "" }
                    Text(
                        "Последняя передача: ${fmt(d.lastTransferAt)} $mark",
                        color = if (d.lastTransferOk == false) MaterialTheme.colorScheme.error else ZenithOnSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    d.lastTransferNote?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                if (d.autoAcceptLight) {
                    Text("Избранное и история принимаются без вопросов", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                    Button(onClick = { vm.sendTo(d, manualAddress) }, enabled = send !is SendUi.Running) { Text("Отправить сюда") }
                    OutlinedButton(onClick = { vm.startInputTo(d, manualAddress) }) { Text("Ввести на устройстве") }
                    TextButton(onClick = { forgetTarget = d }) { Text("Забыть") }
                }
            }
        }
    }

    // ---- результат отправки / получения
    when (val s = send) {
        is SendUi.None -> {}
        is SendUi.Running -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(ZenithDimens.paddingS))
                Text(
                    when (s.stage) {
                        SendStage.OFFERING -> "Связываюсь с «${s.deviceName}»…"
                        SendStage.WAITING_CONFIRM -> "Ждём, пока «${s.deviceName}» подтвердит приём…"
                        SendStage.SENDING -> "Отправляю на «${s.deviceName}»…"
                    },
                    color = ZenithOnSurfaceVariant
                )
            }
        }
        is SendUi.Failed -> {
            Text("«${s.deviceName}»: ${s.message}", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { vm.dismissSend() }) { Text("Закрыть") }
        }
        is SendUi.Done -> {
            SendResultCard(s.result)
            TextButton(onClick = { vm.dismissSend() }) { Text("Закрыть") }
        }
    }
    received?.let { r ->
        Spacer(Modifier.height(ZenithDimens.paddingS))
        ReceivedCard(r)
        TextButton(onClick = { vm.dismissReceived() }) { Text("Закрыть") }
    }
    Spacer(Modifier.height(ZenithDimens.paddingM))

    // ---- найденные рядом, ещё не связанные
    val pairedIds = state.paired.map { it.id }.toSet()
    val unpaired = peers.filter { it.deviceId !in pairedIds }
    Text("Найдены рядом", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(ZenithDimens.paddingS))
    if (unpaired.isEmpty()) {
        Text(
            if (state.running) "Новых устройств не видно. На втором устройстве тоже откройте этот раздел." else "Поиск не запущен.",
            color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
    }
    for (p in unpaired) {
        Row(Modifier.fillMaxWidth().padding(bottom = ZenithDimens.paddingS), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = ZenithOnSurface)
                Text(typeName(p.type), color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(ZenithDimens.paddingM))

    if (unpaired.isNotEmpty()) {
        Text(
            "Чтобы связать найденное устройство, введите его код в блоке «Связать устройство» выше.",
            color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
        )
    }

    // ---------------------------------------------------------------- диалоги

    PairingDialogs(pair, vm)

    InputDialogs(input, vm)

    incoming?.let { offer ->
        val m = offer.manifest
        var fav by remember(offer.transferId) { mutableStateOf(true) }
        var hist by remember(offer.transferId) { mutableStateOf(true) }
        var src by remember(offer.transferId) { mutableStateOf(true) }
        var plg by remember(offer.transferId) { mutableStateOf(true) }
        var set by remember(offer.transferId) { mutableStateOf(true) }
        var hid by remember(offer.transferId) { mutableStateOf(true) }
        var pwd by remember(offer.transferId) { mutableStateOf(false) }
        var always by remember(offer.transferId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { vm.declineIncoming() },
            title = { Text("«${offer.deviceName}» хочет передать") },
            text = {
                Column {
                    if (m.favorites > 0) CheckRow("Избранное (${m.favorites})", fav) { fav = it }
                    if (m.history > 0) CheckRow("История просмотра (${m.history})", hist) { hist = it }
                    if (m.sources > 0) {
                        CheckRow("Источники (${m.sources}): ${m.sourceNames.joinToString(", ")}", src) { src = it }
                        if (m.sourcesWithCredentials > 0) {
                            CheckRow(
                                "Передать логины и пароли источников (у ${m.sourcesWithCredentials}). Без этого они придут выключенными, данные входа нужно будет ввести самому.",
                                pwd && src, enabled = src
                            ) { pwd = it }
                        }
                    }
                    if (m.plugins > 0) CheckRow("Плагины (${m.plugins}): ${m.pluginNames.joinToString(", ")}. Плагины — это программный код.", plg) { plg = it }
                    if (m.settings) CheckRow("Настройки интерфейса", set) { set = it }
                    if (m.hidden > 0) CheckRow("Скрытые каналы и категории (${m.hidden})", hid) { hid = it }
                    if (m.isLightOnly()) {
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        CheckRow("Всегда принимать избранное и историю от этого устройства", always) { always = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.acceptIncoming(
                        AcceptSet(
                            favorites = fav && m.favorites > 0, history = hist && m.history > 0,
                            sources = src && m.sources > 0, plugins = plg && m.plugins > 0,
                            settings = set && m.settings, hidden = hid && m.hidden > 0,
                            passwords = pwd && src && m.sources > 0
                        ),
                        alwaysLight = always && m.isLightOnly()
                    )
                }) { Text("Принять") }
            },
            dismissButton = { TextButton(onClick = { vm.declineIncoming() }) { Text("Отклонить") } }
        )
    }

    forgetTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { Text("Забыть устройство?") },
            text = { Text("«${d.name}» исчезнет из связанных на этом устройстве. Чтобы снова обмениваться данными, свяжите их заново.") },
            confirmButton = { TextButton(onClick = { vm.forget(d); forgetTarget = null }) { Text("Забыть") } },
            dismissButton = { TextButton(onClick = { forgetTarget = null }) { Text("Отмена") } }
        )
    }
}

/** Окна связывания: подключение, сверка контрольной строки, итог. Общие для «Мои устройства» и экранов «Ввод с телефона». */
@Composable
fun PairingDialogs(pair: PairUi, vm: LocalSyncViewModel) {
    when (val p = pair) {
        is PairUi.None -> {}
        is PairUi.Connecting -> AlertDialog(
            onDismissRequest = {}, title = { Text("Связывание") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(p.text) } },
            confirmButton = {}
        )
        is PairUi.Confirm -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Сверьте цифры") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(p.sas, color = MaterialTheme.colorScheme.primary, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    if (!p.selfDecided) {
                        Text("Те же четыре цифры должны быть на экране «${p.peerName}». Если они совпадают — подтвердите. Если нет, нажмите «Не совпадает»: возможно, в сети кто-то вмешивается.")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Ждём подтверждения на «${p.peerName}»…")
                        }
                    }
                }
            },
            confirmButton = { if (!p.selfDecided) TextButton(onClick = { vm.confirmPairing(true) }) { Text("Совпадает") } },
            dismissButton = { TextButton(onClick = { vm.confirmPairing(false) }) { Text(if (p.selfDecided) "Отмена" else "Не совпадает") } }
        )
        is PairUi.Done -> AlertDialog(
            onDismissRequest = { vm.dismissPair() }, title = { Text("Готово") },
            text = { Text("«${p.peerName}» связано. Теперь можно отправлять данные без кода.") },
            confirmButton = { TextButton(onClick = { vm.dismissPair() }) { Text("Ок") } }
        )
        is PairUi.Failed -> AlertDialog(
            onDismissRequest = { vm.dismissPair() }, title = { Text("Не получилось") },
            text = { Text(p.message) },
            confirmButton = { TextButton(onClick = { vm.dismissPair() }) { Text("Ок") } }
        )
    }

}

/** Ввод текста на другом устройстве: показывает, что оно ждёт, даёт поле и отправляет. */
@Composable
private fun InputDialogs(input: InputUi, vm: LocalSyncViewModel) {
    when (val i = input) {
        is InputUi.None -> {}
        is InputUi.Loading -> AlertDialog(
            onDismissRequest = { vm.dismissInput() }, title = { Text("«${i.deviceName}»") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text("Узнаю, что нужно ввести…") } },
            confirmButton = {}
        )
        is InputUi.Prompt -> {
            var text by remember(i.requestId) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { vm.dismissInput() },
                title = { Text("«${i.device.name}»") },
                text = {
                    Column {
                        Text(i.prompt)
                        Spacer(Modifier.height(ZenithDimens.paddingS))
                        OutlinedTextField(value = text, onValueChange = { if (it.length <= LocalProto.MAX_INPUT_CHARS) text = it }, enabled = !i.sending)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.submitInput(text) }, enabled = text.isNotBlank() && !i.sending) { Text(if (i.sending) "Отправляю…" else "Отправить") } },
                dismissButton = { TextButton(onClick = { vm.dismissInput() }) { Text("Отмена") } }
            )
        }
        is InputUi.Done -> AlertDialog(
            onDismissRequest = { vm.dismissInput() }, title = { Text("Готово") },
            text = { Text("Текст отправлен на «${i.deviceName}».") },
            confirmButton = { TextButton(onClick = { vm.dismissInput() }) { Text("Ок") } }
        )
        is InputUi.Failed -> AlertDialog(
            onDismissRequest = { vm.dismissInput() }, title = { Text("Не получилось") },
            text = { Text("«${i.deviceName}»: ${i.message}") },
            confirmButton = { TextButton(onClick = { vm.dismissInput() }) { Text("Ок") } }
        )
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SendResultCard(r: SendResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ZenithDimens.paddingM)) {
            Text(
                if (r.allConfirmed && r.report.errors.isEmpty()) "✓ Подтверждено устройством «${r.peerName}»" else "⚠ «${r.peerName}»: есть расхождения или ошибки",
                color = if (r.allConfirmed && r.report.errors.isEmpty()) ZenithSuccess else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            val any = r.checks.filter { it.sent > 0 || it.confirmed > 0 }
            if (any.isEmpty()) Text("Получатель ничего не принял.", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            for (c in any) {
                val rr = c.result
                Text(
                    "${categoryName(c.category)}: отправлено ${c.sent}, получатель подтвердил ${c.confirmed}" +
                        (if (c.ok) " ✓" else " ⚠") +
                        " (новых ${rr.added}, обновлено ${rr.updated}, пропущено ${rr.skipped}, ошибок ${rr.errors})",
                    color = if (c.ok) ZenithOnSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            NotesAndErrors(r.report)
        }
    }
}

@Composable
private fun ReceivedCard(r: ReceivedUi) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ZenithDimens.paddingM)) {
            if (r.error != null) {
                Text("Получение от «${r.deviceName}» не удалось: ${r.error}", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            Text("Получено от «${r.deviceName}»", color = ZenithOnSurface, fontWeight = FontWeight.Bold)
            val report = r.report ?: return@Column
            for (c in LocalProto.CATEGORIES) {
                val rr = report.categories[c] ?: continue
                if (rr.received == 0) continue
                Text(
                    "${categoryName(c)}: получено ${rr.received} (новых ${rr.added}, обновлено ${rr.updated}, пропущено ${rr.skipped}, ошибок ${rr.errors})",
                    color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            NotesAndErrors(report)
        }
    }
}

@Composable
private fun NotesAndErrors(report: ApplyReport) {
    for (n in report.notes) Text("• $n", color = ZenithOnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    for (e in report.errors) Text("✕ $e", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

private fun categoryName(c: String) = when (c) {
    LocalProto.CAT_FAVORITES -> "Избранное"
    LocalProto.CAT_HISTORY -> "История"
    LocalProto.CAT_SOURCES -> "Источники"
    LocalProto.CAT_PLUGINS -> "Плагины"
    LocalProto.CAT_SETTINGS -> "Настройки"
    LocalProto.CAT_HIDDEN -> "Скрытые каналы"
    else -> c
}

private fun typeName(t: String) = when (t) { "tv" -> "ТВ"; "phone" -> "Телефон"; "tablet" -> "Планшет"; else -> "Устройство" }

private fun fmt(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
