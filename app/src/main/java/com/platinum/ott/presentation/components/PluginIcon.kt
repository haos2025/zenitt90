package com.platinum.ott.presentation.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.platinum.ott.core.plugin.PluginUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Иконка плагина (общая для TV и телефона, без tv/material3-зависимостей).
 * Пока иконки нет, она не прошла проверку или не загрузилась — круглый
 * плейсхолдер с первой буквой названия.
 *
 * Безопасность: iconUrl приходит из манифеста стороннего плагина, поэтому
 * (1) URL проверяется тем же PluginUrlValidator, что и сетевые вызовы
 * плагинов (в IO-потоке — он резолвит DNS), (2) картинка грузится отдельным
 * ImageLoader с ОТКЛЮЧЁННЫМИ редиректами — иначе иконка могла бы увести
 * запрос на 127.0.0.1/локальную сеть в обход проверки (тот же SSRF-класс,
 * что закрыт в PluginApi.kt).
 */
@Composable
fun PluginIcon(iconUrl: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember { pluginIconLoader(context) }
    val safeUrl by produceState<String?>(null, iconUrl) {
        value = if (iconUrl.isNullOrBlank()) null
        else withContext(Dispatchers.IO) { if (PluginUrlValidator.isValid(iconUrl)) iconUrl else null }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 5)).background(Color(0xFF1E2A3F)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = (size.value * 0.45f).sp)
        )
        safeUrl?.let {
            AsyncImage(model = it, imageLoader = loader, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

@Volatile private var iconLoader: ImageLoader? = null

private fun pluginIconLoader(context: Context): ImageLoader =
    iconLoader ?: synchronized(PluginIconLock) {
        iconLoader ?: ImageLoader.Builder(context.applicationContext)
            .okHttpClient(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build())
            .build().also { iconLoader = it }
    }

private object PluginIconLock
