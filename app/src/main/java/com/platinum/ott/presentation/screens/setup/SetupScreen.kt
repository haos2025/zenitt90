package com.platinum.ott.presentation.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.platinum.ott.core.companion.CompanionHttpServer
import com.platinum.ott.core.companion.LocalNetworkUtils
import com.platinum.ott.presentation.screens.qr.QrScanScreen
import com.platinum.ott.ui.theme.*
import com.platinum.ott.core.platform.ZenithDimens

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
    // Раньше этот экран был тупиком: пока пользователь не введёт рабочий
    // M3U/Xtream адрес, попасть больше никуда было нельзя — ни в настройки
    // (проверить плагины, синхронизацию, сменить тему), ни в историю/избранное,
    // которые физически хранятся в Room и не зависят от того, залогинен ли
    // пользователь (ServiceLocator.initAuth() строит их безусловно при
    // старте приложения, см. ServiceLocator.kt). Блокировка была чисто
    // навигационной, не технической. Теперь эти экраны достижимы и отсюда.
    onSettingsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var m3uUrl by remember { mutableStateOf("") }
    var xtHost by remember { mutableStateOf("") }; var xtUser by remember { mutableStateOf("") }; var xtPass by remember { mutableStateOf("") }

    // Телефон-компаньон (PROMPT_QR_PANELS.md), четвёртое применение канала —
    // "/m3u_url". Только вкладка M3U (одно поле, один скан) — Xtream
    // сознательно не трогается (три поля, см. промт). Текст со скана
    // подставляется в m3uUrl, форма не отправляется автоматически —
    // пользователь по-прежнему сам нажимает "Подключиться".
    var showCompanionQr by remember { mutableStateOf(false) }
    var companionAddress by remember { mutableStateOf<String?>(null) }
    DisposableEffect(showCompanionQr) {
        var server: CompanionHttpServer? = null
        if (showCompanionQr) {
            server = CompanionHttpServer(endpointPath = "/m3u_url") { text ->
                // Обработчик NanoHTTPD вызывается не в главном потоке — то
                // же самое, что уже сделано в SearchScreen.kt/PlayerScreen.kt.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    m3uUrl = text
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
    androidx.activity.compose.BackHandler(enabled = showCompanionQr) { showCompanionQr = false }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(ZenithDimens.paddingL), horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
            OutlinedButton(onClick = onFavoritesClick) { Text("Избранное") }
            OutlinedButton(onClick = onHistoryClick) { Text("История") }
            OutlinedButton(onClick = onSettingsClick) { Text("Настройки") }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZenithDimens.paddingL), modifier = Modifier.align(Alignment.Center).width(520.dp)) {
            Text("ZENITH", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Text("Подключите источник контента", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingSM)) {
                TabButton("M3U Плейлист", selectedTab == 0) { selectedTab = 0 }; TabButton("Xtream Codes", selectedTab == 1) { selectedTab = 1 }
            }
            when (selectedTab) {
                0 -> {
                    SetupTextField(m3uUrl, { m3uUrl = it }, "http://example.com/playlist.m3u")
                    OutlinedButton(onClick = { showCompanionQr = true }, modifier = Modifier.fillMaxWidth()) { Text("По QR с телефона") }
                }
                1 -> { SetupTextField(xtHost, { xtHost = it }, "http://example.com:8080"); SetupTextField(xtUser, { xtUser = it }, "Логин"); SetupTextField(xtPass, { xtPass = it }, "Пароль", true) }
            }
            if (uiState is SetupUiState.Error) Text("⚠ ${(uiState as SetupUiState.Error).message}", color = MaterialTheme.colorScheme.error)
            Button(onClick = { if (uiState !is SetupUiState.Loading) when (selectedTab) { 0 -> viewModel.loginWithM3U(m3uUrl, onSetupComplete); 1 -> viewModel.loginWithXtream(xtHost, xtUser, xtPass, onSetupComplete) } }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = uiState !is SetupUiState.Loading) {
                Text(if (uiState is SetupUiState.Loading) "Проверка..." else "Подключиться")
            }
        }
    }

    if (showCompanionQr) {
        QrScanScreen(
            content = companionAddress,
            onDismiss = { showCompanionQr = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainer, focusedContainerColor = if (selected) MaterialTheme.colorScheme.primary else ZenithFocusContainerActive)) { Text(label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)) }
}

@Composable private fun SetupTextField(value: String, onChange: (String) -> Unit, placeholder: String, isPassword: Boolean = false) {
    BasicTextField(value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(Color.White, 16.sp), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.06f), RoundedCornerShape(8.dp)).padding(16.dp, 14.dp), decorationBox = { if (value.isEmpty()) Text(placeholder, style = TextStyle(Color.White.copy(0.3f), 16.sp)); it() })
}
