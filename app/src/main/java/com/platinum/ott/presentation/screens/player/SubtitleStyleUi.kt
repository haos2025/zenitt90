package com.platinum.ott.presentation.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.platinum.ott.core.SUBTITLE_SETTINGS
import com.platinum.ott.core.SubtitleStyle
import com.platinum.ott.core.SubtitleStyleStore

/** Текущее оформление субтитров; обновляется сразу после изменения в меню. */
@Composable
fun rememberSubtitleStyle(): State<SubtitleStyle> {
    val context = LocalContext.current
    remember { SubtitleStyleStore.ensureLoaded(context); true }
    return SubtitleStyleStore.style.collectAsState()
}

private fun SubtitleStyle.textComposeColor() = Color(0xFF000000.toInt() or textColor).copy(alpha = textOpacity / 100f)
private fun SubtitleStyle.bgComposeColor() = Color(0xFF000000.toInt() or bgColor).copy(alpha = bgOpacity / 100f)

/** Текст субтитров с учётом настроек (размер, цвета, фон, края). baseSp — размер при 100%. */
@Composable
fun StyledSubtitleText(text: String, style: SubtitleStyle, baseSp: Float, modifier: Modifier = Modifier) {
    val textStyle = TextStyle(
        color = style.textComposeColor(),
        fontSize = (baseSp * style.sizePercent / 100f).sp,
        textAlign = TextAlign.Center,
        shadow = if (style.edge == SubtitleStyle.EDGE_SHADOW) Shadow(Color.Black, Offset(3f, 3f), 6f) else null
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(style.bgComposeColor())
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (style.edge == SubtitleStyle.EDGE_OUTLINE) {
            // Контур: сначала чёрная обводка, поверх обычный текст.
            Text(text, style = textStyle.copy(color = Color.Black, drawStyle = Stroke(width = 6f)))
        }
        Text(text, style = textStyle)
    }
}

/** Слой субтитров у нижнего края; высота отступа — доля высоты экрана из настроек. */
@Composable
fun BoxScope.AutoSubtitleOverlay(text: String, style: SubtitleStyle, baseSp: Float) {
    // Прямой потомок внешнего Box — та же схема, что была у прежней надписи (без отдельного слоя).
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    StyledSubtitleText(
        text = text, style = style, baseSp = baseSp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 32.dp)
            .padding(bottom = (screenHeightDp * style.bottomPercent / 100f).dp)
    )
}

/**
 * Автосубтитры целиком в одном слое: свой опрос позиции плеера (4 раза в секунду, только пока есть реплики)
 * и своё чтение оформления. Раньше текст зависел от общей позиции экрана (обновлялась раз в 0,5 с, а смена
 * любой настройки перерисовывала весь плеер): при изменении внешнего вида реплики появлялись с задержкой
 * или пропускались. Теперь перерисовывается только этот слой.
 */
@Composable
fun BoxScope.AutoSubtitleLayer(cues: List<com.platinum.ott.core.subtitles.SubtitleCue>, player: androidx.media3.common.Player, baseSp: Float) {
    val style by rememberSubtitleStyle()
    var positionMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(cues.isNotEmpty()) {
        while (cues.isNotEmpty()) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            kotlinx.coroutines.delay(250)
        }
    }
    // Сдвиг по времени из настроек: положительный — субтитры позже.
    val text = remember(cues, positionMs, style.offsetMs) {
        val shifted = positionMs - style.offsetMs
        cues.firstOrNull { shifted in it.startMs..it.endMs }?.text
    }
    if (text != null) AutoSubtitleOverlay(text, style, baseSp)
}

/** Применяет то же оформление к встроенным и внешним дорожкам, которые рисует сам ExoPlayer. */
@androidx.annotation.OptIn(UnstableApi::class)
fun applySubtitleStyle(view: PlayerView, style: SubtitleStyle) {
    val sv = view.subtitleView ?: return
    val fg = ((style.textOpacity * 255 / 100) shl 24) or style.textColor
    val bg = ((style.bgOpacity * 255 / 100) shl 24) or style.bgColor
    val edgeType = when (style.edge) {
        SubtitleStyle.EDGE_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        SubtitleStyle.EDGE_OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        else -> CaptionStyleCompat.EDGE_TYPE_NONE
    }
    sv.setApplyEmbeddedStyles(false)
    sv.setApplyEmbeddedFontSizes(false)
    sv.setStyle(CaptionStyleCompat(fg, bg, android.graphics.Color.TRANSPARENT, edgeType, android.graphics.Color.BLACK, null))
    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.sizePercent / 100f)
    sv.setBottomPaddingFraction(style.bottomPercent / 100f)
}

/** Диалог настроек для телефона: образец сверху, ниже строки — нажатие переключает значение по кругу. */
@Composable
fun SubtitleStyleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val style by rememberSubtitleStyle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Оформление субтитров") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF303030)).padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    StyledSubtitleText("Так будут выглядеть субтитры", style, 16f)
                }
                SUBTITLE_SETTINGS.forEach { setting ->
                    TextButton(onClick = { SubtitleStyleStore.update(context, setting.next) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(setting.title, color = LocalContentColor.current)
                            Text(setting.valueLabel(style), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = { SubtitleStyleStore.reset(context) }) { Text("Сбросить") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}
