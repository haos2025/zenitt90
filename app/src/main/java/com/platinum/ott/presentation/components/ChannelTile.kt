package com.platinum.ott.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.ui.theme.ZenithDurationShort
import com.platinum.ott.ui.theme.ZenithEasingStandard
import com.platinum.ott.ui.theme.ZenithError
import com.platinum.ott.ui.theme.ZenithFocusContainerActive
import com.platinum.ott.ui.theme.ZenithShapeSmall
import com.platinum.ott.ui.theme.ZenithSuccess
import com.platinum.ott.ui.theme.ZenithSurface

/**
 * РЕДИЗАЙН (разбор IPTV с нуля, экран просмотра каналов): логотип-плитка
 * для сетки — раньше ChannelUiItem.logo/category вообще не использовались
 * визуально, канал в списке был просто строкой текста с крошечным
 * превью 56×36. Общий компонент для TV и телефона — тот же принцип, что и
 * MovieCard.kt/HomeInfoStrip.kt (androidx.tv.material3.Surface сюда
 * нельзя, компонент используется и в телефонной версии).
 */
@Composable
fun ChannelTile(item: ChannelUiItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(ZenithDurationShort, easing = ZenithEasingStandard),
        label = "channelTileScale"
    )
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.35f).scale(scale).onFocusChanged { isFocused = it.isFocused },
        shape = ZenithShapeSmall,
        colors = CardDefaults.cardColors(containerColor = ZenithSurface)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(ZenithDimens.paddingS), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (item.logo != null) {
                        AsyncImage(
                            model = item.logo, contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(0.75f)
                        )
                    } else {
                        // Нет логотипа — инициалы вместо пустого места, тот
                        // же принцип, что и заглушка "TV" в старом ChannelCard.
                        Text(
                            item.canonicalName.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
                Text(
                    item.canonicalName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Точка health-check — тот же приём, что был в ChannelCard
            // (ChannelsManageScreen.kt), только в углу плитки, не превью.
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(CircleShape)
                    .background(if (item.healthStatus == "alive") ZenithSuccess else if (item.healthStatus == "dead") ZenithError else Color.Gray)
            )
            if (isFocused) Box(Modifier.fillMaxSize().background(ZenithFocusContainerActive))
        }
    }
}
