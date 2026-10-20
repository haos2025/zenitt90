package com.platinum.ott.presentation.phone.screens

import com.platinum.ott.ui.theme.ZenithOnSurfaceVariant
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.platinum.ott.core.platform.ZenithDimens
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.ui.theme.ZenithShapeMedium
import com.platinum.ott.ui.theme.ZenithShapeSmall

/**
 * План 07, этап 5: строка канала в режиме «Править» (раздел «Каналы» на телефоне).
 * Перенесена из бывшего PhoneChannelsManageScreen.kt без изменений логики —
 * переключатель подписки, проверка, переименование, слияние, удаление.
 */
@Composable
internal fun PhoneChannelEditCard(
    item: ChannelUiItem,
    onToggleSubscribed: (Boolean) -> Unit,
    onRename: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onCheckNow: () -> Unit
) {
    var showMenu by remember(item.id) { mutableStateOf(false) }
    Surface(
        shape = ZenithShapeMedium, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(ZenithDimens.paddingM).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp, 32.dp).clip(ZenithShapeSmall).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.logo != null) {
                    AsyncImage(model = item.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                } else {
                    Text("TV", style = MaterialTheme.typography.labelSmall)
                }
                // Та же точка health-check, что и на TV (ChannelsScreen.kt) —
                // цвет по агрегату ChannelUiItem.healthStatus.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.healthStatus) {
                                "alive" -> Color(0xFF4CAF50)
                                "dead" -> MaterialTheme.colorScheme.error
                                else -> ZenithOnSurfaceVariant
                            }
                        )
                )
            }
            Spacer(Modifier.width(ZenithDimens.paddingM))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.canonicalName, style = MaterialTheme.typography.titleSmall)
                    if (item.regionHint != null) {
                        Spacer(Modifier.width(ZenithDimens.paddingXS))
                        Text("(${item.regionHint})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                Text(
                    buildString {
                        append(if (item.streamCount == 1) "1 источник" else "${item.streamCount} источника(ов)")
                        item.category?.let { append(" • $it") }
                        if (item.isMergeCandidate) append(" • не сопоставлен")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.isMergeCandidate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Switch(checked = item.isSubscribed, onCheckedChange = onToggleSubscribed)

            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Действия") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Проверить сейчас") }, onClick = { showMenu = false; onCheckNow() })
                    DropdownMenuItem(text = { Text("Изменить название/регион") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Слить с другим каналом") }, onClick = { showMenu = false; onMerge() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                }
            }
        }
    }
}
