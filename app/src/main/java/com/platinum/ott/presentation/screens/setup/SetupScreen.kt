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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(onSetupComplete: () -> Unit, modifier: Modifier = Modifier, viewModel: SetupViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var m3uUrl by remember { mutableStateOf("") }
    var xtHost by remember { mutableStateOf("") }; var xtUser by remember { mutableStateOf("") }; var xtPass by remember { mutableStateOf("") }
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0D0D1A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.width(520.dp)) {
            Text("ZENITH", style = MaterialTheme.typography.displayMedium, color = Color(0xFF6C63FF))
            Text("Подключите источник контента", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TabButton("M3U Плейлист", selectedTab == 0) { selectedTab = 0 }; TabButton("Xtream Codes", selectedTab == 1) { selectedTab = 1 }
            }
            when (selectedTab) {
                0 -> SetupTextField(m3uUrl, { m3uUrl = it }, "http://example.com/playlist.m3u")
                1 -> { SetupTextField(xtHost, { xtHost = it }, "http://example.com:8080"); SetupTextField(xtUser, { xtUser = it }, "Логин"); SetupTextField(xtPass, { xtPass = it }, "Пароль", true) }
            }
            if (uiState is SetupUiState.Error) Text("⚠ ${(uiState as SetupUiState.Error).message}", color = Color(0xFFFF6B6B))
            Button(onClick = { if (uiState !is SetupUiState.Loading) when (selectedTab) { 0 -> viewModel.loginWithM3U(m3uUrl, onSetupComplete); 1 -> viewModel.loginWithXtream(xtHost, xtUser, xtPass, onSetupComplete) } }, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = uiState !is SetupUiState.Loading) {
                Text(if (uiState is SetupUiState.Loading) "Проверка..." else "Подключиться")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class) @Composable private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)), colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.08f), focusedContainerColor = if (selected) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.18f))) { Text(label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)) }
}

@Composable private fun SetupTextField(value: String, onChange: (String) -> Unit, placeholder: String, isPassword: Boolean = false) {
    BasicTextField(value = value, onValueChange = onChange, singleLine = true, textStyle = TextStyle(Color.White, 16.sp), cursorBrush = SolidColor(Color(0xFF6C63FF)), visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth().background(Color.White.copy(0.06f), RoundedCornerShape(8.dp)).padding(16.dp, 14.dp), decorationBox = { if (value.isEmpty()) Text(placeholder, style = TextStyle(Color.White.copy(0.3f), 16.sp)); it() })
}
