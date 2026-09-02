package com.platinum.ott.presentation.screens.sources

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import com.platinum.ott.core.companion.CompanionHttpServer
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.qr.QrScanScreen
import com.platinum.ott.ui.theme.*

/**
 * Заменяет прежний SetupScreen.kt — тот отвечал за первичный вход
 * (единственный источник), этот только добавляет ОДИН новый источник в уже
 * существующий список (SourcesScreen.kt), поэтому здесь нет ни кнопок-обходов
 * (их и не было в SetupScreen смысла переносить — этот экран открывается уже
 * ИЗ навигации, а не вместо неё), ни отдельного "успешного" редиректа на
 * home — по завершении просто возвращаемся на список источников (onDone).
 *
 * QR-подключение телефона для ввода M3U-ссылки — перенесённый без изменений
 * код из старого SetupScreen.kt (тот же endpointPath "/m3u_url"). Доступен
 * только на TV — на телефоне у пользователя уже есть клавиатура, сканировать
 * что-либо там незачем (см. также решение PROMPT_QR_PANELS.md: Xtream и
 * телефонная сторона такую кнопку никогда не получали).
 */
private enum class SourceType { M3U, XTREAM }
private enum class M3uMethod { URL, FILE, QR }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddSourceScreen(
    onDone: () -> Unit,
    viewModel: AddSourceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sourceType by remember { mutableStateOf(SourceType.M3U) }
    var m3uMethod by remember { mutableStateOf(M3uMethod.URL) }
    var label by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var xtHost by remember { mutableStateOf("") }
    var xtUser by remember { mutableStateOf("") }
    var xtPass by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()?.let { text ->
            fileContent = text
            fileName = uri.lastPathSegment
        }
    }

    // Тот же паттерн, что в старом SetupScreen.kt — локальный HTTP-сервер
    // живёт ровно пока открыт QR-оверлей, останавливается в onDispose.
    var showCompanionQr by remember { mutableStateOf(false) }
    var companionAddress by remember { mutableStateOf<String?>(null) }
    DisposableEffect(showCompanionQr) {
        var server: CompanionHttpServer? = null
        if (showCompanionQr) {
            server = CompanionHttpServer(endpointPath = "/m3u_url") { text ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    m3uUrl = text
                    m3uMethod = M3uMethod.URL
                    showCompanionQr = false
                }
            }
            val port = server.startServer()
            val ip = LocalNetworkUtils.getLocalIpAddress()
            companionAddress = if (ip != null) "http://$ip:$port#m3u_url" else null
        } else {
            companionAddress = null
        }
        onDispose { server?.stop() }
    }
    BackHandler(enabled = showCompanionQr) { showCompanionQr = false }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingM),
            modifier = Modifier.align(Alignment.Center).width(520.dp)
        ) {
            Text("Добавить источник", style = MaterialTheme.typography.displaySmall, color = Color.White)

            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                TabButton("M3U Плейлист", sourceType == SourceType.M3U) { sourceType = SourceType.M3U }
                TabButton("Xtream Codes", sourceType == SourceType.XTREAM) { sourceType = SourceType.XTREAM }
            }

            SourceTextField(label, { label = it }, "Название источника (необязательно)")

            when (sourceType) {
                SourceType.M3U -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                        TabButton("Ссылка", m3uMethod == M3uMethod.URL) { m3uMethod = M3uMethod.URL }
                        TabButton("Файл", m3uMethod == M3uMethod.FILE) { m3uMethod = M3uMethod.FILE }
                        TabButton("QR", m3uMethod == M3uMethod.QR) { m3uMethod = M3uMethod.QR }
                    }
                    when (m3uMethod) {
                        M3uMethod.URL -> SourceTextField(m3uUrl, { m3uUrl = it }, "http://example.com/playlist.m3u")
                        M3uMethod.FILE -> {
                            OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                                Text(fileName ?: "Выбрать файл .m3u")
                            }
                        }
                        M3uMethod.QR -> {
                            OutlinedButton(onClick = { showCompanionQr = true }, modifier = Modifier.fillMaxWidth()) { Text("Показать QR для телефона") }
                            if (m3uUrl.isNotBlank()) Text("Получено: $m3uUrl", color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
                SourceType.XTREAM -> {
                    SourceTextField(xtHost, { xtHost = it }, "http://example.com:8080")
                    SourceTextField(xtUser, { xtUser = it }, "Логин")
                    SourceTextField(xtPass, { xtPass = it }, "Пароль", isPassword = true)
                }
            }

            if (uiState is AddSourceUiState.Error) {
                Text("⚠ ${(uiState as AddSourceUiState.Error).message}", color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                OutlinedButton(onClick = onDone) { Text("Отмена") }
                Button(
                    onClick = {
                        when (sourceType) {
                            SourceType.M3U -> when (m3uMethod) {
                                M3uMethod.URL, M3uMethod.QR -> viewModel.addM3uUrl(label, m3uUrl, onDone)
                                M3uMethod.FILE -> fileContent?.let { viewModel.addM3uFile(label, it, onDone) }
                            }
                            SourceType.XTREAM -> viewModel.addXtream(label, xtHost, xtUser, xtPass, onDone)
                        }
                    },
                    enabled = uiState !is AddSourceUiState.Loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState is AddSourceUiState.Loading) "Проверка..." else "Добавить")
                }
            }
        }
    }

    if (showCompanionQr) {
        QrScanScreen(content = companionAddress, onDismiss = { showCompanionQr = false }, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainer,
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive
        )
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = if (selected) Color.White else Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun SourceTextField(value: String, onChange: (String) -> Unit, placeholder: String, isPassword: Boolean = false) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(Color.White, 16.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp)).padding(16.dp, 14.dp),
        decorationBox = { if (value.isEmpty()) Text(placeholder, style = TextStyle(Color.White.copy(alpha = 0.3f), 16.sp)); it() }
    )
}
