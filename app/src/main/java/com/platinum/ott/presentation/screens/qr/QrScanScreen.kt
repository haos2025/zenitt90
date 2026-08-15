package com.platinum.ott.presentation.screens.qr

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.platinum.ott.core.platform.ZenithDimens

// Раньше это была ни к чему не привязанная заглушка "QR-код авторизации" —
// ни один экран сюда не вёл (nav-route "qr_scan" на TV рисовал пустой Box).
// PROMPT_PHONE_COMPANION.md прямо просил переиспользовать именно этот файл
// под реальную задачу (ROADMAP.md п.6). Переписан в общий composable
// "показать QR с текстом": единственный вызывающий сейчас — PlayerScreen.kt
// (внешние субтитры), но сигнатура не завязана на субтитры специально —
// второе применение того же канала (поиск текстом, см. промт) сможет
// переиспользовать этот же composable без изменений.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QrScanScreen(content: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Телефон-компаньон", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(ZenithDimens.paddingS))
            if (content != null) {
                Text("Отсканируйте QR-код телефоном — оба устройства должны быть в одной сети Wi-Fi", color = Color.Gray)
                Spacer(Modifier.height(ZenithDimens.paddingM))
                val bitmap = remember(content) { generateQrBitmap(content, 320) }
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(), contentDescription = "QR-код",
                        modifier = Modifier.size(320.dp).background(Color.White).padding(ZenithDimens.paddingS)
                    )
                } else {
                    Text("Не удалось сгенерировать QR-код", color = MaterialTheme.colorScheme.error)
                }
            } else {
                // LocalNetworkUtils.getLocalIpAddress() не смог определить
                // адрес — без него показывать нечего, TV и телефон должны
                // быть в одной локальной сети.
                Text("Не удалось определить локальный IP-адрес.\nПроверьте, что TV подключён к сети.", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(ZenithDimens.paddingL))
            OutlinedButton(onClick = onDismiss) { Text("Отмена") }
        }
    }
}

private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? = try {
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
