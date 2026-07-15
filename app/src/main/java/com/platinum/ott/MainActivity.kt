package com.platinum.ott

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
        val intentUri = intent?.data
        val deepLinkMovieId = if (intentUri?.scheme == "zenith" && intentUri.host == "player")
            intentUri.getQueryParameter("id") else null
        val startDestination = when {
            deepLinkMovieId != null -> "player/$deepLinkMovieId"
            ServiceLocator.checkAuthUseCase.execute() -> "home"
            else -> "setup"
        }
        setContent {
            val isTv = remember { isTV(context) }
            ZenithTheme {
                ZenithNavHost(
                    startDestination = startDestination,
                    isTV = isTv,
                    modifier = Modifier.fillMaxSize().background(ZenithBackground)
                )
            }
        }
    }
}
