package com.platinum.ott.external

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.platinum.ott.core.InterfacePreferences
import com.platinum.ott.core.platform.isTV
import com.platinum.ott.presentation.phone.screens.PhonePlayerScreen
import com.platinum.ott.presentation.screens.player.DirectPlayRequest
import com.platinum.ott.presentation.screens.player.EXTERNAL_PLAYER_ID_PREFIX
import com.platinum.ott.presentation.screens.player.PlayerScreen
import com.platinum.ott.presentation.screens.player.PlayerViewModel
import com.platinum.ott.ui.theme.LocalAppearance
import com.platinum.ott.ui.theme.ZenithBackground
import com.platinum.ott.ui.theme.ZenithTheme
import com.platinum.ott.ui.theme.ZenithTvTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Режим «внешний плеер» (план 05): другие приложения открывают ссылку через «Открыть с помощью».
 * Играет ссылку полноценным плеером Zenith (тот же оверлей и меню субтитров), без каталога,
 * без истории и избранного. «Назад» закрывает экран — возвращает в вызвавшее приложение.
 */
@AndroidEntryPoint
class ExternalPlayerActivity : ComponentActivity() {
    @Inject lateinit var interfacePreferences: InterfacePreferences

    private var request by mutableStateOf<DirectPlayRequest?>(null)
    private var nonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = isTV(this)
        if (isTv) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (!accept()) return
        setContent {
            val appearance by interfacePreferences.appearance.collectAsState()
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ZenithTheme(isTv = isTv, accent = appearance.accent) {
                    if (isTv) ZenithTvTheme(accent = appearance.accent) { PlayerHost(isTv) } else PlayerHost(isTv)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept()
    }

    /** false — интент не подходит, активити закрыта. */
    private fun accept(): Boolean {
        val parsed = ExternalIntentParser.parse(intent)
        if (parsed == null) {
            Toast.makeText(this, "Zenith: не удалось открыть ссылку", Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        request = parsed
        nonce += 1
        return true
    }

    @Composable
    private fun PlayerHost(isTv: Boolean) {
        val req = request ?: return
        val viewModel: PlayerViewModel = hiltViewModel()
        val id = "$EXTERNAL_PLAYER_ID_PREFIX$nonce"
        // remember выполняется при композиции — раньше LaunchedEffect внутри экрана плеера,
        // который вызывает loadMovie(id) и уже найдёт подготовленный запрос.
        remember(nonce) { viewModel.prepareDirect(req); nonce }
        val modifier = Modifier.fillMaxSize().background(ZenithBackground)
        androidx.compose.foundation.layout.Box(modifier) {
            if (isTv) {
                PlayerScreen(movieId = id, onBackPressed = { finish() }, viewModel = viewModel)
            } else {
                PhonePlayerScreen(movieId = id, navController = null, onExit = { finish() }, viewModel = viewModel)
            }
        }
    }
}
