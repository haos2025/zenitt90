package com.platinum.ott.presentation.phone.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
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
import com.platinum.ott.presentation.screens.setup.SetupViewModel
import com.platinum.ott.presentation.screens.setup.SetupUiState
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneSetupScreen(onSetupComplete: () -> Unit, viewModel: SetupViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var m3uUrl by remember { mutableStateOf("") }
    var xtHost by remember { mutableStateOf("") }; var xtUser by remember { mutableStateOf("") }; var xtPass by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D1A)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("ZENITH", style = MaterialTheme.typography.displayLarge, color = Color(0xFF6C63FF))
        Text("Подключите источник", color = Color.White.copy(0.5f))
        Spacer(Modifier.height(32.dp))
        TabRow(selectedTabIndex = selectedTab) { Tab(selectedTab == 0, onClick = { selectedTab = 0 }) { Text("M3U") }; Tab(selectedTab == 1, onClick = { selectedTab = 1 }) { Text("Xtream") } }
        Spacer(Modifier.height(16.dp))
        when (selectedTab) {
            0 -> PhoneTextField(m3uUrl, { m3uUrl = it }, "http://example.com/playlist.m3u")
            1 -> { PhoneTextField(xtHost, { xtHost = it }, "Хост"); PhoneTextField(xtUser, { xtUser = it }, "Логин"); PhoneTextField(xtPass, { xtPass = it }, "Пароль", true) }
        }
        if (uiState is SetupUiState.Error) Text("⚠ ${(uiState as SetupUiState.Error).message}", color = Color(0xFFFF6B6B))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { if (uiState !is SetupUiState.Loading) when (selectedTab) { 0 -> viewModel.loginWithM3U(m3uUrl, onSetupComplete); 1 -> viewModel.loginWithXtream(xtHost, xtUser, xtPass, onSetupComplete) } }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text(if (uiState is SetupUiState.Loading) "Проверка..." else "Подключиться") }
    }
}

@Composable private fun PhoneTextField(value: String, onChange: (String) -> Unit, placeholder: String, isPassword: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onChange, placeholder = { Text(placeholder) }, singleLine = true, visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp))
}
