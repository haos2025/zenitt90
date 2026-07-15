package com.platinum.ott.presentation.screens.qr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QrScanScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("QR-код авторизации", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text("Отсканируйте QR-код с помощью телефона", color = Color.Gray)
        // QR code display with ZXing would go here
    }
}
