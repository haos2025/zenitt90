package com.platinum.ott

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.core.platform.isTV
import com.platinum.ott.navigation.ZenithNavHost
import com.platinum.ott.ui.theme.ZenithBackground
import com.platinum.ott.ui.theme.ZenithTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val isTv = isTV(context)

        // Раньше ориентация была зашита в AndroidManifest.xml как
        // android:screenOrientation="landscape" — статически, для ВСЕХ
        // устройств разом. Манифест не может различить ТВ и телефон на
        // этапе сборки — поэтому приложение всегда открывалось в landscape
        // на смартфоне тоже, хотя ZenithNavHost уже правильно выбирает
        // Phone-экраны (PhoneHomeScreen и т.д., портретный Material3) через
        // тот же isTV(). Единственное место, которое реально знает тип
        // устройства — рантайм, поэтому ориентация теперь выставляется здесь.
        requestedOrientation = if (isTv) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val intentUri = intent?.data
        val deepLinkMovieId = if (intentUri?.scheme == "zenith" && intentUri.host == "player")
            intentUri.getQueryParameter("id") else null
        val startDestination = when {
            deepLinkMovieId != null -> "player/$deepLinkMovieId"
            ServiceLocator.checkAuthUseCase.execute() -> "home"
            else -> "setup"
        }
        setContent {
            // Раньше ZenithTheme брал только системную тему, пользовательский
            // переключатель в настройках ни на что не влиял. darkThemeFlow
            // готов уже в ServiceLocator.init() — до логина тоже.
            val darkTheme by ServiceLocator.darkThemeFlow.collectAsState()
            ZenithTheme(darkTheme = darkTheme) {
                ZenithNavHost(
                    startDestination = startDestination,
                    isTV = isTv,
                    modifier = Modifier.fillMaxSize().background(ZenithBackground)
                )
            }
        }
    }
}
