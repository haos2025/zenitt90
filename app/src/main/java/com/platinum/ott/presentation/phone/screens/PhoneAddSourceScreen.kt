package com.platinum.ott.presentation.phone.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.presentation.screens.sources.AddSourceUiState
import com.platinum.ott.presentation.screens.sources.AddSourceViewModel

/**
 * Телефон-версия AddSourceScreen.kt (TV) — тот же AddSourceViewModel.
 * Без QR-подключения (решение сессии, как и в PROMPT_QR_PANELS.md — на
 * телефоне уже есть клавиатура, сканировать самого себя незачем), локальный
 * файл — тот же ACTION_OPEN_DOCUMENT, только на телефоне этот способ и
 * ожидается чаще всего (скачанный .m3u в Загрузках).
 */
private enum class SourceTypeTab { M3U, XTREAM }
private enum class M3uMethodTab { URL, FILE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAddSourceScreen(
    navController: NavHostController,
    onDone: () -> Unit,
    viewModel: AddSourceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sourceType by remember { mutableStateOf(SourceTypeTab.M3U) }
    var m3uMethod by remember { mutableStateOf(M3uMethodTab.URL) }
    var label by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var xtHost by remember { mutableStateOf("") }
    var xtUser by remember { mutableStateOf("") }
    var xtPass by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }
            .getOrNull()?.let { text -> fileContent = text; fileName = uri.lastPathSegment }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить источник") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).padding(ZenithDimens.paddingM)
        ) {
            TabRow(selectedTabIndex = sourceType.ordinal) {
                Tab(sourceType == SourceTypeTab.M3U, onClick = { sourceType = SourceTypeTab.M3U }) { Text("M3U", modifier = Modifier.padding(ZenithDimens.paddingS)) }
                Tab(sourceType == SourceTypeTab.XTREAM, onClick = { sourceType = SourceTypeTab.XTREAM }) { Text("Xtream", modifier = Modifier.padding(ZenithDimens.paddingS)) }
            }
            Spacer(Modifier.height(ZenithDimens.paddingM))

            OutlinedTextField(
                value = label, onValueChange = { label = it },
                placeholder = { Text("Название источника (необязательно)") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS), shape = RoundedCornerShape(12.dp)
            )

            when (sourceType) {
                SourceTypeTab.M3U -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(ZenithDimens.paddingS)) {
                        FilterChip(selected = m3uMethod == M3uMethodTab.URL, onClick = { m3uMethod = M3uMethodTab.URL }, label = { Text("Ссылка") })
                        FilterChip(selected = m3uMethod == M3uMethodTab.FILE, onClick = { m3uMethod = M3uMethodTab.FILE }, label = { Text("Файл") })
                    }
                    Spacer(Modifier.height(ZenithDimens.paddingS))
                    when (m3uMethod) {
                        M3uMethodTab.URL -> OutlinedTextField(
                            value = m3uUrl, onValueChange = { m3uUrl = it },
                            placeholder = { Text("http://example.com/playlist.m3u") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        M3uMethodTab.FILE -> OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(fileName ?: "Выбрать файл .m3u") }
                    }
                }
                SourceTypeTab.XTREAM -> {
                    OutlinedTextField(value = xtHost, onValueChange = { xtHost = it }, placeholder = { Text("Хост, например http://example.com:8080") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = xtUser, onValueChange = { xtUser = it }, placeholder = { Text("Логин") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(value = xtPass, onValueChange = { xtPass = it }, placeholder = { Text("Пароль") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(vertical = ZenithDimens.paddingXS), shape = RoundedCornerShape(12.dp))
                }
            }

            if (uiState is AddSourceUiState.Error) {
                Text("⚠ ${(uiState as AddSourceUiState.Error).message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = ZenithDimens.paddingS))
            }

            Spacer(Modifier.height(ZenithDimens.paddingM))
            Button(
                onClick = {
                    when (sourceType) {
                        SourceTypeTab.M3U -> when (m3uMethod) {
                            M3uMethodTab.URL -> viewModel.addM3uUrl(label, m3uUrl, onDone)
                            M3uMethodTab.FILE -> fileContent?.let { viewModel.addM3uFile(label, it, onDone) }
                        }
                        SourceTypeTab.XTREAM -> viewModel.addXtream(label, xtHost, xtUser, xtPass, onDone)
                    }
                },
                enabled = uiState !is AddSourceUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (uiState is AddSourceUiState.Loading) "Проверка..." else "Добавить") }
        }
    }
}
